# infra/terraform

laborcase GCP 인프라 — VPC + Cloud SQL + Cloud Run (services + jobs) + Secret Manager + Artifact Registry + WIF + Monitoring.

## 상태 (State)

- **backend**: GCS `gs://laborcase-tfstate` (versioning 켜짐, 리전 `asia-northeast3`).
- **prefix**: `root` → `gs://laborcase-tfstate/root/default.tfstate`.
- 로컬 `.terraform/`, `*.tfstate*` 는 `.gitignore` 에서 차단.

## 초기화 & 적용

```bash
# 한 번만: ADC 인증
gcloud auth application-default login

# 매번
cd infra/terraform
terraform init          # 최초 + 프로바이더 업그레이드 시
terraform fmt -check    # 포매팅 체크
terraform validate      # 문법 검증 (자격증명 없이도 동작)
terraform plan
terraform apply
```

## 파일 / 관리 대상

| 파일 | 관리 리소스 |
|---|---|
| `main.tf` | google / google-beta provider (`user_project_override = true`) |
| `versions.tf` / `backend.tf` | required providers + GCS backend |
| `variables.tf` | project_id, region, github_owner, alert_email, billing_account_id, monthly_budget_usd, api_min/max_instances, frontend_allowed_origin |
| `outputs.tf` | URL / SA email / WIF provider name 등 |
| `network.tf` | `laborcase-vpc` + subnet + Cloud Router + Cloud NAT (`nat_egress_ip` 고정) |
| `db.tf` | Cloud SQL `laborcase-pg` (private IP, POSTGRES_16) + db-app-password secret |
| `service-accounts.tf` | `law-sync-sa`, `api-sa` 기본 SA |
| `storage.tf` | GCS `laborcase-raw` (XML 불변 보관) |
| `secrets.tf` | `law-oc` secret resource (값은 외부 주입) |
| `run_jobs.tf` | Artifact Registry `laborcase-images` + Cloud Run **Job** × 2 (full-sync / delta-sync) + Cloud Scheduler |
| `api_service.tf` | Cloud Run **Service** `laborcase-api` (Direct VPC Egress, private IP JDBC, secret env, allUsers invoker) |
| `frontend_service.tf` | Cloud Run **Service** `laborcase-frontend` (Next.js standalone, no VPC, allUsers invoker) |
| `wif.tf` | Workload Identity Federation pool/provider + `deployer-sa` + `frontend-sa` + IAM bindings (deploy-time + actAs) |
| `observability.tf` | email notification channel + uptime check (`/actuator/health/liveness` 60s × 3 region) + alert policy. billing budget 은 console manual (DT-Task 13). |

## Secret 값 주입 (비-Terraform 경로)

Terraform 은 secret **리소스만** 만든다. 값은 state 에 남지 않도록 별도 주입한다.

```bash
# 법제처 OC
gcloud secrets versions add law-oc --data-file=- --project=laborcase-prod
# (입력 후 Ctrl+D)

# Upstage API key
gcloud secrets versions add upstage-api-key --data-file=- --project=laborcase-prod

# Sentry DSN (선택, 안 넣으면 starter no-op)
echo "https://...@sentry.io/..." | gcloud secrets versions add sentry-dsn --data-file=- --project=laborcase-prod
```

로테이션은 [`docs/runbooks/secret-leak.md`](../../docs/runbooks/secret-leak.md) 참조.

## Cloud NAT IP 등록 (법제처)

`terraform apply` 후 출력되는 `nat_egress_ip` 를 법제처 [open.law.go.kr → OPEN API 신청 수정](https://open.law.go.kr) 의 **도메인 주소** 또는 **IP 주소** 필드에 등록. 이 단계 전엔 sync job 호출 시 "사용자 정보 검증 실패" 응답.

## GitHub Actions WIF — GitHub Secrets 등록

`terraform apply` 후 다음 output 을 GitHub repo Secrets 에 등록 ([`docs/runbooks/deploy.md`](../../docs/runbooks/deploy.md) §1.3):

| GitHub Secret | terraform output |
|---|---|
| `WIF_PROVIDER` | `wif_provider_name` |
| `DEPLOYER_SA_EMAIL` | `deployer_sa_email` |
| `GCP_PROJECT` | `laborcase-prod` (또는 `var.project_id`) |

## Billing budget — console manual

`google_billing_budget` 자동화가 invalid argument 로 거부 → Cloud Console 에서 5분 셋업. 절차는 [`docs/runbooks/deploy.md`](../../docs/runbooks/deploy.md) §1.7.

## Cloud Run image lifecycle

`api_service.tf` / `frontend_service.tf` 의 `containers.image` 는 `lifecycle.ignore_changes` 로 terraform plan 에서 제외. CI (GHA workflow) 가 `gcloud run deploy --image ...:<git-sha>` 로 갱신. terraform 은 인프라 모양만, image rotation 은 git tag 기반.

## 의존성 / apply 순서

대부분 자동 dependency. 한 번 apply 면 끝나지만 **첫 셋업 시 secret 값 주입** 이 service 시작에 필수:

1. terraform apply (모든 리소스 생성, service 는 placeholder image 로 시작 시도)
2. `gcloud secrets versions add` × 4 (law-oc, upstage-api-key, sentry-dsn 또는 service.tf 의 SENTRY_DSN block 주석 처리, db-app-password 는 terraform 자동)
3. 첫 manual 이미지 push (`gcloud builds submit`) — service 가 이미지 받아 시작 가능
4. service URL 응답 확인

상세는 [`docs/runbooks/deploy.md`](../../docs/runbooks/deploy.md) §1.

## 관련 ADR

- [`adr-0001-repo-split.md`](../../docs/decisions/adr-0001-repo-split.md) — public/private 레포 분리
- [`adr-0002-stale-data-banner.md`](../../docs/decisions/adr-0002-stale-data-banner.md) — freshness 메타
- [`adr-0003-embedding-model.md`](../../docs/decisions/adr-0003-embedding-model.md) — Upstage embedding
- [`adr-0004-cloud-run-prod-deploy.md`](../../docs/decisions/adr-0004-cloud-run-prod-deploy.md) — 본 인프라 배포 결정
