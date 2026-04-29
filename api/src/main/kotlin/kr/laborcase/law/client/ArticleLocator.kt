package kr.laborcase.law.client

/**
 * Identifies a single article node in a law: 조 → 항 → 호 → 목.
 *
 * `jo` is the raw 조 number (1-based integer, not yet formatted for the URL).
 * Sub-levels are nullable; callers ask for the deepest level they want and the
 * builder formats the URL accordingly.
 *
 * URL parameter conversion rule (verified against fixture responses —
 * see docs/research/drf-schema-notes.md §JO 파라미터 포맷 정정):
 *   param = (n * 100) left-padded to 6 digits
 *   제23조 → "002300", 제23조 제1항 → HANG "000100", 제23조 제1항 제2호 → HO "000200".
 */
data class ArticleLocator(
    val jo: Int,
    val hang: Int? = null,
    val ho: Int? = null,
    val mok: String? = null,
) {
    init {
        require(jo in 1..9999) { "jo must be 1..9999, got $jo" }
        hang?.let { require(it in 1..9999) { "hang must be 1..9999, got $it" } }
        ho?.let { require(it in 1..9999) { "ho must be 1..9999, got $it" } }
        mok?.let { require(it.isNotBlank() && it.length <= 4) { "mok must be 1..4 non-blank chars, got \"$it\"" } }
    }

    fun joParam(): String = toDrfIntParam(jo)
    fun hangParam(): String? = hang?.let(::toDrfIntParam)
    fun hoParam(): String? = ho?.let(::toDrfIntParam)

    companion object {
        fun toDrfIntParam(n: Int): String = (n * 100).toString().padStart(6, '0')
    }
}
