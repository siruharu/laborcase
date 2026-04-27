variable "project_id" {
  description = "GCP project ID that owns laborcase resources."
  type        = string
  default     = "laborcase-prod"
}

variable "region" {
  description = "Primary GCP region for compute, Cloud SQL, and Cloud Run."
  type        = string
  default     = "asia-northeast3"
}

variable "network_cidr" {
  description = "CIDR for the laborcase VPC subnet used by Cloud Run egress to the internet via Cloud NAT."
  type        = string
  default     = "10.10.0.0/20"
}

variable "api_min_instances" {
  description = "Minimum Cloud Run instances for the API service. 0 = pay nothing while idle but JVM cold-start affects first request. Bump to 1 once cold-start UX is unacceptable (analysis P1-c trigger)."
  type        = number
  default     = 0
}

variable "api_max_instances" {
  description = "Maximum Cloud Run instances for the API service. Caps cost spike from abuse / accidental load."
  type        = number
  default     = 5
}

variable "frontend_allowed_origin" {
  description = "Origin allowed by Spring Boot @CrossOrigin in production. Until the frontend Cloud Run service exists this stays as the dev default; flip to the real frontend URL in Task 11 (`gcloud run services update --update-env-vars CORS_ALLOWED_ORIGINS=...`)."
  type        = string
  default     = "http://localhost:3000"
}
