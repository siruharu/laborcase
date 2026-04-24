package kr.laborcase.law.xml

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Parses the full-body XML returned by `/DRF/lawService.do?target=law` (and the
 * per-article variant `target=lawjosub`) into flattened [ParsedArticle] rows
 * plus the enclosing [ParsedLaw] + [ParsedLawVersion].
 *
 * Only `조문여부 == "조문"` and `"전문"` entries are emitted. Section headers
 * (편/장/절/관) are not present in this fixture set but would be filtered out
 * by the same predicate.
 *
 * `lsiSeq` / MST is not present in the body XML (it's only in search
 * responses), so the caller (Task 6 FullSyncJob) passes the value that the
 * prior search step recorded.
 */
class LawXmlParser {

    private val xmlMapper: XmlMapper = XmlMapper().apply { registerKotlinModule() }
    private val ymd: DateTimeFormatter = DateTimeFormatter.BASIC_ISO_DATE

    fun parse(xml: String, lsiSeq: String): ParsedLawBody {
        val root = xmlMapper.readTree(xml)
        val base = root.path("기본정보")

        val law = ParsedLaw(
            lsId = base.path("법령ID").asText(""),
            nameKr = base.path("법령명_한글").asText(""),
            shortName = base.path("법령약칭명").asText("").takeIf { it.isNotBlank() },
            department = base.path("소관부처").let { node ->
                when {
                    node.isTextual -> node.asText().takeIf { it.isNotBlank() }
                    node.has("") -> node.path("").asText().takeIf { it.isNotBlank() }
                    else -> node.asText("").takeIf { it.isNotBlank() }
                }
            },
        )

        val version = ParsedLawVersion(
            lsiSeq = lsiSeq,
            promulgationDate = LocalDate.parse(base.path("공포일자").asText(), ymd),
            promulgationNo = base.path("공포번호").asText("").takeIf { it.isNotBlank() },
            effectiveDate = LocalDate.parse(base.path("시행일자").asText(), ymd),
            revisionType = base.path("제개정구분").asText("").takeIf { it.isNotBlank() },
        )

        val articles = parseArticles(root)
        return ParsedLawBody(law = law, version = version, articles = articles)
    }

    private fun parseArticles(root: JsonNode): List<ParsedArticle> {
        val unitsNode = root.path("조문").path("조문단위")
        val units: List<JsonNode> = when {
            unitsNode.isArray -> unitsNode.toList()
            unitsNode.isMissingNode -> emptyList()
            else -> listOf(unitsNode)
        }

        val out = mutableListOf<ParsedArticle>()

        for (unit in units) {
            val kind = unit.path("조문여부").asText("")
            if (kind !in INCLUDED_KINDS) continue

            val joRaw = unit.path("조문번호").asText("")
            if (joRaw.isBlank()) continue
            val jo = padSix(joRaw.toInt())
            val joBranch = unit.path("조문가지번호").asText("").trim().takeIf { it.isNotBlank() }?.toInt()
            val title = unit.path("조문제목").asText("").trim().takeIf { it.isNotBlank() }
            val effective = unit.path("조문시행일자").asText("")
                .takeIf { it.isNotBlank() }
                ?.let { LocalDate.parse(it, ymd) }

            // 조 header row
            val headerBody = unit.path("조문내용").asText("").trim()
            out += ParsedArticle(
                jo = jo,
                joBranch = joBranch,
                title = title,
                body = headerBody,
                effectiveDate = effective,
            )

            for (hangNode in arrayFrom(unit.path("항"))) {
                val hangRaw = hangNode.path("항번호").asText("").trim()
                if (hangRaw.isBlank()) continue // some XML has empty 항 placeholders; skip.
                val hangN = parseHangNumber(hangRaw)
                val hang = padSix(hangN)
                val hangBody = hangNode.path("항내용").asText("").trim()

                out += ParsedArticle(
                    jo = jo,
                    joBranch = joBranch,
                    hang = hang,
                    body = hangBody,
                )

                for (hoNode in arrayFrom(hangNode.path("호"))) {
                    val hoRaw = hoNode.path("호번호").asText("").trim()
                    if (hoRaw.isBlank()) continue
                    val hoN = parseHoNumber(hoRaw)
                    val ho = padSix(hoN)
                    val hoBody = hoNode.path("호내용").asText("").trim()

                    out += ParsedArticle(
                        jo = jo,
                        joBranch = joBranch,
                        hang = hang,
                        ho = ho,
                        body = hoBody,
                    )

                    for (mokNode in arrayFrom(hoNode.path("목"))) {
                        val mok = mokNode.path("목번호").asText("").trim().take(4)
                        val mokBody = mokNode.path("목내용").asText("").trim()
                        if (mok.isBlank()) continue
                        out += ParsedArticle(
                            jo = jo,
                            joBranch = joBranch,
                            hang = hang,
                            ho = ho,
                            mok = mok,
                            body = mokBody,
                        )
                    }
                }
            }
        }
        return out
    }

    private fun arrayFrom(node: JsonNode): List<JsonNode> = when {
        node.isArray -> node.toList()
        node.isMissingNode || node.isNull -> emptyList()
        else -> listOf(node)
    }

    companion object {
        // Only real articles are stored. "전문" entries are chapter/section
        // dividers ("제1장 총칙") that share a 조문번호 with the article they
        // precede and would collide on the UNIQUE (law_version, jo, hang, ho,
        // mok) index. Section navigation metadata can be added later if a
        // product needs it (probably as a separate table, not here).
        private val INCLUDED_KINDS = setOf("조문")

        internal fun padSix(n: Int): String = n.toString().padStart(6, '0')

        /**
         * Full-width circled digits ①-⑳ map to 1-20; ㉑-㉟ map to 21-35.
         * Plain ASCII digits fall through for defensive-parsing.
         */
        internal fun parseHangNumber(raw: String): Int {
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) error("empty 항번호")
            val c = trimmed.first()
            return when {
                c in '①'..'⑳' -> c.code - '①'.code + 1
                c in '㉑'..'㉟' -> c.code - '㉑'.code + 21
                c.isDigit() -> trimmed.takeWhile { it.isDigit() }.toInt()
                else -> error("unrecognized 항번호: \"$raw\" (first code=${c.code})")
            }
        }

        /** 호번호 pattern: "1.", "12." — strip trailing period and parse. */
        internal fun parseHoNumber(raw: String): Int {
            val trimmed = raw.trim().removeSuffix(".").trim()
            return trimmed.takeWhile { it.isDigit() }.toInt()
        }
    }
}
