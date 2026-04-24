package kr.laborcase.law.storage

import com.google.cloud.storage.BlobId
import com.google.cloud.storage.BlobInfo
import com.google.cloud.storage.Storage
import com.google.cloud.storage.StorageException

/**
 * Immutable archive for 법령 XML bodies. One object per (lsId, lsiSeq); once
 * written, attempts to rewrite the same key fail loudly so a drifted parser
 * or corrupted payload can never silently replace an already-audited copy.
 */
interface RawXmlStore {
    fun put(lsId: String, lsiSeq: String, xml: String): GcsUri
    fun exists(lsId: String, lsiSeq: String): Boolean
}

class GcsRawXmlStore(
    private val storage: Storage,
    private val bucket: String,
    private val pathPrefix: String = "law",
) : RawXmlStore {

    override fun put(lsId: String, lsiSeq: String, xml: String): GcsUri {
        require(lsId.isNotBlank()) { "lsId must be non-blank" }
        require(lsiSeq.isNotBlank()) { "lsiSeq must be non-blank" }

        val objectName = objectName(lsId, lsiSeq)
        val gsUri = GcsUri(bucket, objectName)

        // Defense in depth:
        //   (a) Explicit pre-check rejects duplicates fast and works with the
        //       in-memory test fake (which does not implement GCS preconditions).
        //   (b) doesNotExist() precondition on the create request prevents a
        //       race between two sync jobs running concurrently in prod — GCS
        //       enforces atomically with If-Generation-Match: 0.
        if (exists(lsId, lsiSeq)) {
            throw RawXmlAlreadyExists(
                "object already exists at $gsUri — refusing to overwrite",
            )
        }

        val info = BlobInfo.newBuilder(bucket, objectName)
            .setContentType("application/xml")
            .setContentEncoding("utf-8")
            .build()

        val bytes = xml.toByteArray(Charsets.UTF_8)
        try {
            storage.create(info, bytes, Storage.BlobTargetOption.doesNotExist())
        } catch (e: StorageException) {
            if (e.code == 412) {
                throw RawXmlAlreadyExists("object $gsUri was created concurrently — refusing to overwrite")
            }
            if (e.cause is UnsupportedOperationException) {
                // The in-memory test Storage does not implement preconditions.
                // The exists()-check above already prevented overwrites, so
                // re-create without the precondition.
                storage.create(info, bytes)
            } else {
                throw e
            }
        }
        return gsUri
    }

    override fun exists(lsId: String, lsiSeq: String): Boolean {
        val blobId = BlobId.of(bucket, objectName(lsId, lsiSeq))
        val blob = storage.get(blobId)
        return blob != null && blob.exists()
    }

    private fun objectName(lsId: String, lsiSeq: String): String =
        "$pathPrefix/$lsId/$lsiSeq.xml"
}

class RawXmlAlreadyExists(message: String) : RuntimeException(message)
