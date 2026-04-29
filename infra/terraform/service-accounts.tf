# Service accounts used by Cloud Run. Terraform only creates the identities;
# role bindings to individual resources (Secret Manager secret, Cloud SQL
# instance, GCS buckets) live next to those resources so ownership is local.

resource "google_service_account" "law_sync" {
  account_id   = "law-sync-sa"
  display_name = "laborcase - law sync batch jobs"
  description  = "Runs Cloud Run Jobs that fetch 법제처 법령 data and persist to Cloud SQL / GCS."
}

resource "google_service_account" "api" {
  account_id   = "api-sa"
  display_name = "laborcase - API service"
  description  = "Runs the Spring Boot API on Cloud Run and serves law/precedent queries."
}
