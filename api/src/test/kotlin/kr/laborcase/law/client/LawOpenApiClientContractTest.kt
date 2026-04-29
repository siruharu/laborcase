package kr.laborcase.law.client

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.containing
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

// Given/When/Then contract tests for LawOpenApiClient backed by WireMock.
// Fixtures are the real responses captured in Task 0 under
// api/src/test/resources/fixtures/drf; a schema drift in the upstream API
// shows up here immediately.
class LawOpenApiClientContractTest {

    private lateinit var server: WireMockServer
    private lateinit var client: LawOpenApiClient

    private fun fixture(name: String): String =
        requireNotNull(this::class.java.classLoader.getResourceAsStream("fixtures/drf/$name")) {
            "fixture fixtures/drf/$name not found on classpath"
        }.use { it.reader(Charsets.UTF_8).readText() }

    @BeforeEach
    fun start() {
        server = WireMockServer(WireMockConfiguration.options().dynamicPort())
        server.start()
        val urlBuilder = LawOpenApiUrlBuilder(
            baseUrl = "http://localhost:${server.port()}",
            oc = "TESTOC",
        )
        client = LawOpenApiClient(urlBuilder = urlBuilder, initialBackoffMillis = 5)
    }

    @AfterEach
    fun stop() {
        server.stop()
    }

    @Test
    fun `searchLaws - given query, when called, then URL has correct params and response parses to hits`() {
        // Given
        server.stubFor(
            get(urlPathEqualTo("/DRF/lawSearch.do"))
                .withQueryParam("OC", equalTo("TESTOC"))
                .withQueryParam("target", equalTo("law"))
                .withQueryParam("type", equalTo("XML"))
                .withQueryParam("query", containing("근로기준법"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/xml; charset=utf-8")
                        .withBody(fixture("lawSearch_geunrogijunbeop.xml")),
                ),
        )

        // When
        val hits = client.searchLaws("근로기준법", display = 3)

        // Then
        assertEquals(3, hits.size, "fixture has 3 law entries")
        val mainLaw = hits.first { it.nameKr == "근로기준법" }
        assertEquals("001872", mainLaw.lsId)
        assertEquals("265959", mainLaw.lsiSeq)
        assertTrue(mainLaw.isCurrent)
        assertEquals("고용노동부", mainLaw.department)
    }

    @Test
    fun `fetchLawByLsId - given lsId, when called, then ID param used and raw XML returned`() {
        // Given
        server.stubFor(
            get(urlPathEqualTo("/DRF/lawService.do"))
                .withQueryParam("OC", equalTo("TESTOC"))
                .withQueryParam("target", equalTo("law"))
                .withQueryParam("ID", equalTo("001872"))
                .withQueryParam("type", equalTo("XML"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/xml; charset=utf-8")
                        .withBody(fixture("lawService_geunrogijunbeop_lsid.xml")),
                ),
        )

        // When
        val response = client.fetchLawByLsId("001872")

        // Then
        assertTrue(response.xml.contains("<법령"), "response must contain <법령> root")
        assertTrue(response.xml.contains("<법령ID>001872</법령ID>"))
        assertEquals("001872", response.requestUri.query?.substringAfter("ID=")?.substringBefore("&"))
    }

    @Test
    fun `fetchArticle - given locator, when called, then JO is n_times_100 zero-padded and lawjosub endpoint is hit`() {
        // Given
        server.stubFor(
            get(urlPathEqualTo("/DRF/lawService.do"))
                .withQueryParam("OC", equalTo("TESTOC"))
                .withQueryParam("target", equalTo("lawjosub"))
                .withQueryParam("ID", equalTo("001872"))
                .withQueryParam("JO", equalTo("002300"))
                .withQueryParam("type", equalTo("XML"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/xml; charset=utf-8")
                        .withBody(fixture("lawjosub_geunrogijunbeop_article23.xml")),
                ),
        )

        // When
        val response = client.fetchArticle(
            lsId = "001872",
            locator = ArticleLocator(jo = 23),
        )

        // Then
        assertTrue(response.xml.contains("해고 등의 제한"))
        assertTrue(response.xml.contains("<조문번호>23</조문번호>"))
    }

    @Test
    fun `fetchHistory - when called, then fails explicitly with TODO message rather than hitting a 404`() {
        // Given — no WireMock stub; if the client actually called it we would get a MISSING stub error.
        // When / Then
        val ex = assertThrows(UnsupportedOperationException::class.java) {
            client.fetchHistory("근로기준법")
        }
        assertTrue(ex.message!!.contains("lsHistory"), "exception should explain the limitation")
        assertEquals(0, server.allServeEvents.size, "no HTTP call must be made")
    }

    @Test
    fun `transient server errors - given 503, when called, then retries up to maxAttempts and eventually succeeds`() {
        // Given — 2 × 503 then 200
        server.stubFor(
            get(urlPathEqualTo("/DRF/lawSearch.do"))
                .inScenario("retry")
                .whenScenarioStateIs("Started")
                .willSetStateTo("one")
                .willReturn(aResponse().withStatus(503)),
        )
        server.stubFor(
            get(urlPathEqualTo("/DRF/lawSearch.do"))
                .inScenario("retry")
                .whenScenarioStateIs("one")
                .willSetStateTo("two")
                .willReturn(aResponse().withStatus(503)),
        )
        server.stubFor(
            get(urlPathEqualTo("/DRF/lawSearch.do"))
                .inScenario("retry")
                .whenScenarioStateIs("two")
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/xml; charset=utf-8")
                        .withBody(fixture("lawSearch_geunrogijunbeop.xml")),
                ),
        )

        // When
        val hits = client.searchLaws("근로기준법")

        // Then
        assertTrue(hits.isNotEmpty())
        assertEquals(3, server.allServeEvents.count { it.request.url.contains("/DRF/lawSearch.do") })
    }

    @Test
    fun `permission-denied HTML is surfaced as LawOpenApiException`() {
        // Given — server happily returns 200 with the error HTML (real DRF behavior)
        server.stubFor(
            get(urlPathEqualTo("/DRF/lawSearch.do"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/html; charset=utf-8")
                        .withBody(fixture("lsHistory_error_permission_denied.xml")),
                ),
        )

        // When / Then
        assertThrows(LawOpenApiException::class.java) {
            client.searchLaws("test")
        }
    }

}
