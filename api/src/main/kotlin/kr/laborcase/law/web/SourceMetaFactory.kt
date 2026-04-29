package kr.laborcase.law.web

import java.time.Clock
import java.time.Instant

/**
 * Builds the provenance block (공공누리 제1유형 출처표시) for outgoing
 * responses. Rewrites any URL originally provided by 법제처 so the caller's
 * OC never reaches the client — 법령상세링크 in raw XML includes
 * `?OC=zephyr&...` (see docs/research/drf-schema-notes.md §법령상세링크).
 */
class SourceMetaFactory(
    private val clock: Clock = Clock.systemUTC(),
    private val provider: String = "법제처 국가법령정보센터",
    private val license: String = "KOGL-1",
) {
    fun forLawVersion(lsiSeq: String): SourceMeta = SourceMeta(
        provider = provider,
        license = license,
        url = "https://www.law.go.kr/lsInfoP.do?lsiSeq=$lsiSeq",
        retrievedAt = Instant.now(clock),
    )

    fun forLawList(): SourceMeta = SourceMeta(
        provider = provider,
        license = license,
        url = "https://www.law.go.kr",
        retrievedAt = Instant.now(clock),
    )
}
