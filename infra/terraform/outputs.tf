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
