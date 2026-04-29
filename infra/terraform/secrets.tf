# law-oc: the 법제처 OPEN API OC (email-id-style credential).
# Terraform manages the Secret resource; the **value** is seeded and rotated
# out-of-band via `gcloud secrets versions add` so OC never transits the
# Terraform state file or the repo. See docs/runbooks/secret-leak.md.

resource "google_secret_manager_secret" "law_oc" {
  secret_id = "law-oc"

  labels = local.labels

  replication {
    user_managed {
      replicas {
        location = var.region
      }
    }
  }
}

resource "google_secret_manager_secret_iam_member" "law_oc_sync_accessor" {
  secret_id = google_secret_manager_secret.law_oc.id
  role      = "roles/secretmanager.secretAccessor"
  member    = "serviceAccount:${google_service_account.law_sync.email}"
}

resource "google_secret_manager_secret_iam_member" "law_oc_api_accessor" {
  secret_id = google_secret_manager_secret.law_oc.id
  role      = "roles/secretmanager.secretAccessor"
  member    = "serviceAccount:${google_service_account.api.email}"
}
