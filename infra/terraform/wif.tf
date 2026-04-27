# Workload Identity Federation — keyless GitHub Actions → GCP authentication.
#
# Trust chain at deploy time:
#
#   GitHub Actions worker
#     → mints OIDC ID token
#       (sub = "repo:siruharu/laborcase:ref:refs/tags/v0.1.0")
#     → token is verified against the WIF provider below
#       (issuer + attribute_condition)
#     → token is exchanged for a Google access token that *impersonates*
#       google_service_account.deployer
#     → deployer-sa runs `gcloud builds submit` and `gcloud run deploy`.
#
# Why a separate deployer-sa (not api-sa / frontend-sa direct)?
#   * api-sa / frontend-sa are RUNTIME identities — they hold only the
#     least privileges needed to serve traffic (Secret Manager accessor,
#     Cloud SQL client). Granting them deploy-time roles (run.admin,
#     cloudbuild.builds.editor, actAs) would mix concerns and fail the
#     "minimal IAM" principle from analysis §P5-c.
#   * deployer-sa exists only as a deployment vehicle; it has no env
#     access to any service.
#
# The attribute_condition narrows token issuance to exactly two repos
# under the `github_owner` — anyone else's workflow (forks, other repos)
# is rejected at the provider level even before iam.workloadIdentityUser
# binding is checked.

resource "google_iam_workload_identity_pool" "github" {
  workload_identity_pool_id = "github-pool"
  display_name              = "GitHub Actions"
  description               = "OIDC trust for GHA workflows in laborcase / laborcase-internal."
}

resource "google_iam_workload_identity_pool_provider" "github_oidc" {
  workload_identity_pool_id          = google_iam_workload_identity_pool.github.workload_identity_pool_id
  workload_identity_pool_provider_id = "github-oidc"
  display_name                       = "GitHub OIDC"
  description                        = "Trusts tokens from GitHub Actions for the configured owner only."

  oidc {
    issuer_uri = "https://token.actions.githubusercontent.com"
  }

  # Map the GitHub OIDC claims we care about into Google attributes so
  # IAM policies can reference `attribute.repository`, etc.
  attribute_mapping = {
    "google.subject"             = "assertion.sub"
    "attribute.repository"       = "assertion.repository"
    "attribute.repository_owner" = "assertion.repository_owner"
    "attribute.ref"              = "assertion.ref"
    "attribute.workflow_ref"     = "assertion.workflow_ref"
  }

  # Only owner siruharu, only the two laborcase repos. Belt-and-suspenders
  # vs the principalSet binding below.
  attribute_condition = "assertion.repository_owner == \"${var.github_owner}\" && (assertion.repository == \"${var.github_owner}/laborcase\" || assertion.repository == \"${var.github_owner}/laborcase-internal\")"
}

# ── Deployer service account ───────────────────────────────────────────

resource "google_service_account" "deployer" {
  account_id   = "deployer-sa"
  display_name = "laborcase - CI deployer"
  description  = "Impersonated by GitHub Actions (via WIF) to build images and deploy Cloud Run revisions. Holds NO runtime privileges."
}

# Allow GHA tokens (any tag/branch) from the two repos to impersonate
# deployer-sa. `attribute.repository` is taken from the OIDC claim per the
# mapping above.
resource "google_service_account_iam_member" "deployer_wif_laborcase" {
  service_account_id = google_service_account.deployer.name
  role               = "roles/iam.workloadIdentityUser"
  member             = "principalSet://iam.googleapis.com/${google_iam_workload_identity_pool.github.name}/attribute.repository/${var.github_owner}/laborcase"
}

resource "google_service_account_iam_member" "deployer_wif_laborcase_internal" {
  service_account_id = google_service_account.deployer.name
  role               = "roles/iam.workloadIdentityUser"
  member             = "principalSet://iam.googleapis.com/${google_iam_workload_identity_pool.github.name}/attribute.repository/${var.github_owner}/laborcase-internal"
}

# ── Deploy-time roles for deployer-sa ──────────────────────────────────
# Cloud Build (`gcloud builds submit`) + Cloud Run revision update.

# `roles/cloudbuild.builds.builder` 는 standalone Cloud Build 호출자에
# 권장되는 combined role. cloudbuild.builds.editor + serviceusage.services
# .use + storage.buckets.* + storage.objects.* + secretmanager.versions
# .access 등을 한 묶음으로 부여한다.
#
# 발견 경위: 첫 GHA 실행이 cloudbuild.builds.editor + serviceusage.
# serviceUsageConsumer 조합으로도 staging bucket 접근에서 거부됨. GCP 의
# 새 Cloud Build 정책 (2024+) 에서 builder role 이 사실상 표준.
resource "google_project_iam_member" "deployer_cloudbuild_builder" {
  project = var.project_id
  role    = "roles/cloudbuild.builds.builder"
  member  = "serviceAccount:${google_service_account.deployer.email}"
}

# Cloud Build streams source via gs://<project>_cloudbuild bucket; the
# editor role above includes that, but the legacy bucket needs an explicit
# ObjectAdmin in some accounts.
resource "google_project_iam_member" "deployer_storage_object_admin" {
  project = var.project_id
  role    = "roles/storage.objectAdmin"
  member  = "serviceAccount:${google_service_account.deployer.email}"
}

resource "google_project_iam_member" "deployer_run_admin" {
  project = var.project_id
  role    = "roles/run.admin"
  member  = "serviceAccount:${google_service_account.deployer.email}"
}

# `gcloud run deploy` sets the revision's runtime SA to api-sa or
# frontend-sa; deployer-sa needs `actAs` on each.
resource "google_service_account_iam_member" "deployer_actas_api" {
  service_account_id = google_service_account.api.name
  role               = "roles/iam.serviceAccountUser"
  member             = "serviceAccount:${google_service_account.deployer.email}"
}

resource "google_service_account_iam_member" "deployer_actas_frontend" {
  service_account_id = google_service_account.frontend.name
  role               = "roles/iam.serviceAccountUser"
  member             = "serviceAccount:${google_service_account.deployer.email}"
}

# Cloud Build 의 build runner 가 default compute SA
# (`<project-number>-compute@developer.gserviceaccount.com`) 인 신규
# 프로젝트(2024+) 정책. `gcloud builds submit` 호출자(deployer-sa) 는 이
# 빌드 SA 를 actAs 권한이 필요. 발견: GHA 첫 실행에서 ERROR
# "caller does not have permission to act as service account .../<num>"
# (113339563568809375726 = 936735263575-compute@developer.gserviceaccount.com).
data "google_project" "current" {
  project_id = var.project_id
}

resource "google_service_account_iam_member" "deployer_actas_default_compute" {
  service_account_id = "projects/${var.project_id}/serviceAccounts/${data.google_project.current.number}-compute@developer.gserviceaccount.com"
  role               = "roles/iam.serviceAccountUser"
  member             = "serviceAccount:${google_service_account.deployer.email}"
}

# Read images during deploy + write images during Cloud Build. Cloud Build
# itself runs as the *project Cloud Build SA*, but the deployer is what
# actually queues the build, so the writer role is stamped here for
# clarity (project-level grants belong to Cloud Build SA already).
resource "google_artifact_registry_repository_iam_member" "deployer_writer" {
  repository = google_artifact_registry_repository.images.name
  location   = google_artifact_registry_repository.images.location
  role       = "roles/artifactregistry.writer"
  member     = "serviceAccount:${google_service_account.deployer.email}"
}

# ── Frontend runtime service account ───────────────────────────────────
# Mirrors api-sa but for the Next.js Cloud Run service. Specific role
# bindings (artifactregistry.reader for image pull) live in
# frontend_service.tf (DT-Task 8).

resource "google_service_account" "frontend" {
  account_id   = "frontend-sa"
  display_name = "laborcase - frontend service"
  description  = "Runtime identity for the Next.js Cloud Run service. Holds only what the frontend needs to pull images and emit logs."
}

resource "google_artifact_registry_repository_iam_member" "frontend_reader" {
  repository = google_artifact_registry_repository.images.name
  location   = google_artifact_registry_repository.images.location
  role       = "roles/artifactregistry.reader"
  member     = "serviceAccount:${google_service_account.frontend.email}"
}
