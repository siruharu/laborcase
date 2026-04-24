# Raw-XML archive bucket. Every successful law/article fetch lands here
# unchanged so legal audits can always be replayed from source. Versioning
# is on (extra safety against accidental overwrite in code) and the
# DoesNotExist precondition in GcsRawXmlStore enforces single-write
# semantics from the app side too.

resource "google_storage_bucket" "raw" {
  name                        = "${local.resource_prefix}-raw"
  location                    = var.region
  storage_class               = "STANDARD"
  public_access_prevention    = "enforced"
  uniform_bucket_level_access = true
  force_destroy               = false

  versioning {
    enabled = true
  }

  # Delete non-current versions after 365 days to cap cost while keeping
  # a full year of history available for audits.
  lifecycle_rule {
    action {
      type = "Delete"
    }
    condition {
      days_since_noncurrent_time = 365
      with_state                 = "ARCHIVED"
    }
  }

  labels = local.labels
}

# law-sync Cloud Run Job writes (but must not overwrite — controlled at app
# layer via DoesNotExist precondition). objectCreator role lets it create
# new blobs but not update existing ones.
resource "google_storage_bucket_iam_member" "raw_sync_writer" {
  bucket = google_storage_bucket.raw.name
  role   = "roles/storage.objectCreator"
  member = "serviceAccount:${google_service_account.law_sync.email}"
}

# API service reads for audit endpoints / request-time original XML.
resource "google_storage_bucket_iam_member" "raw_api_reader" {
  bucket = google_storage_bucket.raw.name
  role   = "roles/storage.objectViewer"
  member = "serviceAccount:${google_service_account.api.email}"
}
