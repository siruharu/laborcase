package kr.laborcase.law.web

import kr.laborcase.law.embed.UpstageEmbeddingClient
import org.springframework.beans.factory.ObjectProvider
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

/**
 * Natural-language similarity search over current-version 조문.
 *
 * The flow:
 *   1. embed the user's `query` with Upstage solar-embedding-1-large-query
 *   2. order article_embedding rows by cosine distance to that vector
 *   3. join law / law_version / article and return the top N
 *
 * The Upstage client is wired via [ObjectProvider] so the controller still
 * starts when `UPSTAGE_API_KEY` is unset (e.g. local dev) — calls just
 * return 503 in that case.
 */
@RestController
@RequestMapping("/api/v1/articles")
class SearchController(
    private val repo: LawReadRepository,
    private val sourceMeta: SourceMetaFactory,
    private val freshness: SyncFreshnessService,
    private val upstageProvider: ObjectProvider<UpstageEmbeddingClient>,
) {

    @PostMapping("/search")
    fun search(@RequestBody body: ArticleSearchRequest): ResponseEntity<ApiResponse<ArticleSearchResponse>> {
        val query = body.query.trim()
        if (query.isEmpty()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "query must not be blank")
        }
        if (query.length > MAX_QUERY_CHARS) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "query is too long ($MAX_QUERY_CHARS chars max)",
            )
        }
        val limit = (body.limit ?: DEFAULT_LIMIT).coerceIn(1, MAX_LIMIT)

        val client = upstageProvider.ifAvailable
            ?: throw ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "embedding backend not configured — set UPSTAGE_API_KEY",
            )

        val queryVec = client.embedQuery(query)
        val hits = repo.findSimilarArticles(queryVec, limit)

        return ResponseEntity.ok(
            ApiResponse(
                data = ArticleSearchResponse(query = query, hits = hits),
                source = sourceMeta.forLawList(),
                freshness = freshness.current(),
            ),
        )
    }

    companion object {
        const val DEFAULT_LIMIT = 5
        const val MAX_LIMIT = 20
        const val MAX_QUERY_CHARS = 1000
    }
}
