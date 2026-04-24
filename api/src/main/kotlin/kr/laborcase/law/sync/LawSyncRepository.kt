package kr.laborcase.law.sync

import kr.laborcase.law.xml.ParsedArticle
import kr.laborcase.law.xml.ParsedLaw
import kr.laborcase.law.xml.ParsedLawVersion
import org.springframework.jdbc.core.simple.JdbcClient
import java.util.UUID

/**
 * Persistence layer for the law sync flow.
 *
 * Uses JdbcClient (Spring 6.1+) so callers get fluent SQL without the JPA
 * lifecycle baggage — each method maps to one concrete statement and the
 * reader can trace the SQL without chasing entity annotations.
 */
class LawSyncRepository(private val jdbc: JdbcClient) {

    /** Upsert by ls_id. Returns the (possibly pre-existing) law.id. */
    fun upsertLaw(parsed: ParsedLaw): UUID {
        return jdbc.sql(
            """
            INSERT INTO law (ls_id, name_kr, short_name)
            VALUES (:lsId, :nameKr, :shortName)
            ON CONFLICT (ls_id) DO UPDATE
                SET name_kr = EXCLUDED.name_kr,
                    short_name = EXCLUDED.short_name
            RETURNING id
            """.trimIndent(),
        )
            .param("lsId", parsed.lsId)
            .param("nameKr", parsed.nameKr)
            .param("shortName", parsed.shortName)
            .query(UUID::class.java).single()
    }

    fun findLawVersionId(lawId: UUID, lsiSeq: String): UUID? {
        return jdbc.sql("SELECT id FROM law_version WHERE law_id = :lawId AND lsi_seq = :lsiSeq")
            .param("lawId", lawId)
            .param("lsiSeq", lsiSeq)
            .query(UUID::class.java).optional().orElse(null)
    }

    /** Called before inserting a new current version so only one is_current=true per law. */
    fun demoteCurrentVersions(lawId: UUID): Int {
        return jdbc.sql("UPDATE law_version SET is_current = FALSE WHERE law_id = :lawId AND is_current = TRUE")
            .param("lawId", lawId)
            .update()
    }

    fun insertLawVersion(
        lawId: UUID,
        parsed: ParsedLawVersion,
        rawXmlGcsUri: String,
        isCurrent: Boolean,
    ): UUID {
        return jdbc.sql(
            """
            INSERT INTO law_version
                (law_id, lsi_seq, promulgation_date, promulgation_no, effective_date,
                 revision_type, is_current, raw_xml_gcs_uri)
            VALUES (:lawId, :lsiSeq, :promulgationDate, :promulgationNo, :effectiveDate,
                    :revisionType, :isCurrent, :rawXmlGcsUri)
            RETURNING id
            """.trimIndent(),
        )
            .param("lawId", lawId)
            .param("lsiSeq", parsed.lsiSeq)
            .param("promulgationDate", java.sql.Date.valueOf(parsed.promulgationDate))
            .param("promulgationNo", parsed.promulgationNo)
            .param("effectiveDate", java.sql.Date.valueOf(parsed.effectiveDate))
            .param("revisionType", parsed.revisionType)
            .param("isCurrent", isCurrent)
            .param("rawXmlGcsUri", rawXmlGcsUri)
            .query(UUID::class.java).single()
    }

    fun insertArticles(lawVersionId: UUID, articles: List<ParsedArticle>) {
        if (articles.isEmpty()) return
        // JdbcClient lacks a convenient batch API on 6.2; fall back to
        // individual inserts within the same transaction. For our volumes
        // (≈150 rows/law) the round-trip cost is negligible.
        //
        // ON CONFLICT DO NOTHING is defensive: some 법령 XMLs (e.g. 퇴직급여법,
        // 남녀고용평등법) contain repeated (조, 항, 호, 목) entries that the
        // parser legitimately emits — they are the same rule expressed in
        // multiple sections. Letting the first row win keeps the sync
        // idempotent and preserves the article shape we already test. When
        // we encounter this in practice we log the skipped count so it's
        // visible.
        for (a in articles) {
            jdbc.sql(
                """
                INSERT INTO article
                    (law_version_id, jo, jo_branch, hang, ho, mok, title, body, effective_date)
                VALUES
                    (:lawVersionId, :jo, :joBranch, :hang, :ho, :mok, :title, :body, :effectiveDate)
                ON CONFLICT ON CONSTRAINT article_locator_uq DO NOTHING
                """.trimIndent(),
            )
                .param("lawVersionId", lawVersionId)
                .param("jo", a.jo)
                .param("joBranch", a.joBranch)
                .param("hang", a.hang)
                .param("ho", a.ho)
                .param("mok", a.mok)
                .param("title", a.title)
                .param("body", a.body)
                .param("effectiveDate", a.effectiveDate?.let { java.sql.Date.valueOf(it) })
                .update()
        }
    }

    fun countArticlesForVersion(lawVersionId: UUID): Int =
        jdbc.sql("SELECT COUNT(*) FROM article WHERE law_version_id = :id")
            .param("id", lawVersionId)
            .query(Int::class.java).single()
}
