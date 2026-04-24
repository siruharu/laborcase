# Runbook: Law Sync 잡 실패 대응

`law-full-sync` 또는 `law-delta-sync` (Cloud Run Jobs) 가 실패했을 때 따라 쓰는 절차.

## 트리거

다음 신호 중 하나라도 발생하면 이 runbook 을 시작한다.

- Sentry `full-sync` 또는 `delta-sync` 태그의 ERROR 이벤트.
- Cloud Scheduler `law-delta-sync-daily` 의 실행 실패 (콘솔 또는 로그).
- API 응답의 `freshness.stale == true` (기본 48h 임계).
- Cloud SQL `sync_log` 테이블에 `status='FAILED'` 최근 행이 있음:
  ```sql
  SELECT id, job_name, started_at, finished_at, error_message
    FROM sync_log
   WHERE status = 'FAILED'
   ORDER BY started_at DESC
   LIMIT 10;
  ```

## 빠른 실행 체크리스트 (5분 분류)

### 1. 로그 확인

```bash
# 최근 law-full-sync 실행 N개
gcloud run jobs executions list --job=law-full-sync \
  --region=asia-northeast3 --project=laborcase-prod --limit=5

# 특정 실행의 로그
gcloud logging read \
  'resource.type="cloud_run_job" AND
   labels."run.googleapis.com/execution_name"="law-full-sync-xxxxx"' \
  --project=laborcase-prod --limit=50 \
  --format='value(timestamp,severity,textPayload)'
```

### 2. 에러 유형 매칭

아래 표에서 에러 메시지를 찾아 해당 섹션으로 이동.

| 에러 메시지 (일부) | 원인 | 섹션 |
|---|---|---|
| `DRF returned permission/auth failure HTML` | OC 미승인 / IP 미등록 / 권한 category 없음 | [§A](#a-drf-인증-실패) |
| `law-sync-sa@... does not have storage.objects.get` | GCS IAM 바인딩 누락 | [§B](#b-gcs-권한-드리프트) |
| `object already exists at gs://laborcase-raw/...` | Orphan GCS 객체 (DB 롤백 후) | [§C](#c-gcs--db-비일관성) |
| `duplicate key value violates unique constraint "article_locator_uq"` | 파서가 중복 locator 방출 (스키마 미반영) | [§D](#d-article-중복-locator) |
| `Connection refused` / `connect timed out` (Cloud SQL) | VPC 부착 또는 private IP 문제 | [§E](#e-cloud-sql-연결-실패) |
| Container exited 1, other | 일반 | [§F](#f-일반-재시도) |

---

## §A. DRF 인증 실패

### 증상
로그에 다음이 찍힘:
```
DRF returned permission/auth failure HTML for
https://www.law.go.kr/DRF/lawSearch.do?OC=...&target=...
(OC not registered or endpoint not approved).
```

### 원인 후보
1. **Cloud NAT 고정 IP 가 법제처에 등록 안 됨 또는 삭제됨**.
2. **OC 값이 Secret Manager 에서 사라짐 or 빈 문자열**.
3. **접근한 target (예: lsHistory) 이 활용 신청에 없는 카테고리**.

### 조치
```bash
# 1. NAT IP 확인
cd infra/terraform && terraform output nat_egress_ip
# 결과를 open.law.go.kr → OPEN API 신청 수정 → 도메인/IP 주소 칸과 대조.

# 2. Secret 값 확인
gcloud secrets versions access latest --secret=law-oc --project=laborcase-prod
# 결과가 예상 OC 와 일치하는지 확인.

# 3. 권한 없는 target 인 경우
grep -r "target=" api/src/main/kotlin/kr/laborcase/law/client/
# 가장 가능성 높은 원인은 target=lsHistory — Task 7 이후 사용 안 함.
```

만약 NAT IP 가 바뀌었다면 `nat_egress_ip` 를 복사해 [open.law.go.kr](https://open.law.go.kr) 의 활용 신청 수정 폼에 반영.

---

## §B. GCS 권한 드리프트

### 증상
```
law-sync-sa@laborcase-prod.iam.gserviceaccount.com does not have
storage.objects.get (또는 create) access to the Google Cloud Storage object.
```

### 원인
`terraform apply` 가 `google_storage_bucket_iam_member` 를 제거·변경함.

### 조치
```bash
cd infra/terraform
terraform plan | grep -E 'raw_sync_(writer|reader)'
# "No changes" 가 나와야 함. 변경 예정이면 apply 로 복구.
terraform apply -target=google_storage_bucket_iam_member.raw_sync_writer \
               -target=google_storage_bucket_iam_member.raw_sync_reader
```

`law-sync-sa` 는 **objectCreator + objectViewer** 둘 다 필요 (Task 5/10 참조).

---

## §C. GCS ↔ DB 비일관성

### 증상
```
object already exists at gs://laborcase-raw/law/{lsId}/{lsiSeq}.xml
— refusing to overwrite
```

### 원인
이전 실행이 "GCS put 성공 → DB insert 실패" 사이에 끊겨 **orphan GCS 객체** 만 남음. 현재 실행이 write-once 프리컨디션에 걸려 재업로드 불가.

### 조치
```bash
# 1. 해당 key 가 DB 에 있는지 확인
PGPASSWORD=$(gcloud secrets versions access latest --secret=db-app-password --project=laborcase-prod) \
  /opt/homebrew/opt/libpq/bin/psql \
  "sslmode=require host=$PUBLIC_IP port=5432 user=laborcase_app dbname=laborcase" \
  -c "SELECT 1 FROM law_version WHERE lsi_seq = '{lsi_seq}' LIMIT 1;"

# DB 에 없으면 orphan 이다. GCS 에서 삭제 후 재실행:
gsutil rm gs://laborcase-raw/law/{lsId}/{lsiSeq}.xml
gcloud run jobs execute law-full-sync --region=asia-northeast3 --project=laborcase-prod --wait
```

> ⚠️ DB 에 이미 있으면 **삭제하지 말 것** — 감사 원본이 사라진다.

### 재발 방지
- [ ] 잡 시작 시 DB↔GCS 정합성 감사 step 추가 (후속).
- [ ] `RawXmlStore` 에 "같은 content-hash 면 overwrite OK" 옵션 추가 검토.

---

## §D. article 중복 locator

### 증상
```
duplicate key value violates unique constraint "article_locator_uq"
```

### 원인
특정 법령의 XML 이 같은 `(조, 가지, 항, 호, 목)` 조합을 여러 번 emit. 2026-04-24 실측에서 **퇴직급여법·남녀고용평등법** 에서 발생.

### 현 상태
`LawSyncRepository.insertArticles` 가 `ON CONFLICT ON CONSTRAINT article_locator_uq DO NOTHING` 으로 방어. **재발생해도 sync 는 성공**하지만 일부 row 가 조용히 스킵됨.

### 조치 (본격 원인 분석이 필요하면)
```bash
# 해당 법령의 raw XML 에서 중복 locator 추출
gsutil cp gs://laborcase-raw/law/{lsId}/{lsiSeq}.xml /tmp/check.xml
# 파이썬으로 조/항/호/목 조합 카운트:
python3 -c '
import re
xml = open("/tmp/check.xml").read()
units = re.findall(r"<조문단위[^>]*>((?:(?!<조문단위).)*?)</조문단위>", xml, re.DOTALL)
# ... (구현 상세)
'
```

- XML 에 실제 중복이 있으면 법제처에 보고.
- 파서 버그라면 `LawXmlParser` 테스트 추가 후 수정.

---

## §E. Cloud SQL 연결 실패

### 증상
```
Connection refused to 10.x.x.x:5432
또는 connect timed out
```

### 원인
1. Cloud Run Job 이 VPC 에 부착되지 않았거나 subnet 이 잘못됨.
2. Cloud SQL 인스턴스가 STOPPED.
3. VPC peering (service networking) 이 끊김.

### 조치
```bash
# 1. Cloud SQL 상태
gcloud sql instances describe laborcase-pg --project=laborcase-prod \
  --format='value(state,ipAddresses[0].ipAddress)'
# state=RUNNABLE 이어야 함.

# 2. Cloud Run Job VPC 설정
gcloud run jobs describe law-full-sync --region=asia-northeast3 --project=laborcase-prod \
  --format='value(spec.template.spec.template.spec.vpcAccess)'

# 3. Terraform 으로 복구
cd infra/terraform
terraform apply -target=google_service_networking_connection.cloudsql_peering \
               -target=google_cloud_run_v2_job.law_full_sync
```

---

## §F. 일반 재시도

위 어느 분류에도 맞지 않는 일시적 오류 (네트워크 hiccup, Google 500 등):

```bash
# 단순 재시도 — idempotent 하므로 안전
gcloud run jobs execute law-full-sync \
  --region=asia-northeast3 --project=laborcase-prod --wait
```

동일 실패가 3회 이상 재현되면 §A~§E 중 어느 하나로 분류 후 조치.

---

## 사후 조치 (모든 분류 공통)

1. `sync_log` 에 `status='FAILED'` 행이 남았으면 그대로 둔다 — 감사 기록.
2. Sentry 이벤트에 짧은 코멘트 남겨 재발 여부 추적.
3. 해결 3일 내 재발하면 [분석 §R2 (실패 알림)](../../analysis/2026-04-24_national-law-open-api.md) 로 돌아가 구조 점검.

## 관련 문서

- [secret-leak.md](./secret-leak.md) — 비밀 노출 시 로테이션 절차 (이 runbook 과 겹칠 수 있음).
- [deploy-key-rotation.md](./deploy-key-rotation.md) — Deploy Key 주기 교체.
- [adr-0002-stale-data-banner.md](../decisions/adr-0002-stale-data-banner.md) — `freshness.stale=true` 가 사용자에게 어떻게 노출되는지.
- [API 플랜 §P5·§R2](../plans/2026-04-24_national-law-open-api.md) — 동기화 주기와 실패 리스크 설계.
