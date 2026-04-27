# ADR-0004: 프로덕션 배포 = Cloud Run v2 + GitHub Actions WIF

- **Date**: 2026-04-27
- **Status**: Accepted
- **Context sources**:
  - 리서치: [`docs/research/2026-04-27_gcp-prod-deploy.md`](../../{Vault}/10_Projects/laborcase/01_Research/2026-04-27_gcp-prod-deploy.md)
  - 분석: [`{Vault}/10_Projects/laborcase/02_Analysis/2026-04-27_gcp-prod-deploy.md`](../../{Vault}/10_Projects/laborcase/02_Analysis/2026-04-27_gcp-prod-deploy.md)

## 컨텍스트

laborcase 는 12 개 백엔드 task + 12 개 프론트엔드 task 로 코드 / 인프라 일부 (VPC + Cloud SQL + Cloud Run **Job** sync × 2) 만 동작하는 상태였다. **HTTP API service / Frontend service** 자체와 CI/CD 가 비어있어 배포 path 가 매번 manual.

다음을 결정해야 했다:
1. API service host: Cloud Run v2 vs Cloud Run gen1 vs GKE.
2. Frontend host: Cloud Run + Dockerfile vs Vercel.
3. VPC 연결: Direct VPC Egress vs VPC Connector.
4. Cloud SQL 연결: private IP 직결 vs Auth Proxy 사이드카.
5. CI/CD: GitHub Actions + WIF vs Cloud Build trigger vs manual.
6. 도메인: Cloud Run domain mapping vs HTTPS LB vs Cloudflare.
7. min-instances: 0 (콜드 스타트 감수) vs 1 (~$50/월).
8. CORS: 단일 origin vs WebMvcConfigurer multi-origin.

## 결정

### 핵심 (Accepted)

1. **API + Frontend 모두 Cloud Run v2 Service** (asia-northeast3, allUsers invoker).
2. **Direct VPC Egress** (Connector 기각). API 만 VPC 연결 (Cloud SQL private IP 접근). Frontend 는 VPC 없이 public egress.
3. **Cloud SQL private IP 직결** (Auth Proxy 사이드카 기각). 비밀번호는 Secret Manager.
4. **GitHub Actions + Workload Identity Federation (keyless)**. SA key JSON 사용 안 함. tag-based deploy (`v*-api`, `v*-fe`, `v*`).
5. **별도 deployer-sa** (runtime SA api-sa / frontend-sa 와 분리). minimal IAM.
6. **Cloud Build (`gcloud builds submit`) 가 빌더**. Cloud Run revision 갱신은 GHA 의 `gcloud run deploy` 가 담당.
7. **min_instance_count = 0** (둘 다). startup CPU boost. **트리거 시 1 로 전환** — P95 first-request latency > 5s 또는 cold-start ratio > 30%.
8. **CORS 단일 origin** (frontend Cloud Run URL). multi-origin 필요 시 `WebMvcConfigurer` 마이그레이션.
9. **Cloud Monitoring uptime check + email alert** (5분 fail). **Sentry release auto-tag** (CI 가 commit-sha 주입).
10. **Cloud Billing budget**: $50/월, 60/80/100% threshold — terraform 자동화 거부 → console manual 5분.

### Deferred

- **Domain Mapping** (사용자 도메인 미보유) → 베타는 `*.run.app`. 도메인 등록 시 mapping 또는 HTTPS LB.
- **HTTPS Load Balancer** + Cloud Armor — 트리거: DAU 100+ 또는 P95 > 1s.
- **Frontend Sentry** — 현재 API 만.
- **WAF / rate limit** — 트리거: abuse 발견.
- **Multi-region / multi-tenant** — Phase 2 이후.

## 선택 이유

### Cloud Run v2 Service (API/Frontend) — Vercel 기각

- **Vercel Hobby**: "personal/non-commercial only" (2026-02 갱신 약관). 광고 / 결제 / 사업체 분류 시 Pro $20/월 필요. laborcase 가 향후 광고 의도 있어 약관 회색지대.
- **Cloud Run 비용**: 베타 ~$0-5/월 (min=0), Vercel Pro 보다 저렴.
- **백엔드 GCP 와 단일 콘솔/IAM 통합** — 1인 운영에 인지 부담 작음.
- **한국 트래픽 latency**: asia-northeast3 (서울) 단일 region — cross-cloud egress 없음.

### Direct VPC Egress — Connector 기각

- **GA**: Service 2024-04, Job 2024-09. 2026 update — instance 당 IP 사용 4× → 2× 감소.
- **추가 비용 0** (connector 시간당 ~$8-30/월 회피).
- **Latency 한 hop 적음**.
- 새 인프라라 legacy (connector) 부담 없음.

### Cloud SQL Private IP 직결 — Auth Proxy 사이드카 기각

- 우리 instance 가 이미 **private IP only** 라 직결 자연스러움.
- Auth Proxy 의 IAM 인증 / 회전 SSL 이점은 매력적이나 Admin API quota 사용 + 사이드카 운영 비용. MVP 단계에선 over-engineering.

### GitHub Actions + WIF — Cloud Build trigger / manual 기각

- **2023 SA key JSON deprecated** (Google 공식). public repo + key 보관 위험 0 원칙.
- **`google-github-actions/auth@v3`** — 2026 시점 표준.
- **Generic CI** — 사용자의 다른 프로젝트 (Petory, BaseballLab) 와 transferable.
- **Cloud Build trigger** 도 좋지만 GCP-only — 잠금 위험 약간.

### 별도 deployer-sa — runtime SA 직접 binding 기각

- runtime SA (api-sa / frontend-sa) 가 deploy 권한 (run.admin / cloudbuild.editor / actAs) 까지 가지면 권한 epeneration + 사고 시 blast radius 큼.
- deployer-sa 는 deploy-time only — runtime 트래픽 받지 않음.

### min=0 — min=1 기각 (베타 단계)

- 베타 비용 민감도 높음 ($50/월 추가 회피).
- 검색 자체가 1-2s 걸리는 도메인 (Upstage embed) 이라 cold start 5s 추가 영향 작음.
- Skeleton UI (FT-Task 7) 가 첫 응답 흡수.

### Domain Mapping deferred — 사용자 도메인 미보유

- 베타 `*.run.app` URL 로 시작 — Cloud Run 자동 cert + URL stable.
- 도메인 등록 시 mapping (preview / 무료) 또는 HTTPS LB ($18/월) 선택.

## 영향 / 결과

### 비용 (월, 가정)

| 항목 | 비용 |
|---|---|
| Cloud SQL `db-custom-1-3840` ZONAL | ~$50 (dominant) |
| Cloud NAT 고정 IP | ~$1.5 |
| Cloud Run × 2 (min=0) | ~$0-5 |
| Cloud Run Job (sync, 1회/일) | ~$0.1 |
| Artifact Registry storage | <$0.5 |
| Cloud Build (free 120분/일) | $0 |
| **합계** | **~$60-100** |

### 운영

- `git push origin v0.1.X` 한 줄로 prod 배포.
- 사고 시 `gcloud run services update-traffic --to-revisions=` 로 즉시 롤백.
- `docs/runbooks/deploy.md` 가 첫 셋업 / 트러블슈팅 / 운영 메타 박제.

### 보안

- Cloud Run invoker = `allUsers` (정보 제공 서비스 본질).
- WIF attribute_condition 으로 외부 fork 의 token 발급 차단.
- runtime SA / deploy SA 권한 분리 — minimal IAM 원칙 (분석 §P5-c).

## 만난 함정 (DISCOVERED, runbook 박제)

### Cloud Run v2 Service startup probe — 4종 fail
1. SENTRY_DSN secret 미seed → service 거부 → secret seed 또는 env 제거.
2. Sentry SDK 가 single-space DSN URI parse fail → env 자체 제거 (valid DSN 받으면 복원).
3. SyncConfig.lawOpenApiClient bean 이 prod profile 에서도 평가 → LAW_OC env 추가.
4. DATABASE_PASSWORD secret 미seed → secret seed.

### GHA + WIF — IAM 3 라운드
1. `cloudbuild.builds.editor` 단독 → SERVICE_USE 부족.
2. `serviceUsageConsumer` 추가 → 같은 거부 (combination 부족) → `cloudbuild.builds.builder` 단일 (combined role).
3. build runner = default compute SA (신규 프로젝트 정책) → `iam.serviceAccountUser` on `<num>-compute@developer` 필요.

### 기타
- `terraform google_billing_budget` 가 invalid argument 400 (specific field 명시 X) 거부 → console manual.
- `selected_regions` 최소 3개 필수 (uptime check).
- ADC quota_project 부재 — `user_project_override = true` + `billing_project` 설정.

상세 절차는 [`docs/runbooks/deploy.md`](../runbooks/deploy.md) §5 트러블슈팅.

## 후속

- 사용자 도메인 등록 시 Domain Mapping 또는 HTTPS LB 마이그레이션 (별도 ADR).
- 트래픽 증가 시 `min_instance_count = 1` 전환.
- staging 환경 도입 시 multi-origin CORS 마이그레이션.
- WAF / rate limit (Cloudflare 또는 Cloud Armor) — abuse 발견 시.
- Multi-region — 트래픽 polling 후.

## 참고

- 리서치: `{Vault}/10_Projects/laborcase/01_Research/2026-04-27_gcp-prod-deploy.md`
- 분석: `{Vault}/10_Projects/laborcase/02_Analysis/2026-04-27_gcp-prod-deploy.md`
- 플랜: `{Vault}/10_Projects/laborcase/03_Plan/2026-04-27_gcp-prod-deploy.md`
- 운영 runbook: [`docs/runbooks/deploy.md`](../runbooks/deploy.md)
- 관련 ADR: [`adr-0001-repo-split.md`](./adr-0001-repo-split.md), [`adr-0002-stale-data-banner.md`](./adr-0002-stale-data-banner.md), [`adr-0003-embedding-model.md`](./adr-0003-embedding-model.md)
