# 배포 runbook — laborcase API + Frontend

> 한 줄 요약: `git tag v0.1.X-{api|fe} && git push --tags` 하면 끝. 처음 셋업 시점의 함정과 롤백 절차를 박제했다.

- **Stack**: Cloud Run v2 (asia-northeast3) × 2 (api / frontend) + Cloud Run Job × 2 (sync) + Cloud SQL (private IP) + Artifact Registry + WIF + GHA.
- **자동화**: `git push origin v*` → GHA workflow → Cloud Build → Cloud Run revision 갱신.
- **운영 영향**: 1인 운영. 사용자 = `siru.haru7419@gmail.com`. 베타 트래픽 가정.

---

## 0. 초간단 — 정상 배포 시 (95% 케이스)

```bash
# 따로
git tag v0.1.X-api && git push origin v0.1.X-api
git tag v0.1.X-fe  && git push origin v0.1.X-fe

# 또는 동시 (두 tag 한 번에 push)
git tag v0.1.X-api v0.1.X-fe
git push origin v0.1.X-api v0.1.X-fe
```

> tag 명명: 반드시 **`-api` 또는 `-fe` suffix** 가 있어야 한다. `v0.1.X` 만은 어떤 workflow 도 trigger 하지 않는다 (2026-04-29 의도 외 trigger 사고 이후 정밀화).

GitHub Actions 페이지 (`https://github.com/siruharu/laborcase/actions`) 에서 진행 추적. 마지막 step 의 service URL 이 응답 200 이면 성공.

**현재 prod URL**:
- API: `https://laborcase-api-mxq42pqgaa-du.a.run.app`
- Frontend: `https://laborcase-frontend-mxq42pqgaa-du.a.run.app`

---

## 1. 처음 셋업 체크리스트 (한 번만)

새 GCP 프로젝트에서 처음부터 시작하는 경우 — 이미 끝났다면 다음 절을 건너뛴다.

### 1.1 인프라 적용 (terraform)
```bash
cd infra/terraform
terraform init
terraform apply -auto-approve
```

생성될 리소스 (요약):
- VPC + Cloud NAT + 고정 외부 IP
- Cloud SQL `laborcase-pg` (private IP)
- Secret Manager × 4 (`law-oc`, `db-app-password`, `upstage-api-key`, `sentry-dsn`)
- Cloud Run **Job** × 2 (full-sync / delta-sync) + Cloud Scheduler
- Cloud Run **Service** × 2 (api / frontend)
- Service accounts: `api-sa`, `frontend-sa`, `law-sync-sa`, `deployer-sa`, `scheduler-sa`
- WIF: `github-pool` + `github-oidc` provider
- Artifact Registry `laborcase-images`
- Cloud Monitoring uptime check + alert policy + email channel

### 1.2 secret 값 주입 (수동, 1회성)
secret 리소스는 terraform 이 만들지만 **값은 따로 넣어야**. shell history 에 안 남도록 `--data-file=-` + Ctrl+D.

```bash
# 법제처 OC
gcloud secrets versions add law-oc --data-file=- --project=laborcase-prod
# (입력 후 Ctrl+D)

# Upstage API key
gcloud secrets versions add upstage-api-key --data-file=- --project=laborcase-prod

# Sentry DSN (선택, 안 넣으면 starter no-op)
echo "https://...@sentry.io/..." | gcloud secrets versions add sentry-dsn --data-file=- --project=laborcase-prod
```

### 1.3 GitHub Secrets 등록
GitHub repo (`siruharu/laborcase`) → **Settings → Secrets and variables → Actions** → New repository secret:

| Name | 값 |
|---|---|
| `WIF_PROVIDER` | `terraform output wif_provider_name` |
| `DEPLOYER_SA_EMAIL` | `terraform output deployer_sa_email` |
| `GCP_PROJECT` | `laborcase-prod` |

### 1.4 법제처 NAT IP 등록
```bash
terraform output nat_egress_ip
```
→ 법제처 OPEN API 신청 수정 페이지에 IP 등록 (이미 done 인 경우 skip).

### 1.5 첫 manual 이미지 push (terraform service 가 :latest 를 expect)
```bash
gcloud builds submit --config api/cloudbuild.yaml \
  --substitutions=COMMIT_SHA=manual1 --project=laborcase-prod .
gcloud builds submit --config frontend/cloudbuild.yaml \
  --substitutions=COMMIT_SHA=manual1,_NEXT_PUBLIC_API_BASE_URL=https://<api-url> \
  --project=laborcase-prod .
```

### 1.6 Cloud Monitoring 채널 verification
첫 배포 시 GCP 가 `siru.haru7419@gmail.com` 으로 verification email 발송. 클릭해야 alert 실제 발송.

### 1.7 Billing budget (manual 5분)
terraform 으로 자동화 시도 후 invalid argument 400 거부 → console 셋업:
1. Cloud Console → **Billing** → **Budgets & alerts** → **Create budget**
2. Name `laborcase monthly budget 50 USD`
3. Type: Specified amount, $50 USD
4. Scope: Project `laborcase-prod`
5. Thresholds: Actual 60% / 80% / 100% + Forecasted 100%
6. Email recipients: 자동 (billing user)

### 1.8 첫 sync (선택)
```bash
gcloud run jobs execute law-full-sync --region asia-northeast3 --project laborcase-prod
```

---

## 2. 일반 배포 (tag-based)

### 흐름
1. main 에 PR 머지 (코드 변경).
2. tag push.
3. GHA workflow 실행 (~5-10분).
4. Cloud Run 새 revision 으로 traffic 100% 라우팅.

### tag 명명 규칙

- `v0.1.X` — API + Frontend 동시 배포 (둘 다 trigger).
- `v0.1.X-api` — API 만 (frontend 코드 미변경).
- `v0.1.X-fe` — Frontend 만 (API 미변경).

### workflow 가 하는 일

**Deploy API** (`.github/workflows/deploy-api.yml`):
1. `actions/checkout@v4`
2. WIF auth → `deployer-sa` impersonate
3. `gcloud builds submit --config api/cloudbuild.yaml`
4. `gcloud run deploy laborcase-api --image ...:<sha> --update-env-vars SENTRY_RELEASE=<sha>`
5. service URL echo

**Deploy Frontend** (`.github/workflows/deploy-frontend.yml`):
1~2 동일.
3. `gcloud run services describe laborcase-api` 로 API URL 동적 조회
4. `gcloud builds submit --config frontend/cloudbuild.yaml --substitutions=_NEXT_PUBLIC_API_BASE_URL=<api-url>`
5. `gcloud run deploy laborcase-frontend --image ...:<sha>`

---

## 3. 롤백

새 revision 이 문제 있을 때 이전 revision 으로 100% traffic 되돌리기.

```bash
# 1) revision 목록 확인
gcloud run revisions list --service laborcase-api \
  --region asia-northeast3 --project laborcase-prod

# 2) 이전 revision 으로 traffic 100% 라우팅
gcloud run services update-traffic laborcase-api \
  --region asia-northeast3 --project laborcase-prod \
  --to-revisions=laborcase-api-00007-xyz=100
```

복구 후 원인 분석 + 코드 수정 + 새 tag 로 재배포.

---

## 4. 긴급 배포 (CI 우회)

GHA 가 down 또는 secret 오설정으로 정상 배포 불가 시.

```bash
# (사용자 로컬에서)
gcloud auth login
gcloud config set project laborcase-prod

# 이미지 빌드 + push
gcloud builds submit --config api/cloudbuild.yaml \
  --substitutions=COMMIT_SHA=$(git rev-parse --short HEAD) \
  --project=laborcase-prod .

# Cloud Run revision 갱신
gcloud run deploy laborcase-api \
  --region asia-northeast3 \
  --project laborcase-prod \
  --image asia-northeast3-docker.pkg.dev/laborcase-prod/laborcase-images/api:$(git rev-parse --short HEAD)
```

이후 GHA 가 복구되면 동일 SHA tag 로 다시 push 해 traceability 회복.

---

## 5. 트러블슈팅

### 5.1 GHA workflow 의 IAM 거부 (첫 셋업의 흔한 함정)

새 GCP 프로젝트(2024+) 에서 GHA 를 처음 셋업하면 보통 IAM trial-and-error 3 라운드를 만난다. wif.tf 가 모두 반영하고 있으나 새 환경에서 재현 시 참고:

| 라운드 | 에러 메시지 | fix |
|---|---|---|
| 1 | `forbidden from accessing the bucket [...cloudbuild]` | `roles/cloudbuild.builds.builder` 단일 grant. `editor` + `serviceUsageConsumer` 조합으론 부족 |
| 2 | `caller does not have permission to act as service account .../<num>` | unique ID `<num>` 가 가리키는 SA 가 default compute (`<project-num>-compute@developer.gserviceaccount.com`). deployer-sa 에 그 SA 의 `roles/iam.serviceAccountUser` 부여 |
| 3 | (run 단계) `iam.serviceAccounts.actAs` denied on api-sa or frontend-sa | wif.tf 가 이미 `actas_api` / `actas_frontend` binding 함. 누락이면 추가 |

### 5.2 Cloud Run service 가 startup probe 로 죽음 (첫 배포 시)

Spring Boot context 가 시작 못 함. Cloud Logging 에서 stack trace 확인:
```bash
gcloud logging read 'resource.type=cloud_run_revision AND resource.labels.service_name=laborcase-api AND severity>=WARNING' \
  --project=laborcase-prod --limit=30 --format='value(textPayload)'
```

자주 만나는 원인:
- **Sentry SDK 가 빈/whitespace DSN URI parse fail** → SENTRY_DSN env 자체를 service.tf 에서 빼거나 valid DSN seed 후 복원.
- **SyncConfig.lawOpenApiClient 가 LAW_OC 빈 값에 throw** → API service 에도 LAW_OC env 주입 (sync 와 동일 secret).
- **DATABASE_PASSWORD secret 미seed** → `gcloud secrets versions add db-app-password ...`.

### 5.3 frontend 가 prod backend 호출 시 CORS 거부

`Access-Control-Allow-Origin` 응답 헤더가 비었거나 다른 origin echo. `variables.tf` 의 `frontend_allowed_origin` default 가 현재 frontend Cloud Run URL 과 일치하는지 확인.

frontend service 가 재생성되어 hash 가 바뀌면 manual update:
```bash
# 새 frontend URL 확인
terraform output frontend_service_url

# variables.tf 의 default 수정 후
terraform apply
```

### 5.4 Sentry DSN seed 시 service.tf 복원

`sentry-dsn` secret 에 valid DSN 등록되면:
```bash
# 1. secret seed
echo "https://...@sentry.io/..." | gcloud secrets versions add sentry-dsn \
  --data-file=- --project=laborcase-prod

# 2. api_service.tf 의 SENTRY_DSN env block 주석 해제 (현재는 주석)
```
그 후 다음 deploy 부터 Sentry SDK 가 활성화.

### 5.5 budget alert 가 안 옴

Cloud Billing console 에서 budget 이 만들어졌는지 + email recipient 가 본인인지 확인. 새 환경에서 처음 만드는 경우 §1.7 절차 따라 manual 셋업.

### 5.6 sync job 실패

별도 runbook 참조: [`law-sync-failure.md`](./law-sync-failure.md).

---

## 6. 운영 메타

### 6.1 가용성 / 모니터링
- **Uptime check**: `/actuator/health/liveness` 60초 간격, 3 region (Asia/USA/Europe). 5분 연속 fail 시 email alert.
- **Sentry**: API 의 application error 자동 capture. release tag = git SHA.
- **Cloud Logging**: stdout/stderr 자동. `gcloud logging read 'resource.type=cloud_run_revision'`.

### 6.2 비용 (~$60-100/월 가정)
- Cloud SQL `db-custom-1-3840` ZONAL — ~$50/월 (dominant).
- Cloud NAT 고정 IP — ~$1.5/월.
- Cloud Run × 2 (min=0) — ~$0-5/월.
- Cloud Run Job (sync) — ~$0.1/월.
- Artifact Registry storage (api 198 MB + frontend 70 MB) — 무시.
- Cloud Build (free tier 120분/일) — 무료.

### 6.3 보안 layer
- Cloud Run invoker = `allUsers` (laborcase 본질이 공개 정보 제공).
- Secret 관리: Secret Manager + 코드 인라인 절대 X (CLAUDE.md).
- WIF: deployer-sa 만 GHA impersonate, attribute_condition 으로 외부 fork 차단.
- runtime SA (api-sa / frontend-sa / law-sync-sa) 권한 최소화 — deploy 권한 분리 (deployer-sa).

### 6.4 GitHub Actions 가 처음이라면
GHA 는 GitHub 안에서 무료로 동작하는 CI. workflow 정의는 `.github/workflows/*.yml`. 트리거 (push / tag / PR) 가 발생하면 GitHub 가 임시 ubuntu VM 띄워 yaml 의 step 실행.

우리 workflow 는 **WIF (Workload Identity Federation)** 으로 GCP 인증. service account JSON key 같은 비밀 파일을 GitHub Secrets 에 두지 않고, 실행 시 GitHub OIDC token → GCP 가 검증 → deployer-sa 의 access token 으로 교환. 키 유출 위험 0.

actions tab 에서 진행 추적 + step 별 stdout/stderr log. 실패 step 의 우상단 **Re-run all jobs** 로 재시도.

---

## 7. follow-up (이 runbook 의 한계)

다음은 별도 ADR / plan 으로 다뤄지지 않은 항목들 — 트래픽 성장 시 검토:

- **Domain mapping** (사용자 도메인 등록 후) 또는 HTTPS LB + Cloud Armor.
- **min_instances=1** 으로 cold start 회피 (~$50/월 추가).
- **Multi-origin CORS** (staging.laborcase.kr 도입 시) → `WebMvcConfigurer` 마이그레이션.
- **WAF / rate limit** (abuse 발견 시) — Cloudflare 또는 Cloud Armor.
- **Multi-region** (서울 region outage 위험 줄이기).
- **Sentry frontend 도입** (현재 API 만).

## 참고 노트
- 분석: `{Vault}/10_Projects/laborcase/02_Analysis/2026-04-27_gcp-prod-deploy.md`
- 플랜: `{Vault}/10_Projects/laborcase/03_Plan/2026-04-27_gcp-prod-deploy.md`
- ADR-0004 (배포 결정): `docs/decisions/adr-0004-cloud-run-prod-deploy.md` (DT-Task 16)
- 다른 runbook: `secret-leak.md`, `deploy-key-rotation.md`, `law-sync-failure.md`
