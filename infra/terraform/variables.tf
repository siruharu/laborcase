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
  description = "Origin allowed by Spring Boot @CrossOrigin in production. Hardcoded to the current frontend Cloud Run URL — terraform 의 cross-resource ref (api → frontend) 가 cycle 을 만들기 때문 (frontend 도 api.uri 를 사용). frontend service 가 재생성되어 hash 가 바뀌면 이 default 를 update."
  type        = string
  default     = "https://laborcase-frontend-mxq42pqgaa-du.a.run.app"
}

variable "github_owner" {
  description = "GitHub user/org that owns the laborcase repos. Used by the Workload Identity Federation provider's attribute_condition so only this owner's workflows can mint tokens."
  type        = string
  default     = "siruharu"
}

variable "alert_email" {
  description = "Email address that receives Cloud Monitoring uptime alerts and Cloud Billing budget notifications. Single channel for MVP — Slack 도입 시 별도 channel 추가."
  type        = string
  default     = "siru.haru7419@gmail.com"
}
