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
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.transaction.support.TransactionTemplate
import java.sql.DriverManager

/**
 * Walks the key DeltaSyncJob behaviours:
 *   - When nothing has changed since the last FullSync, DeltaSync is a
 *     no-op (versionsChanged=0, skipped=1) — the expected steady state.
 *   - When the mock upstream reports a new lsiSeq, DeltaSync picks it up
 *     the same way FullSync would (delegation correctness).
 *   - sync_log distinguishes delta-sync from full-sync entries.
 */
class DeltaSyncJobIntegrationTest {

    private val jdbcUrl = "jdbc:postgresql://localhost:54320/laborcase_test"
    private val user = "test"
    private val password = "test" // pragma: allowlist secret -- local-only test container

    private lateinit var wireMock: WireMockServer
    private lateinit var full: FullSyncJob
    private lateinit var delta: DeltaSyncJob
    private lateinit var jdbc: JdbcClient

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
        full = FullSyncJob(
            client = client,
            rawXmlStore = rawXmlStore,
            parser = LawXmlParser(),
            repo = LawSyncRepository(jdbc),
            syncLog = SyncLogRepository(jdbc),
            tx = tx,
            seed = seed,
        )
        delta = DeltaSyncJob(full)
    }

    @AfterEach
    fun tearDown() {
        wireMock.stop()
    }

    @Test
    fun `delta sync after full sync is a no-op and records job_name=delta-sync`() {
        stubSearch(lsiSeq = "265959")
        stubLawService()

        val initial = full.run()
        assertEquals(1, initial.versionsChanged)

        val delta = delta.run()
        assertEquals(0, delta.versionsChanged, "nothing changed upstream")
        assertEquals(1, delta.lawsSkippedIdempotent)

        val jobNames = jdbc.sql("SELECT job_name FROM sync_log ORDER BY started_at")
            .query(String::class.java).list()
        assertEquals(listOf("full-sync", "delta-sync"), jobNames)
    }

    @Test
    fun `delta sync picks up a new lsiSeq identically to full sync`() {
        stubSearch(lsiSeq = "265959")
        stubLawService()
        full.run()

        // Simulate 법령 개정: upstream now reports a new 법령일련번호.
        wireMock.resetAll()
        stubSearch(lsiSeq = "300001")
        stubLawService()

        val result = delta.run()
        assertEquals(1, result.versionsChanged, "delta must act on lsiSeq drift")

        // Prior version must be demoted; new version must be current.
        val currentRow = jdbc.sql(
            """
            SELECT lsi_seq FROM law_version
             WHERE law_id = (SELECT id FROM law WHERE ls_id = '001872')
               AND is_current = TRUE
            """.trimIndent(),
        ).query(String::class.java).single()
        assertEquals("300001", currentRow)

        val totalVersions = jdbc.sql(
            "SELECT COUNT(*) FROM law_version WHERE law_id = (SELECT id FROM law WHERE ls_id = '001872')",
        ).query(Int::class.java).single()
        assertEquals(2, totalVersions, "old version must be retained (for audit), just not current")
    }

    // ---- Helpers ----

    private fun stubSearch(lsiSeq: String) {
        val searchXml = fixture("lawSearch_geunrogijunbeop.xml")
            .replace("<법령일련번호>265959</법령일련번호>", "<법령일련번호>$lsiSeq</법령일련번호>")
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

    private fun stubLawService() {
        wireMock.stubFor(
            get(urlPathEqualTo("/DRF/lawService.do"))
                .withQueryParam("target", equalTo("law"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/xml; charset=utf-8")
                        .withBody(fixture("lawService_geunrogijunbeop_lsid.xml")),
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
