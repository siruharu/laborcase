# laborcase Frontend (Next.js 16 standalone) on Cloud Run v2.
#
# Topology (분석 §P2 / 사용자 답변 — `*.run.app` URL 베타 시작):
#   * Cloud Run + Dockerfile (standalone output) — Vercel 기각.
#   * Same Artifact Registry repo as the API; image name = `frontend`.
#   * No VPC egress required (frontend talks to the public API URL only —
#     서버사이드 RSC fetch 도 *.run.app 의 public endpoint 로 나간다).
#   * Invoker = allUsers. Same justification as the API.
#   * RAM 512 MiB — Next.js standalone 은 Java 보다 가벼움.
#
# Image lifecycle: terraform plants `:latest`, deploy-frontend.yml CI 가
# `gcloud run deploy --image ...:<sha>` 로 갱신. ignore_changes.

locals {
  frontend_image_uri = "${var.region}-docker.pkg.dev/${var.project_id}/${google_artifact_registry_repository.images.repository_id}/frontend:latest"
}

resource "google_cloud_run_v2_service" "frontend" {
  name     = "laborcase-frontend"
  location = var.region

  ingress             = "INGRESS_TRAFFIC_ALL"
  deletion_protection = false

  template {
    service_account = google_service_account.frontend.email

    scaling {
      min_instance_count = 0
      max_instance_count = 5
    }

    containers {
      image = local.frontend_image_uri

      ports {
        container_port = 3000
      }

      resources {
        limits = {
          cpu    = "1"
          memory = "512Mi"
        }
        startup_cpu_boost = true
      }

      # NEXT_PUBLIC_API_BASE_URL 은 빌드 시 inline 되지만, RSC server-side
      # fetch 가 시작 시 process.env 도 읽도록 안전망으로 runtime env 도 설정.
      # api_service.uri 는 terraform 이 자동 wire (cross-resource ref).
      env {
        name  = "NEXT_PUBLIC_API_BASE_URL"
        value = google_cloud_run_v2_service.api.uri
      }

      env {
        name  = "NODE_ENV"
        value = "production"
      }
    }
  }

  traffic {
    type    = "TRAFFIC_TARGET_ALLOCATION_TYPE_LATEST"
    percent = 100
  }

  lifecycle {
    ignore_changes = [
      template[0].containers[0].image,
      client,
      client_version,
    ]
  }

  depends_on = [
    google_artifact_registry_repository_iam_member.frontend_reader,
    google_cloud_run_v2_service.api,
  ]
}

# Public invoker (analysis §P1-e). 사용자가 brower 로 접근.
resource "google_cloud_run_v2_service_iam_member" "frontend_invoker_public" {
  project  = google_cloud_run_v2_service.frontend.project
  location = google_cloud_run_v2_service.frontend.location
  name     = google_cloud_run_v2_service.frontend.name
  role     = "roles/run.invoker"
  member   = "allUsers"
}
