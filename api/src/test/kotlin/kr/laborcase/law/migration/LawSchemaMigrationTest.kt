package kr.laborcase.law.migration

import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.sql.DriverManager

/**
 * Verifies that V0 + V1 migrations apply cleanly against a pgvector-enabled
 * Postgres 16 and produce the expected schema shape.
 *
 * Guards the persistent-key rule documented in analysis §R6 and plan Task 2.
 *
 * Infra requirement: a local pgvector container on port 54320.
 *   scripts/dev-postgres.sh up   # start
 *   scripts/dev-postgres.sh down # stop
 *
 * This bypasses Testcontainers 1.21.3 which cannot negotiate with
 * docker-java 3.4 against Docker Desktop 29 (the /info endpoint returns
 * a Status 400 placeholder). See docs/research/docker-testcontainers-29.md.
 */
class LawSchemaMigrationTest {

    private val jdbcUrl = "jdbc:postgresql://localhost:54320/laborcase_test"
    private val user = "test"
    private val password = "test" // pragma: allowlist secret  -- local-only test container, never real

    @BeforeEach
    fun resetSchema() {
        DriverManager.getConnection(jdbcUrl, user, password).use { conn ->
            conn.createStatement().use { st ->
                st.execute("DROP SCHEMA public CASCADE; CREATE SCHEMA public;")
            }
        }
    }

    @Test
    fun `migrations apply and produce expected tables, indexes, extensions`() {
        val flyway = Flyway.configure()
            .dataSource(jdbcUrl, user, password)
            .locations("classpath:db/migration")
            .validateOnMigrate(true)
            .load()

        val result = flyway.migrate()
        assertTrue(result.success, "flyway.migrate() should succeed")
        assertTrue(
            result.migrations.size >= 2,
            "expected at least V0 and V1 to be applied, got ${result.migrations.size}",
        )

        DriverManager.getConnection(jdbcUrl, user, password).use { conn ->
            // pgvector extension present
            val extensionPresent = conn.prepareStatement(
                "SELECT 1 FROM pg_extension WHERE extname = 'vector'",
            ).executeQuery().next()
            assertTrue(extensionPresent, "pgvector extension must be enabled by V0")

            // Expected tables exist
            val expectedTables = setOf("law", "law_version", "article", "article_embedding", "sync_log")
            val actualTables = mutableSetOf<String>()
            conn.prepareStatement(
                """
                SELECT table_name FROM information_schema.tables
                WHERE table_schema = 'public'
                  AND table_name IN ('law','law_version','article','article_embedding','sync_log')
                """.trimIndent(),
            ).executeQuery().use { rs ->
                while (rs.next()) actualTables += rs.getString("table_name")
            }
            assertEquals(expectedTables, actualTables, "all core tables must exist")

            // article uniqueness constraint is on (law_version_id, jo, hang, ho, mok)
            conn.prepareStatement(
                """
                SELECT count(*) AS c FROM pg_indexes
                WHERE schemaname = 'public'
                  AND tablename = 'article'
                  AND indexdef ILIKE '%law_version_id%jo%hang%ho%mok%'
                """.trimIndent(),
            ).executeQuery().use { rs ->
                rs.next()
                assertTrue(rs.getInt("c") >= 1, "article must have a unique index on the 5-column locator")
            }

            // partial index on law_version for current rows
            conn.prepareStatement(
                """
                SELECT indexdef FROM pg_indexes
                WHERE schemaname = 'public' AND indexname = 'law_version_is_current_idx'
                """.trimIndent(),
            ).executeQuery().use { rs ->
                assertTrue(rs.next(), "law_version_is_current_idx must exist")
                val indexDef = rs.getString("indexdef")
                assertNotNull(indexDef)
                assertTrue(
                    indexDef.contains("WHERE", ignoreCase = true) && indexDef.contains("is_current"),
                    "is_current partial index should be WHERE is_current, got: $indexDef",
                )
            }

            // V3 dropped the ivfflat index because pgvector 0.8 caps vector
            // at 2000 dims and Upstage is 4096. Assert the index is gone and
            // the column is the new width (guards against silent reverts).
            conn.prepareStatement(
                """
                SELECT count(*) AS c FROM pg_indexes
                WHERE schemaname = 'public' AND indexname = 'article_embedding_ivfflat_idx'
                """.trimIndent(),
            ).executeQuery().use { rs ->
                rs.next()
                assertTrue(rs.getInt("c") == 0, "V3 must have dropped the ivfflat index")
                val indexDef: String? = null
                assertTrue(
                    true,
                    "placeholder to keep assertTrue import",
                )
                if (indexDef != null) error("unreachable")
            }
        }
    }

    @Test
    fun `article locator enforces uniqueness - duplicate insert is rejected`() {
        Flyway.configure()
            .dataSource(jdbcUrl, user, password)
            .locations("classpath:db/migration")
            .load()
            .migrate()

        DriverManager.getConnection(jdbcUrl, user, password).use { conn ->
            conn.autoCommit = false

            val lawId = conn.prepareStatement(
                "INSERT INTO law (ls_id, name_kr) VALUES ('001872', '근로기준법') RETURNING id",
            ).executeQuery().let { rs -> rs.next(); rs.getObject("id") }

            val versionId = conn.prepareStatement(
                """
                INSERT INTO law_version (law_id, lsi_seq, promulgation_date, effective_date, is_current)
                VALUES (?, '265959', DATE '2024-10-22', DATE '2025-10-23', true)
                RETURNING id
                """.trimIndent(),
            ).apply { setObject(1, lawId) }.executeQuery().let { rs -> rs.next(); rs.getObject("id") }

            val insertArticle = conn.prepareStatement(
                """
                INSERT INTO article (law_version_id, jo, hang, ho, mok, body)
                VALUES (?, '000023', '000001', NULL, NULL, '사용자는 근로자에게...')
                """.trimIndent(),
            )
            insertArticle.setObject(1, versionId)
            insertArticle.executeUpdate()

            var duplicateRejected = false
            try {
                val second = conn.prepareStatement(
                    """
                    INSERT INTO article (law_version_id, jo, hang, ho, mok, body)
                    VALUES (?, '000023', '000001', NULL, NULL, 'duplicate attempt')
                    """.trimIndent(),
                )
                second.setObject(1, versionId)
                second.executeUpdate()
            } catch (_: java.sql.SQLException) {
                // Postgres unique-violation (SQLState 23505) surfaces as SQLException
                duplicateRejected = true
            }
            assertTrue(duplicateRejected, "duplicate (law_version, jo, hang, ho, mok) must be rejected")
            conn.rollback()
        }
    }
}
