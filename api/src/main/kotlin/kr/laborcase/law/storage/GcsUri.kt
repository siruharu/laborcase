package kr.laborcase.law.storage

/** Typed `gs://bucket/object` handle. */
data class GcsUri(val bucket: String, val objectName: String) {
    override fun toString(): String = "gs://$bucket/$objectName"
}
