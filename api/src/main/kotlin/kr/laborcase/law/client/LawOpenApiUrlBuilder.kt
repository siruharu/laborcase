package kr.laborcase.law.client

import org.springframework.web.util.UriComponentsBuilder
import java.net.URI

/**
 * Builds the exact URLs the DRF Open API expects.
 *
 * Kept separate from the HTTP client so URL construction stays pure and easy to
 * unit-test. The client just passes through to RestClient.
 *
 * OC is attached to every URL so no caller has to remember it. `type=XML` is
 * also fixed — the documentation is more complete for XML than JSON, and we
 * want one canonical form to validate responses against
 * (docs/research/drf-schema-notes.md §응답 인코딩).
 */
class LawOpenApiUrlBuilder(
    private val baseUrl: String = "https://www.law.go.kr",
    private val oc: String,
) {

    init {
        require(oc.isNotBlank()) { "OC must be non-blank; inject from \${law.oc}" }
    }

    fun searchLaw(query: String, display: Int = 10, page: Int = 1): URI =
        UriComponentsBuilder.fromUriString("$baseUrl/DRF/lawSearch.do")
            .queryParam("OC", oc)
            .queryParam("target", "law")
            .queryParam("type", "XML")
            .queryParam("query", query)
            .queryParam("display", display)
            .queryParam("page", page)
            .build()
            .encode()
            .toUri()

    fun fetchLaw(lsId: String, effectiveYmd: String? = null): URI =
        UriComponentsBuilder.fromUriString("$baseUrl/DRF/lawService.do")
            .queryParam("OC", oc)
            .queryParam("target", "law")
            .queryParam("type", "XML")
            .queryParam("ID", lsId)
            .apply { effectiveYmd?.let { queryParam("efYd", it) } }
            .build()
            .encode()
            .toUri()

    fun fetchArticle(
        lsId: String,
        locator: ArticleLocator,
        effectiveYmd: String? = null,
    ): URI =
        UriComponentsBuilder.fromUriString("$baseUrl/DRF/lawService.do")
            .queryParam("OC", oc)
            .queryParam("target", "lawjosub")
            .queryParam("type", "XML")
            .queryParam("ID", lsId)
            .queryParam("JO", locator.joParam())
            .apply {
                locator.hangParam()?.let { queryParam("HANG", it) }
                locator.hoParam()?.let { queryParam("HO", it) }
                locator.mok?.let { queryParam("MOK", it) }
                effectiveYmd?.let { queryParam("efYd", it) }
            }
            .build()
            .encode()
            .toUri()

    /**
     * Search across revision history. Currently *not* available with our OC
     * approval (the "공동활용 법령종류" form has no 연혁 checkbox for us);
     * kept here so the URL shape is captured if/when access is granted.
     * See docs/research/drf-schema-notes.md §lsHistory 권한 누락.
     */
    fun historySearch(query: String, display: Int = 10): URI =
        UriComponentsBuilder.fromUriString("$baseUrl/DRF/lawSearch.do")
            .queryParam("OC", oc)
            .queryParam("target", "lsHistory")
            .queryParam("type", "XML")
            .queryParam("query", query)
            .queryParam("display", display)
            .build()
            .encode()
            .toUri()
}
