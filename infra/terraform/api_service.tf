# laborcase API HTTP service on Cloud Run v2.
#
# Topology decisions (`02_Analysis/2026-04-27_gcp-prod-deploy.md`):
#   * Direct VPC Egress (no Serverless VPC Access connector) so the service
#     can reach Cloud SQL via private IP and Upstage via the pinned NAT IP.
#   * Cloud SQL connection = JDBC to private IP. No Cloud SQL Auth Proxy
#     sidecar — DB password lives in Secret Manager.
#   * min_instance_count = 0 + startup CPU boost. Trigger to flip to 1:
#     P95 first-request latency > 5s OR cold-start ratio > 30%.
#   * Invoker = allUsers (laborcase is a public information tool — abuse is
#     mitigated by max_instance_count + budget alerts, not by IAM).
#
# Image lifecycle: Terraform plants the service shell with `:latest`, then
# CI (deploy-api.yml, plan Task 4) updates the image tag via
# `gcloud run services update`. We `ignore_changes = image` so terraform
# plan doesn't fight the deployed revision (same pattern as run_jobs.tf).

resource "google_cloud_run_v2_service" "api" {
  name     = "laborcase-api"
  location = var.region

  ingress             = "INGRESS_TRAFFIC_ALL"
  deletion_protection = false

  template {
    service_account = google_service_account.api.email

    scaling {
      min_instance_count = var.api_min_instances
      max_instance_count = var.api_max_instances
    }

    vpc_access {
      network_interfaces {
        network    = google_compute_network.laborcase.id
        subnetwork = google_compute_subnetwork.laborcase_primary.id
      }
      egress = "ALL_TRAFFIC"
    }

    containers {
      image = local.image_uri

      ports {
        container_port = 8080
      }

      resources {
        limits = {
          cpu    = "1"
          memory = "1Gi"
        }
        startup_cpu_boost = true
      }

      # Plain (non-secret) env. application.yml resolves these via ${VAR}.
      env {
        name  = "SPRING_PROFILES_ACTIVE"
        value = "prod"
      }

      env {
        name  = "DATABASE_URL"
        value = "jdbc:postgresql://${google_sql_database_instance.laborcase.private_ip_address}:5432/${google_sql_database.laborcase_app.name}?sslmode=require"
      }

      env {
        name  = "DATABASE_USER"
        value = google_sql_user.laborcase_app.name
      }

      env {
        name  = "CORS_ALLOWED_ORIGINS"
        value = var.frontend_allowed_origin
      }

      env {
        name  = "SENTRY_ENV"
        value = "prod"
      }

      # Sentry release is overwritten per-deploy by the GitHub Actions
      # workflow (`--update-env-vars SENTRY_RELEASE=$GITHUB_SHA`). Empty
      # default lets Spring Boot fall back to application.yml.
      env {
        name  = "SENTRY_RELEASE"
        value = ""
      }

      # Secret-backed env. Cloud Run v2 mounts the latest version into the
      # process environment at start time; the container itself never holds
      # an SDK call to fetch them.
      env {
        name = "DATABASE_PASSWORD"
        value_source {
          secret_key_ref {
            secret  = google_secret_manager_secret.db_app_password.secret_id
            version = "latest"
          }
        }
      }

      env {
        name = "UPSTAGE_API_KEY"
        value_source {
          secret_key_ref {
            secret  = google_secret_manager_secret.upstage_api_key.secret_id
            version = "latest"
          }
        }
      }

      # SyncConfig.lawOpenApiClient 가 prod profile 에서도 평가되므로
      # LAW_OC 가 비면 application context 가 fail. API 자체는 법제처 호출
      # 안 하지만 bean 생성 단계만 통과시키기 위해 sync job 과 동일하게 주입.
      env {
        name = "LAW_OC"
        value_source {
          secret_key_ref {
            secret  = google_secret_manager_secret.law_oc.secret_id
            version = "latest"
          }
        }
      }

      # SENTRY_DSN 은 secret 에 valid DSN 이 seed 된 후 add 한다. 빈/whitespace
      # DSN 은 Sentry SDK 가 URISyntaxException 으로 die → application context
      # 시작 실패 → startup probe 실패. application.yml 의 ${SENTRY_DSN:} 가
      # 빈 default 라 env 자체를 안 내려주면 starter 가 no-op 으로 시작한다.
      # Seed 절차:
      #   echo "https://...@sentry.io/..." | gcloud secrets versions add \
      #     sentry-dsn --data-file=- --project=laborcase-prod
      # 그 후 이 env block 을 복원하고 redeploy.

      # JVM cold-start can take several seconds; give startup probe enough
      # budget (failure_threshold * period_seconds = 150s) before Cloud Run
      # marks the revision unhealthy.
      startup_probe {
        initial_delay_seconds = 0
        timeout_seconds       = 5
        period_seconds        = 5
        failure_threshold     = 30
        http_get {
          path = "/actuator/health/readiness"
          port = 8080
        }
      }

      liveness_probe {
        timeout_seconds   = 5
        period_seconds    = 30
        failure_threshold = 3
        http_get {
          path = "/actuator/health/liveness"
          port = 8080
        }
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
      # SENTRY_RELEASE is set per-deploy by CI; ignore drift in the env list.
      client,
      client_version,
    ]
  }

  depends_on = [
    google_artifact_registry_repository_iam_member.api_reader,
    google_secret_manager_secret_iam_member.db_password_api_accessor,
    google_secret_manager_secret_iam_member.upstage_api_accessor,
    google_secret_manager_secret_iam_member.sentry_dsn_api_accessor,
  ]
}

# Public invoker. The application is an information-disclosure tool by
# design (CLAUDE.md §MVP). Abuse mitigation lives elsewhere
# (max_instance_count cap, budget alerts, Cloudflare follow-up if needed).
resource "google_cloud_run_v2_service_iam_member" "api_invoker_public" {
  project  = google_cloud_run_v2_service.api.project
  location = google_cloud_run_v2_service.api.location
  name     = google_cloud_run_v2_service.api.name
  role     = "roles/run.invoker"
  member   = "allUsers"
}

# project-level cloudsql.client for api-sa so it can resolve the Cloud SQL
# instance via Cloud SQL Admin (the actual JDBC connection still rides the
# private IP inside the VPC). Mirrors the law_sync grant in run_jobs.tf.
resource "google_project_iam_member" "api_cloudsql_client" {
  project = var.project_id
  role    = "roles/cloudsql.client"
  member  = "serviceAccount:${google_service_account.api.email}"
}
