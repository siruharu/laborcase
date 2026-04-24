package kr.laborcase.law.web

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Bean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@WebMvcTest(LawController::class)
@org.springframework.context.annotation.Import(LawControllerTest.Config::class)
class LawControllerTest {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var objectMapper: ObjectMapper

    @MockBean lateinit var repo: LawReadRepository
    @MockBean lateinit var freshness: SyncFreshnessService

    @TestConfiguration
    class Config {
        @Bean
        fun sourceMetaFactory(): SourceMetaFactory =
            SourceMetaFactory(clock = Clock.fixed(Instant.parse("2026-04-24T00:00:00Z"), ZoneOffset.UTC))
    }

    @org.junit.jupiter.api.BeforeEach
    fun stubFreshness() {
        whenever(freshness.current()).thenReturn(
            Freshness(
                lastSyncedAt = Instant.parse("2026-04-23T18:00:00Z"),
                stale = false,
                staleThresholdHours = 48,
            ),
        )
    }

    @Test
    fun `GET _laws returns list with KOGL-1 source meta and disclaimer`() {
        whenever(repo.listLaws()).thenReturn(listOf(SAMPLE_LAW_SUMMARY))

        mockMvc.perform(get("/api/v1/laws"))
            .andExpect(status().isOk)
            .andExpect(content().contentTypeCompatibleWith("application/json"))
            .andExpect(jsonPath("$.data[0].nameKr").value("근로기준법"))
            .andExpect(jsonPath("$.data[0].shortName").value("근기법"))
            .andExpect(jsonPath("$.source.license").value("KOGL-1"))
            .andExpect(jsonPath("$.source.provider").value("법제처 국가법령정보센터"))
            .andExpect(jsonPath("$.source.url").value("https://www.law.go.kr"))
            .andExpect(jsonPath("$.freshness.lastSyncedAt").value("2026-04-23T18:00:00Z"))
            .andExpect(jsonPath("$.freshness.stale").value(false))
            .andExpect(jsonPath("$.freshness.staleThresholdHours").value(48))
            .andExpect(jsonPath("$.disclaimer").value(ApiResponse.LEGAL_DISCLAIMER))
    }

    @Test
    fun `GET _laws _key _articles returns rows filtered by jo with law-version source url`() {
        whenever(repo.findLawByKey("근기법")).thenReturn(SAMPLE_LAW_SUMMARY)
        whenever(repo.findArticles(lsId = SAMPLE_LAW_SUMMARY.lsId, jo = 23)).thenReturn(
            listOf(
                ArticleDto(
                    jo = 23, joBranch = null, hang = null, ho = null, mok = null,
                    title = "해고 등의 제한",
                    body = "제23조(해고 등의 제한)",
                    effectiveDate = LocalDate.of(2025, 10, 23),
                ),
                ArticleDto(
                    jo = 23, joBranch = null, hang = 1, ho = null, mok = null,
                    title = null,
                    body = "① 사용자는 근로자에게 정당한 이유 없이 해고…",
                    effectiveDate = null,
                ),
            ),
        )

        mockMvc.perform(get("/api/v1/laws/근기법/articles").param("jo", "23"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.law.lsId").value("001872"))
            .andExpect(jsonPath("$.data.articles.length()").value(2))
            .andExpect(jsonPath("$.data.articles[0].title").value("해고 등의 제한"))
            .andExpect(jsonPath("$.data.articles[1].hang").value(1))
            .andExpect(jsonPath("$.source.url").value("https://www.law.go.kr/lsInfoP.do?lsiSeq=265959"))
            .andExpect(jsonPath("$.source.license").value("KOGL-1"))
            .andExpect(jsonPath("$.disclaimer").exists())
    }

    @Test
    fun `GET unknown law key returns 404`() {
        whenever(repo.findLawByKey("nope")).thenReturn(null)
        mockMvc.perform(get("/api/v1/laws/nope/articles"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `GET existing law but unknown jo returns 404`() {
        whenever(repo.findLawByKey("근기법")).thenReturn(SAMPLE_LAW_SUMMARY)
        whenever(repo.findArticles(lsId = SAMPLE_LAW_SUMMARY.lsId, jo = 9999)).thenReturn(emptyList())

        mockMvc.perform(get("/api/v1/laws/근기법/articles").param("jo", "9999"))
            .andExpect(status().isNotFound)
    }

    companion object {
        private val SAMPLE_LAW_SUMMARY = LawSummary(
            lsId = "001872",
            nameKr = "근로기준법",
            shortName = "근기법",
            lsiSeq = "265959",
            effectiveDate = LocalDate.of(2025, 10, 23),
            promulgationDate = LocalDate.of(2024, 10, 22),
            revisionType = "일부개정",
        )

        private fun get(path: String): org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder =
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(path)
    }
}
