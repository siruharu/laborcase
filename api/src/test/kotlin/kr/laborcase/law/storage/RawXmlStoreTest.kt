package kr.laborcase.law.storage

import com.google.cloud.storage.contrib.nio.testing.LocalStorageHelper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Unit-tests GcsRawXmlStore against LocalStorageHelper, Google's official
 * in-memory Storage stub. No Docker, no network.
 *
 * Covers the three behaviours that Task 6 (FullSyncJob) will rely on:
 *   1. First put of a (lsId, lsiSeq) pair stores the XML at the canonical
 *      object path.
 *   2. Re-putting the same key fails with RawXmlAlreadyExists so drift
 *      cannot silently overwrite an audited copy.
 *   3. exists() distinguishes absent from present keys.
 */
class RawXmlStoreTest {

    private lateinit var store: GcsRawXmlStore

    @BeforeEach
    fun setUp() {
        val storage = LocalStorageHelper.getOptions().service
        store = GcsRawXmlStore(storage = storage, bucket = "test-raw")
    }

    @Test
    fun `put writes the XML to gs bucket law lsId lsiSeq xml`() {
        val uri = store.put(lsId = "001872", lsiSeq = "265959", xml = SAMPLE_XML)

        assertEquals("test-raw", uri.bucket)
        assertEquals("law/001872/265959.xml", uri.objectName)
        assertEquals("gs://test-raw/law/001872/265959.xml", uri.toString())
        assertTrue(store.exists("001872", "265959"))
    }

    @Test
    fun `exists returns false for keys that were never written`() {
        assertFalse(store.exists("001872", "265959"))
        store.put("001872", "265959", SAMPLE_XML)
        assertFalse(store.exists("001872", "999999"))
        assertFalse(store.exists("999999", "265959"))
    }

    @Test
    fun `second put of same key raises RawXmlAlreadyExists without overwriting`() {
        val first = store.put("001872", "265959", SAMPLE_XML)
        assertTrue(store.exists("001872", "265959"))

        val error = assertThrows(RawXmlAlreadyExists::class.java) {
            store.put("001872", "265959", "<diff><content>B</content></diff>")
        }
        assertTrue(
            error.message!!.contains(first.toString()),
            "error should name the conflicting URI, got: ${error.message}",
        )
    }

    @Test
    fun `put rejects blank lsId or lsiSeq`() {
        assertThrows(IllegalArgumentException::class.java) {
            store.put(lsId = "   ", lsiSeq = "265959", xml = SAMPLE_XML)
        }
        assertThrows(IllegalArgumentException::class.java) {
            store.put(lsId = "001872", lsiSeq = "", xml = SAMPLE_XML)
        }
    }

    @Test
    fun `different lsIds are stored in separate object paths`() {
        store.put("001872", "265959", SAMPLE_XML)
        store.put("000129", "218303", "<sample/>")

        assertTrue(store.exists("001872", "265959"))
        assertTrue(store.exists("000129", "218303"))
        assertFalse(store.exists("001872", "218303")) // cross-wise not present
    }

    companion object {
        private const val SAMPLE_XML =
            "<?xml version=\"1.0\" encoding=\"utf-8\"?><법령 법령키=\"0018722024102220520\"><기본정보>…</기본정보></법령>"
    }
}
