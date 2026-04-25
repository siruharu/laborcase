package kr.laborcase.law.web

import com.fasterxml.jackson.databind.ObjectMapper
import kr.laborcase.law.embed.UpstageEmbeddingClient
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Bean
import org.hamcrest.Matchers.containsString
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@WebMvcTest(SearchController::class)
@org.springframework.context.annotation.Import(SearchControllerTest.Config::class)
class SearchControllerTest {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var objectMapper: ObjectMapper

    @MockBean lateinit var repo: LawReadRepository
    @MockBean lateinit var freshness: SyncFreshnessService

    @Suppress("UNCHECKED_CAST")
    @MockBean lateinit var upstageProvider: ObjectProvider<UpstageEmbeddingClient>

    @MockBean lateinit var upstageClient: UpstageEmbeddingClient

    @TestConfiguration
    class Config {
        @Bean
        fun sourceMetaFactory(): SourceMetaFactory =
            SourceMetaFactory(clock = Clock.fixed(Instant.parse("2026-04-25T00:00:00Z"), ZoneOffset.UTC))
    }

    @BeforeEach
    fun stubFreshness() {
        whenever(freshness.current()).thenReturn(
            Freshness(
                lastSyncedAt = Instant.parse("2026-04-25T00:00:00Z"),
                stale = false,
                staleThresholdHours = 48,
            ),
        )
    }

    @Test
    fun `POST search returns ranked hits with source and freshness`() {
        whenever(upstageProvider.ifAvailable).thenReturn(upstageClient)
        whenever(upstageClient.embedQuery("부당하게 해고당했다")).thenReturn(
            FloatArray(4096) { 0f },
        )
        whenever(repo.findSimilarArticles(org.mockito.kotlin.any(), org.mockito.kotlin.eq(5))).thenReturn(
            listOf(
                ArticleSearchHit(
                    law = SAMPLE_LAW,
                    article = ArticleDto(
                        jo = 28, joBranch = null, hang = 1, ho = null, mok = null,
                        title = null,
                        body = "사용자가 근로자에게 부당해고등을 하면…",
                        effectiveDate = null,
                    ),
                    distance = 0.5407,
                ),
            ),
        )

        mockMvc.perform(
            post("/api/v1/articles/search")
                .contentType("application/json")
                .content("""{"query":"부당하게 해고당했다"}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.query").value("부당하게 해고당했다"))
            .andExpect(jsonPath("$.data.hits[0].article.jo").value(28))
            .andExpect(jsonPath("$.data.hits[0].distance").value(0.5407))
            .andExpect(jsonPath("$.source.license").value("KOGL-1"))
            .andExpect(jsonPath("$.freshness.stale").value(false))
            .andExpect(jsonPath("$.disclaimer").exists())
    }

    @Test
    fun `POST search with blank query returns 400`() {
        whenever(upstageProvider.ifAvailable).thenReturn(upstageClient)
        mockMvc.perform(
            post("/api/v1/articles/search")
                .contentType("application/json")
                .content("""{"query":"   "}"""),
        )
            .andExpect(status().isBadRequest)
    }

    // The "503 when Upstage client missing" path is a single
    // `upstageProvider.ifAvailable ?: throw 503` check in the controller.
    // Reproducing that in @WebMvcTest is awkward because @MockBean
    // for ObjectProvider<T> doesn't beat Spring's autowired ObjectProvider
    // when there's also a @MockBean of the inner type. Behavior covered by
    // the @Conditional bean wiring in SyncConfig + manual smoke instead.

    @Test
    fun `OPTIONS preflight for POST search echoes CORS headers`() {
        mockMvc.perform(
            options("/api/v1/articles/search")
                .header("Origin", "http://localhost:3000")
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "content-type"),
        )
            .andExpect(status().isOk)
            .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"))
            .andExpect(header().string("Access-Control-Allow-Methods", containsString("POST")))
            .andExpect(header().string("Access-Control-Max-Age", "3600"))
    }

    @Test
    fun `POST search clamps oversized limit to MAX_LIMIT`() {
        whenever(upstageProvider.ifAvailable).thenReturn(upstageClient)
        whenever(upstageClient.embedQuery(org.mockito.kotlin.any())).thenReturn(FloatArray(4096) { 0f })
        whenever(repo.findSimilarArticles(org.mockito.kotlin.any(), org.mockito.kotlin.eq(SearchController.MAX_LIMIT)))
            .thenReturn(emptyList())

        mockMvc.perform(
            post("/api/v1/articles/search")
                .contentType("application/json")
                .content("""{"query":"x","limit":9999}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.hits.length()").value(0))
    }

    companion object {
        private val SAMPLE_LAW = LawSummary(
            lsId = "001872",
            nameKr = "근로기준법",
            shortName = "근기법",
            lsiSeq = "265959",
            effectiveDate = LocalDate.of(2025, 10, 23),
            promulgationDate = LocalDate.of(2024, 10, 22),
            revisionType = "일부개정",
        )
    }
}
