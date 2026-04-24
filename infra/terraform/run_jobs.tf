# Artifact Registry repo for laborcase container images + the two Cloud
# Run Jobs that run FullSync / DeltaSync inside the laborcase VPC so egress
# goes through the pinned NAT IP registered with 법제처.

resource "google_artifact_registry_repository" "images" {
  repository_id = "${local.resource_prefix}-images"
  location      = var.region
  format        = "DOCKER"
  description   = "laborcase backend container images"

  labels = local.labels
}

# Allow both service accounts to pull images at container-start time.
resource "google_artifact_registry_repository_iam_member" "sync_reader" {
  repository = google_artifact_registry_repository.images.name
  location   = google_artifact_registry_repository.images.location
  role       = "roles/artifactregistry.reader"
  member     = "serviceAccount:${google_service_account.law_sync.email}"
}

resource "google_artifact_registry_repository_iam_member" "api_reader" {
  repository = google_artifact_registry_repository.images.name
  location   = google_artifact_registry_repository.images.location
  role       = "roles/artifactregistry.reader"
  member     = "serviceAccount:${google_service_account.api.email}"
}

# Cloud SQL client role on the project for both SAs so they can resolve the
# instance via Cloud SQL Admin; the actual connection still goes through
# private IP inside the VPC.
resource "google_project_iam_member" "sync_cloudsql_client" {
  project = var.project_id
  role    = "roles/cloudsql.client"
  member  = "serviceAccount:${google_service_account.law_sync.email}"
}

locals {
  image_uri = "${var.region}-docker.pkg.dev/${var.project_id}/${google_artifact_registry_repository.images.repository_id}/api:latest"

  sync_env_common = {
    DATABASE_URL              = "jdbc:postgresql://${google_sql_database_instance.laborcase.private_ip_address}:5432/laborcase?sslmode=require"
    DATABASE_USER             = google_sql_user.laborcase_app.name
    LABORCASE_GCS_RAW_BUCKET  = google_storage_bucket.raw.name
    SPRING_FLYWAY_ENABLED     = "false"
    SENTRY_ENV                = "prod"
  }
}

# Sentry DSN — optional. Empty secret means the starter is a no-op and the
# jobs simply log to stderr. Seed the secret manually once you have a DSN:
#   echo "https://...@sentry.io/..." | gcloud secrets versions add sentry-dsn --data-file=-
resource "google_secret_manager_secret" "sentry_dsn" {
  secret_id = "sentry-dsn"
  labels    = local.labels

  replication {
    user_managed {
      replicas {
        location = var.region
      }
    }
  }
}

resource "google_secret_manager_secret_iam_member" "sentry_dsn_sync_accessor" {
  secret_id = google_secret_manager_secret.sentry_dsn.id
  role      = "roles/secretmanager.secretAccessor"
  member    = "serviceAccount:${google_service_account.law_sync.email}"
}

resource "google_secret_manager_secret_iam_member" "sentry_dsn_api_accessor" {
  secret_id = google_secret_manager_secret.sentry_dsn.id
  role      = "roles/secretmanager.secretAccessor"
  member    = "serviceAccount:${google_service_account.api.email}"
}

resource "google_cloud_run_v2_job" "law_full_sync" {
  name     = "law-full-sync"
  location = var.region

  template {
    task_count  = 1
    parallelism = 1

    template {
      service_account = google_service_account.law_sync.email
      max_retries     = 1
      timeout         = "1200s"

      vpc_access {
        network_interfaces {
          network    = google_compute_network.laborcase.id
          subnetwork = google_compute_subnetwork.laborcase_primary.id
        }
        egress = "ALL_TRAFFIC"
      }

      containers {
        image = local.image_uri

        env {
          name  = "JOB_MODE"
          value = "full"
        }

        dynamic "env" {
          for_each = local.sync_env_common
          content {
            name  = env.key
            value = env.value
          }
        }

        env {
          name = "LAW_OC"
          value_source {
            secret_key_ref {
              secret  = google_secret_manager_secret.law_oc.secret_id
              version = "latest"
            }
          }
        }

        env {
          name = "DATABASE_PASSWORD"
          value_source {
            secret_key_ref {
              secret  = google_secret_manager_secret.db_app_password.secret_id
              version = "latest"
            }
          }
        }

        resources {
          limits = {
            cpu    = "1"
            memory = "1Gi"
          }
        }
      }
    }
  }

  lifecycle {
    # Deploy-time image tag is set out-of-band by `gcloud run jobs update`
    # once each new image is pushed. Don't fight the drift here.
    ignore_changes = [template[0].template[0].containers[0].image]
  }

  depends_on = [
    google_project_iam_member.sync_cloudsql_client,
    google_secret_manager_secret_iam_member.law_oc_sync_accessor,
    google_secret_manager_secret_iam_member.db_password_sync_accessor,
    google_storage_bucket_iam_member.raw_sync_writer,
  ]
}

resource "google_cloud_run_v2_job" "law_delta_sync" {
  name     = "law-delta-sync"
  location = var.region

  template {
    task_count  = 1
    parallelism = 1

    template {
      service_account = google_service_account.law_sync.email
      max_retries     = 2
      timeout         = "600s"

      vpc_access {
        network_interfaces {
          network    = google_compute_network.laborcase.id
          subnetwork = google_compute_subnetwork.laborcase_primary.id
        }
        egress = "ALL_TRAFFIC"
      }

      containers {
        image = local.image_uri

        env {
          name  = "JOB_MODE"
          value = "delta"
        }

        dynamic "env" {
          for_each = local.sync_env_common
          content {
            name  = env.key
            value = env.value
          }
        }

        env {
          name = "LAW_OC"
          value_source {
            secret_key_ref {
              secret  = google_secret_manager_secret.law_oc.secret_id
              version = "latest"
            }
          }
        }

        env {
          name = "DATABASE_PASSWORD"
          value_source {
            secret_key_ref {
              secret  = google_secret_manager_secret.db_app_password.secret_id
              version = "latest"
            }
          }
        }

        resources {
          limits = {
            cpu    = "1"
            memory = "512Mi"
          }
        }
      }
    }
  }

  lifecycle {
    ignore_changes = [template[0].template[0].containers[0].image]
  }

  depends_on = [
    google_project_iam_member.sync_cloudsql_client,
    google_secret_manager_secret_iam_member.law_oc_sync_accessor,
    google_secret_manager_secret_iam_member.db_password_sync_accessor,
    google_storage_bucket_iam_member.raw_sync_writer,
  ]
}

# ------------------------------------------------------------------
# Cloud Scheduler: daily delta-sync trigger.
# Full-sync is intentionally un-scheduled — it's a one-shot initial-load
# tool run manually after schema changes or DB wipes.
# ------------------------------------------------------------------

resource "google_service_account" "scheduler" {
  account_id   = "scheduler-sa"
  display_name = "laborcase - scheduler invoker"
  description  = "Cloud Scheduler impersonation target for invoking Cloud Run Jobs."
}

resource "google_cloud_run_v2_job_iam_member" "scheduler_invoker_delta" {
  project  = google_cloud_run_v2_job.law_delta_sync.project
  location = google_cloud_run_v2_job.law_delta_sync.location
  name     = google_cloud_run_v2_job.law_delta_sync.name
  role     = "roles/run.invoker"
  member   = "serviceAccount:${google_service_account.scheduler.email}"
}

resource "google_cloud_scheduler_job" "delta_sync_daily" {
  name             = "law-delta-sync-daily"
  description      = "Daily 03:00 KST poll for 법령 개정"
  schedule         = "0 3 * * *"
  time_zone        = "Asia/Seoul"
  attempt_deadline = "600s"
  region           = var.region

  http_target {
    http_method = "POST"
    uri         = "https://${var.region}-run.googleapis.com/apis/run.googleapis.com/v1/namespaces/${var.project_id}/jobs/${google_cloud_run_v2_job.law_delta_sync.name}:run"

    oauth_token {
      service_account_email = google_service_account.scheduler.email
      scope                 = "https://www.googleapis.com/auth/cloud-platform"
    }
  }

  retry_config {
    retry_count          = 1
    max_retry_duration   = "300s"
    max_backoff_duration = "60s"
    min_backoff_duration = "10s"
  }
}
