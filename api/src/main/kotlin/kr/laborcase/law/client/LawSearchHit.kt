package kr.laborcase.law.client

import java.time.LocalDate

/**
 * Minimal projection of a `<law>` entry from `lawSearch.do` responses.
 *
 * Just enough for (a) identifying which law a search matched and (b) deciding
 * whether to pull the full body. Full parsing of the law body happens in
 * LawXmlParser (Task 4), not here.
 */
data class LawSearchHit(
    val lsId: String,            // 법령ID
    val lsiSeq: String,          // 법령일련번호 = MST
    val nameKr: String,          // 법령명한글
    val shortName: String?,      // 법령약칭명 (may be empty)
    val promulgationDate: LocalDate, // 공포일자
    val effectiveDate: LocalDate,    // 시행일자
    val revisionType: String,    // 제개정구분명
    val isCurrent: Boolean,      // 현행연혁코드 == "현행"
    val department: String,      // 소관부처명
)
