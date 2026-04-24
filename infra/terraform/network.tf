# Dedicated VPC so Cloud Run services can egress through a pinned Cloud NAT IP
# that 법제처 Open API permits for our OC. The law.go.kr API ties allowed
# callers to pre-registered IP/도메인, so we need a stable egress address.

resource "google_compute_network" "laborcase" {
  name                    = "${local.resource_prefix}-vpc"
  auto_create_subnetworks = false
  routing_mode            = "REGIONAL"
}

resource "google_compute_subnetwork" "laborcase_primary" {
  name                     = "${local.resource_prefix}-subnet-${var.region}"
  ip_cidr_range            = var.network_cidr
  region                   = var.region
  network                  = google_compute_network.laborcase.id
  private_ip_google_access = true
}

resource "google_compute_router" "laborcase" {
  name    = "${local.resource_prefix}-router"
  region  = var.region
  network = google_compute_network.laborcase.id
}

resource "google_compute_address" "nat_egress" {
  name         = "${local.resource_prefix}-nat-egress"
  region       = var.region
  address_type = "EXTERNAL"
  network_tier = "PREMIUM"
}

resource "google_compute_router_nat" "laborcase" {
  name                               = "${local.resource_prefix}-nat"
  router                             = google_compute_router.laborcase.name
  region                             = var.region
  nat_ip_allocate_option             = "MANUAL_ONLY"
  nat_ips                            = [google_compute_address.nat_egress.self_link]
  source_subnetwork_ip_ranges_to_nat = "ALL_SUBNETWORKS_ALL_IP_RANGES"

  log_config {
    enable = true
    filter = "ERRORS_ONLY"
  }
}
