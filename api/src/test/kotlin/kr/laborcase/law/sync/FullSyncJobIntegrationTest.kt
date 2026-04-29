package kr.laborcase.law.sync

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.google.cloud.storage.contrib.nio.testing.LocalStorageHelper
import kr.laborcase.law.client.LawOpenApiClient
import kr.laborcase.law.client.LawOpenApiUrlBuilder
import kr.laborcase.law.storage.GcsRawXmlStore
import kr.laborcase.law.xml.LawXmlParser
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.transaction.support.TransactionTemplate
import java.sql.DriverManager
import java.util.UUID

/**
 * End-to-end check for FullSyncJob.
 *
 * Wires:
 *   - local Postgres on 54320 (scripts/dev-postgres.sh up)
 *   - WireMock as a stand-in for 법제처 DRF, seeded with the Task 0 fixtures
 *   - LocalStorageHelper as an in-memory GCS
 *   - the real LawXmlParser + LawSyncRepository + SyncLogRepository
 *
 * Confirms the three behaviours plan-§Task 6 calls out:
 *   - one full pass imports a law end-to-end (law + law_version + articles
 *     + GCS object + sync_log SUCCESS).
 *   - running the pass a second time with the same lsiSeq is a no-op
 *     (idempotent).
 *   - running with a NEW lsiSeq demotes is_current on the prior version
 *     and inserts the new one as current.
 */
class FullSyncJobIntegrationTest {

    private val jdbcUrl = "jdbc:postgresql://localhost:54320/laborcase_test"
    private val user = "test"
    private val password = "test" // pragma: allowlist secret -- local-only test container

    private lateinit var wireMock: WireMockServer
    private lateinit var job: FullSyncJob
    private lateinit var jdbc: JdbcClient
    private lateinit var repo: LawSyncRepository
    private lateinit var syncLog: SyncLogRepository

    private fun fixture(name: String): String =
        requireNotNull(this::class.java.classLoader.getResourceAsStream("fixtures/drf/$name")) {
            "fixture fixtures/drf/$name not found"
        }.use { it.reader(Charsets.UTF_8).readText() }

    @BeforeEach
    fun setUp() {
        resetSchemaAndMigrate()

        wireMock = WireMockServer(WireMockConfiguration.options().dynamicPort())
        wireMock.start()

        val dataSource = DriverManagerDataSource(jdbcUrl, user, password).apply {
            setDriverClassName("org.postgresql.Driver")
        }
        jdbc = JdbcClient.create(dataSource)
        repo = LawSyncRepository(jdbc)
        syncLog = SyncLogRepository(jdbc)

        val client = LawOpenApiClient(
            urlBuilder = LawOpenApiUrlBuilder(
                baseUrl = "http://localhost:${wireMock.port()}",
                oc = "TESTOC",
            ),
            initialBackoffMillis = 5,
        )
        val rawXmlStore = GcsRawXmlStore(
            storage = LocalStorageHelper.getOptions().service,
            bucket = "test-raw",
        )
        val tx = TransactionTemplate(DataSourceTransactionManager(dataSource))
        val seed = LawSeed(
            laws = listOf(
                LawSeedEntry(
                    shortName = "근기법",
                    lsId = "001872",
                    searchQuery = "근로기준법",
                ),
            ),
        )
        job = FullSyncJob(client, rawXmlStore, LawXmlParser(), repo, syncLog, tx, seed)
    }

    @AfterEach
    fun tearDown() {
        wireMock.stop()
    }

    @Test
    fun `first run imports the law end-to-end`() {
        stubSearch(lsiSeq = "265959")
        stubLawService(fixture = "lawService_geunrogijunbeop_lsid.xml")

        val result = job.run()

        assertEquals(1, result.versionsChanged)
        assertEquals(0, result.lawsSkippedIdempotent)
        assertEquals(0, result.lawsFailed)

        val lawRow = jdbc.sql("SELECT id, ls_id, name_kr, short_name FROM law WHERE ls_id = '001872'")
            .query { rs, _ ->
                mapOf(
                    "id" to rs.getObject("id", UUID::class.java),
                    "lsId" to rs.getString("ls_id"),
                    "nameKr" to rs.getString("name_kr"),
                    "shortName" to rs.getString("short_name"),
                )
            }
            .single()
        assertEquals("001872", lawRow["lsId"])
        assertEquals("근로기준법", lawRow["nameKr"]) // parser-derived, not the seed placeholder
        val lawId = lawRow["id"] as UUID

        val versionId = repo.findLawVersionId(lawId, "265959")
        assertNotNull(versionId)
        val isCurrent = jdbc.sql("SELECT is_current FROM law_version WHERE id = :id")
            .param("id", versionId!!)
            .query(Boolean::class.java).single()
        assertTrue(isCurrent, "imported version must be is_current=true")

        val articleCount = repo.countArticlesForVersion(versionId)
        assertTrue(articleCount > 100, "expected >100 articles, got $articleCount")

        val latestLog = jdbc.sql(
            "SELECT status, versions_changed FROM sync_log WHERE job_name = 'full-sync' ORDER BY started_at DESC LIMIT 1",
        ).query { rs, _ -> rs.getString("status") to rs.getInt("versions_changed") }.single()
        assertEquals("SUCCESS", latestLog.first)
        assertEquals(1, latestLog.second)
    }

    @Test
    fun `second run with same lsiSeq is idempotent - no new version or articles`() {
        stubSearch(lsiSeq = "265959")
        stubLawService(fixture = "lawService_geunrogijunbeop_lsid.xml")

        val firstRun = job.run()
        assertEquals(1, firstRun.versionsChanged)
        val firstArticleCount = jdbc.sql("SELECT COUNT(*) FROM article").query(Int::class.java).single()
        val firstVersionCount = jdbc.sql("SELECT COUNT(*) FROM law_version").query(Int::class.java).single()

        val secondRun = job.run()
        assertEquals(0, secondRun.versionsChanged)
        assertEquals(1, secondRun.lawsSkippedIdempotent)

        assertEquals(
            firstArticleCount,
            jdbc.sql("SELECT COUNT(*) FROM article").query(Int::class.java).single(),
            "idempotent second run must not insert additional article rows",
        )
        assertEquals(
            firstVersionCount,
            jdbc.sql("SELECT COUNT(*) FROM law_version").query(Int::class.java).single(),
            "idempotent second run must not insert additional version rows",
        )
    }

    @Test
    fun `new lsiSeq demotes prior is_current and inserts new version`() {
        stubSearch(lsiSeq = "265959")
        stubLawService(fixture = "lawService_geunrogijunbeop_lsid.xml")
        job.run()

        // Simulate an 개정: search now returns a different lsiSeq.
        wireMock.resetAll()
        stubSearch(lsiSeq = "999999")
        stubLawService(fixture = "lawService_geunrogijunbeop_lsid.xml")
        val secondRun = job.run()
        assertEquals(1, secondRun.versionsChanged)

        val priorCurrent = jdbc.sql(
            """
            SELECT is_current FROM law_version
             WHERE law_id = (SELECT id FROM law WHERE ls_id = '001872')
               AND lsi_seq = '265959'
            """.trimIndent(),
        ).query(Boolean::class.java).single()
        assertEquals(false, priorCurrent, "prior version must be demoted")

        val newCurrent = jdbc.sql(
            """
            SELECT is_current FROM law_version
             WHERE law_id = (SELECT id FROM law WHERE ls_id = '001872')
               AND lsi_seq = '999999'
            """.trimIndent(),
        ).query(Boolean::class.java).single()
        assertEquals(true, newCurrent, "new version must be is_current=true")
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private fun stubSearch(lsiSeq: String) {
        val searchXml = fixture("lawSearch_geunrogijunbeop.xml").replace("<법령일련번호>265959</법령일련번호>", "<법령일련번호>$lsiSeq</법령일련번호>")
        wireMock.stubFor(
            get(urlPathEqualTo("/DRF/lawSearch.do"))
                .withQueryParam("OC", equalTo("TESTOC"))
                .withQueryParam("target", equalTo("law"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/xml; charset=utf-8")
                        .withBody(searchXml),
                ),
        )
    }

    private fun stubLawService(fixture: String) {
        wireMock.stubFor(
            get(urlPathEqualTo("/DRF/lawService.do"))
                .withQueryParam("target", equalTo("law"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/xml; charset=utf-8")
                        .withBody(fixture(fixture)),
                ),
        )
    }

    private fun resetSchemaAndMigrate() {
        DriverManager.getConnection(jdbcUrl, user, password).use { conn ->
            conn.createStatement().use { it.execute("DROP SCHEMA public CASCADE; CREATE SCHEMA public;") }
        }
        Flyway.configure()
            .dataSource(jdbcUrl, user, password)
            .locations("classpath:db/migration")
            .load()
            .migrate()
    }
}
