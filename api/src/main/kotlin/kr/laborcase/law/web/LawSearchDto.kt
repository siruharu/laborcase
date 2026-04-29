package kr.laborcase.law.web

import com.fasterxml.jackson.annotation.JsonInclude

/**
 * One row in a similarity search result. `distance` is the cosine distance
 * between the user's query embedding and the stored article embedding —
 * 0.0 means identical direction, ~1.0 means orthogonal, ~2.0 means opposite.
 *
 * Distances on Upstage solar-embedding-1-large-{passage,query} for
 * relevant Korean legal queries typically land in 0.4–0.7. Anything above
 * ~1.0 is rarely useful.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ArticleSearchHit(
    val law: LawSummary,
    val article: ArticleDto,
    val distance: Double,
)

/** Request body for POST /api/v1/articles/search. */
data class ArticleSearchRequest(
    val query: String,
    val limit: Int? = null,
)

data class ArticleSearchResponse(
    val query: String,
    val hits: List<ArticleSearchHit>,
)
