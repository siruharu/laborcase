# infra/terraform

laborcase GCP 인프라. 현재 Task 1 범위 (VPC + Cloud NAT + Secret Manager + 서비스계정).

## 상태 (State)

- **backend**: GCS `gs://laborcase-tfstate` (versioning 켜짐, 리전 `asia-northeast3`).
- **prefix**: `root` → `gs://laborcase-tfstate/root/default.tfstate`.
- 로컬 `.terraform/`, `*.tfstate*` 는 `.gitignore` 에서 차단.

## 초기화 & 적용

```bash
# 한 번만: ADC 인증 (Terraform provider 가 쓰는 자격증명)
gcloud auth application-default login

# 매번
cd infra/terraform
terraform init          # 최초 + 프로바이더 업그레이드 시
terraform fmt -check    # 포매팅 체크
terraform validate      # 문법 검증 (자격증명 없이도 동작)
terraform plan
terraform apply
```

## 관리 대상

- `laborcase-vpc` + 단일 subnet (asia-northeast3, 10.10.0.0/20)
- `laborcase-router` + `laborcase-nat` + `laborcase-nat-egress` (고정 외부 IP)
- `law-oc` Secret Manager secret (값은 별도 주입)
- `law-sync-sa`, `api-sa` 서비스계정 + Secret 접근 권한

## OC 값 주입 (비-Terraform 경로)

Terraform 은 secret **리소스만** 만든다. 값은 state 에 남지 않도록 별도 주입한다.

```bash
# stdin 으로 주입 (shell 히스토리에 값이 남지 않는다)
gcloud secrets versions add law-oc --data-file=- --project=laborcase-prod
# 프롬프트에 OC 값 입력 후 Ctrl+D
```

로테이션은 [docs/runbooks/secret-leak.md](../../docs/runbooks/secret-leak.md) 의 법제처 OC 섹션 참조.

## Cloud NAT IP 등록

`terraform apply` 후 출력되는 `nat_egress_ip` 값을 법제처 [open.law.go.kr → OPEN API 신청 수정](https://open.law.go.kr) 에서 **도메인 주소** 또는 **IP 주소** 필드에 등록한다. 이 단계 전까지 Cloud Run 에서 호출 시 "사용자 정보 검증 실패" 응답이 온다.

## 후속 Task 에서 추가될 리소스

- Task 2: Cloud SQL + pgvector + private services access peering
- Task 5: GCS `laborcase-raw` 버킷 (법령 XML 불변 보관)
- Task 10: Cloud Scheduler 2개 (full-sync, delta-sync)

각 Task 는 이 디렉토리에 새 `.tf` 파일을 추가한다.
