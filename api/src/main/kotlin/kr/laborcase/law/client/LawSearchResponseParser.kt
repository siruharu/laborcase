package kr.laborcase.law.client

import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Parses the lawSearch.do XML response into LawSearchHit records.
 *
 * Intentionally minimal — only fields our service consumes. Full law-body
 * parsing (조문/항/호/목 flattening) is the responsibility of LawXmlParser
 * in Task 4; this class's only job is identifying matches.
 */
class LawSearchResponseParser {

    private val xmlMapper: XmlMapper = XmlMapper().apply { registerKotlinModule() }
    private val ymd: DateTimeFormatter = DateTimeFormatter.BASIC_ISO_DATE

    fun parse(xml: String): List<LawSearchHit> {
        // The XML is structured enough that we don't need a full POJO tree.
        // JsonNode keeps the parser simple and tolerant of extra fields.
        val root = xmlMapper.readTree(xml)

        val lawNodes = when {
            root.path("law").isArray -> root.path("law").toList()
            !root.path("law").isMissingNode -> listOf(root.path("law"))
            else -> emptyList()
        }

        return lawNodes.map { node ->
            LawSearchHit(
                lsId = node.path("법령ID").asText(""),
                lsiSeq = node.path("법령일련번호").asText(""),
                nameKr = node.path("법령명한글").asText(""),
                shortName = node.path("법령약칭명").asText("").takeIf { it.isNotBlank() },
                promulgationDate = LocalDate.parse(node.path("공포일자").asText(), ymd),
                effectiveDate = LocalDate.parse(node.path("시행일자").asText(), ymd),
                revisionType = node.path("제개정구분명").asText(""),
                isCurrent = node.path("현행연혁코드").asText("") == "현행",
                department = node.path("소관부처명").asText(""),
            )
        }
    }
}
