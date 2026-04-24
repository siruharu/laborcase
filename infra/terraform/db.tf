# Cloud SQL for PostgreSQL 16 with pgvector. Private-IP only so the DB is
# unreachable from the public internet; Cloud Run services attach to the
# laborcase VPC for access.
#
# Private IP requires a reserved range + Service Networking peering. The
# peering is one-time and can take several minutes on first apply.

resource "google_compute_global_address" "private_ip_alloc" {
  name          = "${local.resource_prefix}-cloudsql-private-range"
  purpose       = "VPC_PEERING"
  address_type  = "INTERNAL"
  prefix_length = 16
  network       = google_compute_network.laborcase.id
}

resource "google_service_networking_connection" "cloudsql_peering" {
  network                 = google_compute_network.laborcase.id
  service                 = "servicenetworking.googleapis.com"
  reserved_peering_ranges = [google_compute_global_address.private_ip_alloc.name]
  deletion_policy         = "ABANDON"
}

resource "google_sql_database_instance" "laborcase" {
  name                = "${local.resource_prefix}-pg"
  database_version    = "POSTGRES_16"
  region              = var.region
  deletion_protection = true

  depends_on = [google_service_networking_connection.cloudsql_peering]

  settings {
    edition           = "ENTERPRISE"       # custom tiers (db-custom-*) belong to Enterprise; Plus requires predefined tiers
    tier              = "db-custom-1-3840" # 1 vCPU, 3.75 GB — MVP
    availability_type = "ZONAL"            # ZONAL for MVP, upgrade to REGIONAL later
    disk_type         = "PD_SSD"
    disk_size         = 10
    disk_autoresize   = true

    user_labels = local.labels

    ip_configuration {
      ipv4_enabled                                  = false
      private_network                               = google_compute_network.laborcase.id
      enable_private_path_for_google_cloud_services = true
    }

    backup_configuration {
      enabled                        = true
      start_time                     = "17:00" # 02:00 KST
      point_in_time_recovery_enabled = true
      backup_retention_settings {
        retained_backups = 7
        retention_unit   = "COUNT"
      }
    }

    maintenance_window {
      day          = 7 # Sunday
      hour         = 18
      update_track = "stable"
    }

    database_flags {
      name  = "cloudsql.iam_authentication"
      value = "on"
    }

    insights_config {
      query_insights_enabled  = true
      record_application_tags = true
      record_client_address   = false
    }
  }
}

resource "google_sql_database" "laborcase_app" {
  name     = "laborcase"
  instance = google_sql_database_instance.laborcase.name
}

# Random password for the app DB user. The value is stored in Secret Manager
# so Cloud Run can read it at runtime. Known trade-off: the password also
# sits in the Terraform state; state bucket has versioning + encryption +
# IAM-restricted access.
resource "random_password" "db_app_password" {
  length  = 32
  special = false
}

resource "google_sql_user" "laborcase_app" {
  name     = "laborcase_app"
  instance = google_sql_database_instance.laborcase.name
  password = random_password.db_app_password.result
}

resource "google_secret_manager_secret" "db_app_password" {
  secret_id = "db-app-password"
  labels    = local.labels

  replication {
    user_managed {
      replicas {
        location = var.region
      }
    }
  }
}

resource "google_secret_manager_secret_version" "db_app_password_current" {
  secret      = google_secret_manager_secret.db_app_password.id
  secret_data = random_password.db_app_password.result
}

resource "google_secret_manager_secret_iam_member" "db_password_api_accessor" {
  secret_id = google_secret_manager_secret.db_app_password.id
  role      = "roles/secretmanager.secretAccessor"
  member    = "serviceAccount:${google_service_account.api.email}"
}

resource "google_secret_manager_secret_iam_member" "db_password_sync_accessor" {
  secret_id = google_secret_manager_secret.db_app_password.id
  role      = "roles/secretmanager.secretAccessor"
  member    = "serviceAccount:${google_service_account.law_sync.email}"
}
