package kr.laborcase.law.xml

import java.time.LocalDate

/** Parsed law envelope produced by LawXmlParser for a single lawService response. */
data class ParsedLawBody(
    val law: ParsedLaw,
    val version: ParsedLawVersion,
    val articles: List<ParsedArticle>,
)

data class ParsedLaw(
    val lsId: String,         // 법령ID (persistent, zero-padded 6 chars)
    val nameKr: String,       // 법령명_한글
    val shortName: String?,   // 법령약칭명 (may be null)
    val department: String?,  // 소관부처 text
)

data class ParsedLawVersion(
    val lsiSeq: String,              // MST/법령일련번호 — supplied by caller (not in body XML)
    val promulgationDate: LocalDate, // 공포일자
    val promulgationNo: String?,     // 공포번호
    val effectiveDate: LocalDate,    // 시행일자
    val revisionType: String?,       // 제개정구분
)

/**
 * Flattened article row. Follows the Task 2 schema convention:
 *
 *   jo, hang, ho are zero-padded 6-char strings of the raw number
 *   (제23조 → "000023", not "002300"; the URL-param shape lives in
 *    ArticleLocator.toDrfIntParam).
 *
 *   mok is the Korean ordinal character itself (가/나/다/...), capped at 4 chars
 *   to match the schema.
 *
 * Rows come in 4 shapes:
 *   (jo,        null, null, null) — 조 header (e.g., 제23조)
 *   (jo, hang,       null, null)  — ①항 of that 조
 *   (jo, hang, ho,        null)   — 1호 of that 항
 *   (jo, hang, ho, mok)           — 가목 of that 호
 */
data class ParsedArticle(
    val jo: String,              // zero-padded 6 chars (e.g., "000023")
    val joBranch: Int? = null,   // 조문가지번호: null for 제N조, K for 제N조의K
    val hang: String? = null,    // zero-padded 6 chars or null
    val ho: String? = null,      // zero-padded 6 chars or null
    val mok: String? = null,     // 1..4 chars or null
    val title: String? = null,   // 조문제목 (only set on the 조 header row)
    val body: String,            // 조문내용 / 항내용 / 호내용 / 목내용 (trimmed CDATA)
    val effectiveDate: LocalDate? = null, // 조문시행일자 (only on the 조 header row)
)
