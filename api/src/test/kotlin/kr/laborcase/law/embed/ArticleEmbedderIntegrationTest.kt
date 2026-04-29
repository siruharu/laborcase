package kr.laborcase.law.embed

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import kr.laborcase.law.sync.LawSyncRepository
import kr.laborcase.law.xml.ParsedArticle
import kr.laborcase.law.xml.ParsedLaw
import kr.laborcase.law.xml.ParsedLawVersion
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.jdbc.datasource.DriverManagerDataSource
import java.sql.DriverManager
import java.time.LocalDate

/**
 * End-to-end check for ArticleEmbedder.
 *
 * Wires:
 *   - local Postgres (pgvector) on 54320 — scripts/dev-postgres.sh up
 *   - WireMock as stand-in for Upstage
 *   - real LawSyncRepository to seed article rows
 *
 * Verifies: pending articles get embedded, article_embedding rows appear with
 * the 4096-dim vector, re-run skips already-embedded rows, and pgvector's
 * cosine distance can rank embedded articles.
 */
class ArticleEmbedderIntegrationTest {

    private val jdbcUrl = "jdbc:postgresql://localhost:54320/laborcase_test"
    private val user = "test"
    private val password = "test" // pragma: allowlist secret -- local-only test container

    private lateinit var wireMock: WireMockServer
    private lateinit var jdbc: JdbcClient
    private lateinit var embedder: ArticleEmbedder

    @BeforeEach
    fun setUp() {
        DriverManager.getConnection(jdbcUrl, user, password).use { conn ->
            conn.createStatement().use { it.execute("DROP SCHEMA public CASCADE; CREATE SCHEMA public;") }
        }
        Flyway.configure().dataSource(jdbcUrl, user, password)
            .locations("classpath:db/migration").load().migrate()

        val ds = DriverManagerDataSource(jdbcUrl, user, password).apply {
            setDriverClassName("org.postgresql.Driver")
        }
        jdbc = JdbcClient.create(ds)

        wireMock = WireMockServer(WireMockConfiguration.options().dynamicPort())
        wireMock.start()

        val client = UpstageEmbeddingClient(
            apiKey = "test-key", // pragma: allowlist secret
            baseUrl = "http://localhost:${wireMock.port()}/v1",
            initialBackoffMillis = 5,
        )
        embedder = ArticleEmbedder(jdbc, client, batchSize = 10)

        seedThreeArticles()
    }

    @AfterEach
    fun tearDown() {
        wireMock.stop()
    }

    @Test
    fun `embedPending inserts one row per article with 4096-dim vector`() {
        stubEmbeddingResponse(dim = 4096)

        val result = embedder.embedPending()

        assertEquals(3, result.embedded)
        assertEquals(0, result.failed)

        val stored = jdbc.sql("SELECT COUNT(*) FROM article_embedding").query(Int::class.java).single()
        assertEquals(3, stored)

        val dims = jdbc.sql(
            "SELECT vector_dims(vector) AS d FROM article_embedding LIMIT 1",
        ).query(Int::class.java).single()
        assertEquals(4096, dims)

        val model = jdbc.sql("SELECT DISTINCT model FROM article_embedding").query(String::class.java).single()
        assertEquals(UpstageEmbeddingClient.MODEL_PASSAGE, model)
    }

    @Test
    fun `rerun is idempotent - pending query excludes already-embedded rows`() {
        stubEmbeddingResponse(dim = 4096)

        embedder.embedPending()
        val afterFirst = jdbc.sql("SELECT COUNT(*) FROM article_embedding").query(Int::class.java).single()
        assertEquals(3, afterFirst)

        wireMock.resetAll()
        stubEmbeddingResponse(dim = 4096)

        val second = embedder.embedPending()
        assertEquals(0, second.embedded, "no new rows should be embedded")
        assertEquals(
            3,
            jdbc.sql("SELECT COUNT(*) FROM article_embedding").query(Int::class.java).single(),
        )
    }

    @Test
    fun `cosine operator works on stored 4096-dim vectors`() {
        // Stores deterministic vectors then asks Postgres to compute cosine
        // distance between one stored row and itself (expected 0) vs two
        // different rows (expected > 0). We don't assemble a 4096-char
        // literal in SQL — using stored vectors sidesteps the parser quirks.
        wireMock.stubFor(
            post(urlPathEqualTo("/v1/embeddings"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                            """
                            {"model":"${UpstageEmbeddingClient.MODEL_PASSAGE}",
                             "data":[
                               ${vec(index = 0, dim = 4096, strongAtIndex = 0)},
                               ${vec(index = 1, dim = 4096, strongAtIndex = 1)},
                               ${vec(index = 2, dim = 4096, strongAtIndex = 2)}
                             ]}
                            """.trimIndent(),
                        ),
                ),
        )
        embedder.embedPending()

        val rows = jdbc.sql(
            """
            WITH probe AS (
              SELECT ae.vector FROM article_embedding ae
              JOIN article a ON a.id = ae.article_id
              WHERE a.jo = '000001' LIMIT 1
            )
            SELECT a.jo, (ae.vector <=> (SELECT vector FROM probe)) AS d
              FROM article_embedding ae
              JOIN article a ON a.id = ae.article_id
             ORDER BY d
            """.trimIndent(),
        ).query { rs, _ -> rs.getString("jo") to rs.getDouble("d") }.list()

        assertEquals(3, rows.size)
        assertEquals("000001", rows[0].first, "closest match must be the probe article itself")
        assertTrue(rows[0].second < 0.001, "self-distance should be ≈0, got ${rows[0].second}")
        assertTrue(rows[1].second > 0.5, "different article should be distant")
    }

    // ---- helpers ----

    private fun seedThreeArticles() {
        val syncRepo = LawSyncRepository(jdbc)
        val lawId = syncRepo.upsertLaw(
            ParsedLaw(lsId = "001872", nameKr = "근로기준법", shortName = "근기법", department = null),
        )
        val versionId = syncRepo.insertLawVersion(
            lawId = lawId,
            parsed = ParsedLawVersion(
                lsiSeq = "265959",
                promulgationDate = LocalDate.of(2024, 10, 22),
                promulgationNo = "20520",
                effectiveDate = LocalDate.of(2025, 10, 23),
                revisionType = "일부개정",
            ),
            rawXmlGcsUri = "gs://laborcase-raw/law/001872/265959.xml",
            isCurrent = true,
        )
        syncRepo.insertArticles(
            versionId,
            listOf(
                ParsedArticle(jo = "000001", title = "목적", body = "이 법은..."),
                ParsedArticle(jo = "000002", title = "정의", body = "이 법에서 근로자란..."),
                ParsedArticle(jo = "000023", title = "해고 등의 제한", body = "사용자는 근로자에게 정당한 이유 없이 해고..."),
            ),
        )
    }

    private fun stubEmbeddingResponse(dim: Int) {
        wireMock.stubFor(
            post(urlPathEqualTo("/v1/embeddings"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withTransformers("response-template")
                        .withBody(anyDimensionResponse(dim)),
                ),
        )
    }

    private fun anyDimensionResponse(dim: Int): String {
        // Return three zero-vectors of the requested dim. Simple and matches
        // any seed-article count <= 3.
        val zeros = (0 until dim).joinToString(",") { "0.0" }
        return """
        {
          "model": "${UpstageEmbeddingClient.MODEL_PASSAGE}",
          "data": [
            {"index": 0, "embedding": [$zeros]},
            {"index": 1, "embedding": [$zeros]},
            {"index": 2, "embedding": [$zeros]}
          ]
        }
        """.trimIndent()
    }

    private fun vec(index: Int, dim: Int, strongAtIndex: Int): String {
        val values = (0 until dim).joinToString(",") { i -> if (i == strongAtIndex) "1.0" else "0.0" }
        return """{"index": $index, "embedding": [$values]}"""
    }
}
