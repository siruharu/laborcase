package kr.laborcase.law.web

import org.springframework.jdbc.core.simple.JdbcClient
import java.time.LocalDate

/**
 * Read-side queries for the HTTP layer. Separated from
 * [kr.laborcase.law.sync.LawSyncRepository] so sync and query evolve
 * independently — read DTOs often drift faster than write paths.
 *
 * All queries filter to `law_version.is_current = TRUE`. Historical versions
 * stay in the DB (for audit) but are not exposed through this API.
 */
class LawReadRepository(private val jdbc: JdbcClient) {

    fun listLaws(): List<LawSummary> =
        jdbc.sql(
            """
            SELECT l.ls_id, l.name_kr, l.short_name,
                   lv.lsi_seq, lv.effective_date, lv.promulgation_date, lv.revision_type
              FROM law l
              JOIN law_version lv ON lv.law_id = l.id AND lv.is_current = TRUE
             ORDER BY l.short_name NULLS LAST, l.name_kr
            """.trimIndent(),
        ).query(::mapLawSummary).list()

    fun findLawByKey(key: String): LawSummary? =
        jdbc.sql(
            """
            SELECT l.ls_id, l.name_kr, l.short_name,
                   lv.lsi_seq, lv.effective_date, lv.promulgation_date, lv.revision_type
              FROM law l
              JOIN law_version lv ON lv.law_id = l.id AND lv.is_current = TRUE
             WHERE l.short_name = :key OR l.name_kr = :key OR l.ls_id = :key
             LIMIT 1
            """.trimIndent(),
        ).param("key", key)
            .query(::mapLawSummary).optional().orElse(null)

    private fun mapLawSummary(rs: java.sql.ResultSet, @Suppress("UNUSED_PARAMETER") rowNum: Int): LawSummary =
        LawSummary(
            lsId = rs.getString("ls_id"),
            nameKr = rs.getString("name_kr"),
            shortName = rs.getString("short_name"),
            lsiSeq = rs.getString("lsi_seq"),
            effectiveDate = rs.getDate("effective_date").toLocalDate(),
            promulgationDate = rs.getDate("promulgation_date").toLocalDate(),
            revisionType = rs.getString("revision_type"),
        )

    /**
     * Articles for a law's current version, optionally filtered by
     * (jo, hang, ho) integer values. A null filter means "include everything
     * at this level and below." Example: filter(jo=23) returns the 제23조
     * header plus every 항/호/목 beneath it.
     */
    fun findArticles(lsId: String, jo: Int? = null, hang: Int? = null, ho: Int? = null): List<ArticleDto> {
        // Build the WHERE clause fragment dynamically. Using explicit single-
        // line strings avoids Spring's NamedParameterParser accidentally
        // pulling adjacent keywords into the parameter name when triple-quoted
        // strings are concatenated (observed in tests — `:lsId\nORDER` was
        // read as `:lsIdORDER`).
        val filters = buildString {
            if (jo != null) append(" AND a.jo = :joPadded")
            if (hang != null) append(" AND a.hang = :hangPadded")
            if (ho != null) append(" AND a.ho = :hoPadded")
        }

        val sql = "SELECT a.jo, a.jo_branch, a.hang, a.ho, a.mok, a.title, a.body, a.effective_date" +
            " FROM article a" +
            " JOIN law_version lv ON lv.id = a.law_version_id AND lv.is_current = TRUE" +
            " JOIN law l ON l.id = lv.law_id" +
            " WHERE l.ls_id = :lsId" +
            filters +
            " ORDER BY a.jo, COALESCE(a.jo_branch, 0)," +
            " COALESCE(a.hang, '000000'), COALESCE(a.ho, '000000'), COALESCE(a.mok, '')"

        val spec = jdbc.sql(sql).param("lsId", lsId)
        if (jo != null) spec.param("joPadded", pad6(jo))
        if (hang != null) spec.param("hangPadded", pad6(hang))
        if (ho != null) spec.param("hoPadded", pad6(ho))

        return spec.query { rs, _ ->
            ArticleDto(
                jo = rs.getString("jo").trim().toInt(),
                joBranch = rs.getObject("jo_branch", Integer::class.java)?.toInt(),
                hang = rs.getString("hang")?.trim()?.takeIf { it.isNotBlank() }?.toInt(),
                ho = rs.getString("ho")?.trim()?.takeIf { it.isNotBlank() }?.toInt(),
                mok = rs.getString("mok"),
                title = rs.getString("title"),
                body = rs.getString("body"),
                effectiveDate = rs.getDate("effective_date")?.toLocalDate(),
            )
        }.list()
    }

    private fun pad6(n: Int): String = n.toString().padStart(6, '0')

    // Forces JdbcClient to map `LocalDate` columns correctly (default mapper
    // returns java.sql.Date). Called via reflection from the list() calls above.
    @Suppress("unused")
    private fun mapDate(v: Any?): LocalDate? = when (v) {
        is LocalDate -> v
        is java.sql.Date -> v.toLocalDate()
        null -> null
        else -> null
    }
}
