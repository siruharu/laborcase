package kr.laborcase.law.web

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
import java.sql.Timestamp
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

class SyncFreshnessServiceTest {

    private val jdbcUrl = "jdbc:postgresql://localhost:54320/laborcase_test"
    private val user = "test"
    private val password = "test" // pragma: allowlist secret -- local-only test container

    private lateinit var jdbc: JdbcClient

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
    }

    @Test
    fun `no sync_log rows - stale=true, lastSyncedAt=null`() {
        val svc = SyncFreshnessService(jdbc, Duration.ofHours(48), fixedClock("2026-04-24T00:00:00Z"))
        val f = svc.current()
        assertNull(f.lastSyncedAt)
        assertTrue(f.stale)
        assertEquals(48, f.staleThresholdHours)
    }

    @Test
    fun `recent SUCCESS within threshold - stale=false`() {
        insertSyncLog(status = "SUCCESS", finishedAt = Instant.parse("2026-04-23T12:00:00Z"))
        val svc = SyncFreshnessService(jdbc, Duration.ofHours(48), fixedClock("2026-04-24T00:00:00Z"))
        val f = svc.current()
        assertEquals(Instant.parse("2026-04-23T12:00:00Z"), f.lastSyncedAt)
        assertTrue(!f.stale)
    }

    @Test
    fun `old SUCCESS beyond threshold - stale=true`() {
        insertSyncLog(status = "SUCCESS", finishedAt = Instant.parse("2026-04-20T00:00:00Z"))
        val svc = SyncFreshnessService(jdbc, Duration.ofHours(48), fixedClock("2026-04-24T00:00:00Z"))
        val f = svc.current()
        assertEquals(Instant.parse("2026-04-20T00:00:00Z"), f.lastSyncedAt)
        assertTrue(f.stale, "4 days old exceeds 48h threshold")
    }

    @Test
    fun `only FAILED rows - still considered stale (no SUCCESS baseline)`() {
        insertSyncLog(status = "FAILED", finishedAt = Instant.parse("2026-04-23T23:00:00Z"))
        val svc = SyncFreshnessService(jdbc, Duration.ofHours(48), fixedClock("2026-04-24T00:00:00Z"))
        val f = svc.current()
        assertNull(f.lastSyncedAt, "FAILED rows do not count as a sync success")
        assertTrue(f.stale)
    }

    @Test
    fun `picks the most recent SUCCESS even when older ones exist`() {
        insertSyncLog(status = "SUCCESS", finishedAt = Instant.parse("2026-04-20T00:00:00Z"))
        insertSyncLog(status = "SUCCESS", finishedAt = Instant.parse("2026-04-23T20:00:00Z"))
        val svc = SyncFreshnessService(jdbc, Duration.ofHours(48), fixedClock("2026-04-24T00:00:00Z"))
        assertEquals(Instant.parse("2026-04-23T20:00:00Z"), svc.current().lastSyncedAt)
    }

    // ---- helpers ----

    private fun fixedClock(iso: String): Clock =
        Clock.fixed(Instant.parse(iso), ZoneOffset.UTC)

    private fun insertSyncLog(status: String, finishedAt: Instant) {
        jdbc.sql(
            """
            INSERT INTO sync_log (job_name, started_at, finished_at, status)
            VALUES ('test-sync', :started, :finished, :status)
            """.trimIndent(),
        )
            .param("started", Timestamp.from(finishedAt.minusSeconds(1)))
            .param("finished", Timestamp.from(finishedAt))
            .param("status", status)
            .update()
    }
}
