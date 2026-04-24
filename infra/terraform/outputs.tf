output "nat_egress_ip" {
  description = "Static external IP used by Cloud NAT. Register this value with 법제처 (OPEN API 신청 수정) so OC calls from Cloud Run are permitted."
  value       = google_compute_address.nat_egress.address
}

output "vpc_name" {
  description = "Name of the laborcase VPC. Attach Cloud Run services/jobs here for egress via the pinned NAT IP."
  value       = google_compute_network.laborcase.name
}

output "subnet_name" {
  description = "Primary subnet name in the configured region."
  value       = google_compute_subnetwork.laborcase_primary.name
}

output "law_sync_sa_email" {
  description = "Service account that Cloud Run Jobs impersonate when calling law.go.kr and writing to Cloud SQL / GCS."
  value       = google_service_account.law_sync.email
}

output "api_sa_email" {
  description = "Service account that the Spring Boot API runs as."
  value       = google_service_account.api.email
}

output "law_oc_secret_id" {
  description = "Secret Manager secret holding the 법제처 OC. Value must be seeded via `gcloud secrets versions add law-oc --data-file=-`."
  value       = google_secret_manager_secret.law_oc.secret_id
}

output "cloudsql_instance_connection_name" {
  description = "Cloud SQL instance connection name (project:region:instance) used by the Cloud SQL JDBC socket factory."
  value       = google_sql_database_instance.laborcase.connection_name
}

output "cloudsql_private_ip" {
  description = "Private IP of the Cloud SQL instance, reachable from inside the laborcase VPC."
  value       = google_sql_database_instance.laborcase.private_ip_address
  sensitive   = true
}

output "db_app_password_secret_id" {
  description = "Secret Manager secret holding the laborcase_app DB password. Rotated via Terraform."
  value       = google_secret_manager_secret.db_app_password.secret_id
}

output "raw_bucket" {
  description = "GCS bucket holding immutable copies of every fetched 법령 XML (gs://laborcase-raw)."
  value       = google_storage_bucket.raw.name
}
