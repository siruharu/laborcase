package kr.laborcase.law.web

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import org.springframework.http.HttpStatus

/**
 * Read-side HTTP surface for laborcase law data.
 *
 * All responses go through [ApiResponse] so the caller sees provenance and
 * the legal disclaimer on every call — a hard requirement from
 * CLAUDE.md §법적 제약 and 공공누리 제1유형 출처표시 의무
 * (docs/legal/source-attribution.md).
 */
@RestController
@RequestMapping("/api/v1/laws")
class LawController(
    private val repo: LawReadRepository,
    private val sourceMeta: SourceMetaFactory,
    private val freshness: SyncFreshnessService,
) {

    @GetMapping
    fun list(): ResponseEntity<ApiResponse<List<LawSummary>>> {
        val laws = repo.listLaws()
        return ResponseEntity.ok(
            ApiResponse(
                data = laws,
                source = sourceMeta.forLawList(),
                freshness = freshness.current(),
            ),
        )
    }

    /**
     * Return one law's articles, optionally filtered by jo/hang/ho. A plain
     * `/articles` call returns every row for the current version; supplying
     * `jo` narrows to that 조 and everything below it, and so on.
     */
    @GetMapping("/{key}/articles")
    fun articles(
        @PathVariable key: String,
        @RequestParam(required = false) jo: Int?,
        @RequestParam(required = false) hang: Int?,
        @RequestParam(required = false) ho: Int?,
    ): ResponseEntity<ApiResponse<ArticleListResponse>> {
        val law = repo.findLawByKey(key)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "law not found for key: $key")

        val articles = repo.findArticles(lsId = law.lsId, jo = jo, hang = hang, ho = ho)
        if (jo != null && articles.isEmpty()) {
            throw ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "no article found in $key for jo=$jo hang=$hang ho=$ho",
            )
        }

        return ResponseEntity.ok(
            ApiResponse(
                data = ArticleListResponse(law = law, articles = articles),
                source = sourceMeta.forLawVersion(law.lsiSeq),
                freshness = freshness.current(),
            ),
        )
    }
}
