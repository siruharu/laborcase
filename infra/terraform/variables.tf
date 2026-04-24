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
