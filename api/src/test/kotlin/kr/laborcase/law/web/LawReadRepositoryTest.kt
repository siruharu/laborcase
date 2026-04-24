package kr.laborcase.law.web

import kr.laborcase.law.sync.LawSyncRepository
import kr.laborcase.law.xml.ParsedArticle
import kr.laborcase.law.xml.ParsedLaw
import kr.laborcase.law.xml.ParsedLawVersion
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.jdbc.datasource.DriverManagerDataSource
import java.sql.DriverManager
import java.time.LocalDate

/**
 * Exercises LawReadRepository against the local dev-postgres container so
 * the SQL (column name quoting, JOIN shape, zero-pad filters) is verified
 * against a real Postgres engine, not a mock.
 */
class LawReadRepositoryTest {

    private val jdbcUrl = "jdbc:postgresql://localhost:54320/laborcase_test"
    private val user = "test"
    private val password = "test" // pragma: allowlist secret -- local-only test container

    private lateinit var repo: LawReadRepository
    private lateinit var syncRepo: LawSyncRepository

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
        val jdbc = JdbcClient.create(ds)
        repo = LawReadRepository(jdbc)
        syncRepo = LawSyncRepository(jdbc)

        seedOneLaw()
    }

    @Test
    fun `listLaws returns rows joined to is_current version`() {
        val laws = repo.listLaws()
        assertEquals(1, laws.size)
        val l = laws.first()
        assertEquals("001872", l.lsId)
        assertEquals("근로기준법", l.nameKr)
        assertEquals("근기법", l.shortName)
        assertEquals("265959", l.lsiSeq)
        assertEquals(LocalDate.of(2025, 10, 23), l.effectiveDate)
    }

    @Test
    fun `findLawByKey matches short_name, name_kr, and ls_id`() {
        assertNotNull(repo.findLawByKey("근기법"))
        assertNotNull(repo.findLawByKey("근로기준법"))
        assertNotNull(repo.findLawByKey("001872"))
        assertNull(repo.findLawByKey("nonsense"))
    }

    @Test
    fun `findArticles with jo filter returns only that 조 and nested levels`() {
        val rows = repo.findArticles(lsId = "001872", jo = 23)
        assertEquals(3, rows.size, "제23조 header + 1 항 + 1 호 = 3 rows in our fixture")
        assertTrue(rows.any { it.hang == null && it.ho == null }, "header row present")
        assertTrue(rows.any { it.hang == 1 && it.ho == null }, "항 1 row present")
        assertTrue(rows.any { it.hang == 1 && it.ho == 2 }, "호 2 row present")
    }

    @Test
    fun `findArticles with no filter returns all rows`() {
        val rows = repo.findArticles(lsId = "001872")
        assertEquals(3, rows.size)
    }

    @Test
    fun `findArticles ordering - jo asc then hang asc then ho asc`() {
        val rows = repo.findArticles(lsId = "001872")
        val pairs = rows.map { Triple(it.jo, it.hang ?: 0, it.ho ?: 0) }
        assertEquals(pairs.sortedBy { "${it.first}-${it.second}-${it.third}" }, pairs)
    }

    // ------------------------------------------------------------------

    private fun seedOneLaw() {
        val lawId = syncRepo.upsertLaw(
            ParsedLaw(
                lsId = "001872",
                nameKr = "근로기준법",
                shortName = "근기법",
                department = "고용노동부",
            ),
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
                ParsedArticle(
                    jo = "000023", title = "해고 등의 제한", body = "제23조(해고 등의 제한)",
                    effectiveDate = LocalDate.of(2025, 10, 23),
                ),
                ParsedArticle(
                    jo = "000023", hang = "000001",
                    body = "① 사용자는 근로자에게 정당한 이유 없이 해고…",
                ),
                ParsedArticle(
                    jo = "000023", hang = "000001", ho = "000002",
                    body = "2. 근로자가 업무상 부상…",
                ),
            ),
        )
    }
}
