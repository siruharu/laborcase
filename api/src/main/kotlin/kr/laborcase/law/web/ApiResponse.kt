package kr.laborcase.law.web

import com.fasterxml.jackson.annotation.JsonInclude
import java.time.Instant

/**
 * Every HTTP response wraps the payload in [data], attaches a [source]
 * attribution block that satisfies the 공공누리 제1유형 출처표시 의무, and
 * pins the [disclaimer] from CLAUDE.md §legal-boundaries.
 *
 * Using a single wrapper means a future caller (frontend, AI server, external
 * audit) always has the provenance and legal notice in every response and
 * never has to guess where to find them.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ApiResponse<T>(
    val data: T,
    val source: SourceMeta,
    val freshness: Freshness? = null,
    val disclaimer: String = LEGAL_DISCLAIMER,
) {
    companion object {
        // CLAUDE.md §UI 필수 요소 — keep character-for-character.
        const val LEGAL_DISCLAIMER: String =
            "본 정보는 공개된 판례 및 법령에 기반한 참고 자료이며, 법률 자문이 아닙니다. " +
                "구체적 사건은 반드시 변호사·노무사와 상담하세요."
    }
}

@JsonInclude(JsonInclude.Include.NON_NULL)
data class SourceMeta(
    val provider: String,
    val license: String,
    val url: String,
    val retrievedAt: Instant,
)
