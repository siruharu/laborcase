package kr.laborcase.law.client

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LawOpenApiUrlBuilderTest {

    private val builder = LawOpenApiUrlBuilder(oc = "TESTOC")

    @Test
    fun `searchLaw URL carries OC, target, type and query`() {
        // Given
        // When
        val uri = builder.searchLaw("근로기준법").toString()

        // Then
        assertEquals("https", uri.substringBefore("://"))
        assertTrue(uri.contains("/DRF/lawSearch.do"))
        assertTrue(uri.contains("OC=TESTOC"))
        assertTrue(uri.contains("target=law"))
        assertTrue(uri.contains("type=XML"))
        assertTrue(uri.contains("query=%EA%B7%BC%EB%A1%9C%EA%B8%B0%EC%A4%80%EB%B2%95"))
    }

    @Test
    fun `fetchLaw URL uses ID and optional efYd`() {
        val without = builder.fetchLaw("001872").toString()
        assertTrue(without.contains("ID=001872"))
        assertTrue(!without.contains("efYd"))

        val withDate = builder.fetchLaw("001872", effectiveYmd = "20251023").toString()
        assertTrue(withDate.contains("efYd=20251023"))
    }

    @Test
    fun `fetchArticle converts ArticleLocator to n_times_100 zero-padded params`() {
        val uri = builder.fetchArticle(
            lsId = "001872",
            locator = ArticleLocator(jo = 23, hang = 1, ho = 2),
        ).toString()

        // 제23조 → JO=002300 (not 000023). See docs/research/drf-schema-notes.md
        assertTrue(uri.contains("JO=002300"), "unexpected JO encoding: $uri")
        assertTrue(uri.contains("HANG=000100"), "unexpected HANG encoding: $uri")
        assertTrue(uri.contains("HO=000200"), "unexpected HO encoding: $uri")
        assertTrue(uri.contains("target=lawjosub"))
    }

    @Test
    fun `fetchArticle with jo only omits hang_ho_mok params`() {
        val uri = builder.fetchArticle(lsId = "001872", locator = ArticleLocator(jo = 2)).toString()
        assertTrue(uri.contains("JO=000200"))
        assertTrue(!uri.contains("HANG="))
        assertTrue(!uri.contains("HO="))
        assertTrue(!uri.contains("MOK="))
    }

    @Test
    fun `locator rejects non-positive values`() {
        assertThrows(IllegalArgumentException::class.java) { ArticleLocator(jo = 0) }
        assertThrows(IllegalArgumentException::class.java) { ArticleLocator(jo = 1, hang = 0) }
        assertThrows(IllegalArgumentException::class.java) { ArticleLocator(jo = 1, mok = "") }
    }

    @Test
    fun `blank OC is rejected at construction time`() {
        assertThrows(IllegalArgumentException::class.java) { LawOpenApiUrlBuilder(oc = "  ") }
    }
}
