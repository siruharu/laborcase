package kr.laborcase.law.web

import com.fasterxml.jackson.annotation.JsonInclude
import java.time.LocalDate

@JsonInclude(JsonInclude.Include.NON_NULL)
data class LawSummary(
    val lsId: String,
    val nameKr: String,
    val shortName: String?,
    val lsiSeq: String,
    val effectiveDate: LocalDate,
    val promulgationDate: LocalDate,
    val revisionType: String?,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class ArticleDto(
    val jo: Int,
    val joBranch: Int?,
    val hang: Int?,
    val ho: Int?,
    val mok: String?,
    val title: String?,
    val body: String,
    val effectiveDate: LocalDate?,
)

data class ArticleListResponse(
    val law: LawSummary,
    val articles: List<ArticleDto>,
)
