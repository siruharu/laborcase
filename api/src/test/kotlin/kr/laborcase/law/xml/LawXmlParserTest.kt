package kr.laborcase.law.xml

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.time.LocalDate

class LawXmlParserTest {

    companion object {
        private lateinit var body: ParsedLawBody

        @JvmStatic
        @BeforeAll
        fun parseOnce() {
            val xml = LawXmlParserTest::class.java.classLoader
                .getResourceAsStream("fixtures/drf/lawService_geunrogijunbeop_lsid.xml")!!
                .use { it.reader(Charsets.UTF_8).readText() }
            body = LawXmlParser().parse(xml, lsiSeq = "265959")
        }
    }

    @Test
    fun `law metadata - lsId, name, effective and promulgation dates`() {
        assertEquals("001872", body.law.lsId)
        assertEquals("근로기준법", body.law.nameKr)
        assertEquals("265959", body.version.lsiSeq)
        assertEquals(LocalDate.of(2024, 10, 22), body.version.promulgationDate)
        assertEquals(LocalDate.of(2025, 10, 23), body.version.effectiveDate)
        assertEquals("20520", body.version.promulgationNo)
    }

    @Test
    fun `flattened article rows exceed 100 and have at least one deep-nested row`() {
        // 근로기준법 has 116 articles plus sub-levels. We expect much more than 100 rows.
        assertTrue(body.articles.size > 100, "expected >100 parsed rows, got ${body.articles.size}")

        // A deep nested row exists somewhere (조+항+호 at minimum)
        assertTrue(
            body.articles.any { it.jo != "000000" && it.hang != null && it.ho != null },
            "expected at least one (조, 항, 호) row",
        )
    }

    @Test
    fun `제2조 정의 - has header + 항1 + 호1 with expected text`() {
        val article2Rows = body.articles.filter { it.jo == "000002" }
        assertTrue(article2Rows.isNotEmpty(), "제2조 rows must exist")

        val header = article2Rows.first { it.hang == null }
        assertEquals("정의", header.title, "제2조 title should be 정의")
        assertTrue(header.body.contains("제2조"), "제2조 header body should reference 제2조")

        // 제2조 제1항 제1호 - "근로자" 정의
        val ho1 = article2Rows.first { it.hang == "000001" && it.ho == "000001" }
        assertTrue(
            ho1.body.contains("근로자") && ho1.body.contains("임금"),
            "제2조 1항 1호 must contain 근로자 definition, got: ${ho1.body.take(100)}",
        )
    }

    @Test
    fun `제23조 해고 등의 제한 - has header and hang1`() {
        val rows = body.articles.filter { it.jo == "000023" }
        assertTrue(rows.isNotEmpty(), "제23조 rows must exist")

        val header = rows.first { it.hang == null }
        assertEquals("해고 등의 제한", header.title)

        val hang1 = rows.first { it.hang == "000001" }
        assertTrue(
            hang1.body.contains("정당한 이유 없이") && hang1.body.contains("해고"),
            "제23조 1항 must reference 해고, got: ${hang1.body.take(100)}",
        )
        assertNull(hang1.ho, "제23조 1항 has no 호 breakdown")
    }

    @Test
    fun `hang number parsing - circled digits map correctly`() {
        assertEquals(1, LawXmlParser.parseHangNumber("①"))
        assertEquals(2, LawXmlParser.parseHangNumber("② "))
        assertEquals(20, LawXmlParser.parseHangNumber("⑳"))
        assertEquals(21, LawXmlParser.parseHangNumber("㉑"))
    }

    @Test
    fun `ho number parsing - ASCII digit with trailing period`() {
        assertEquals(1, LawXmlParser.parseHoNumber("1."))
        assertEquals(12, LawXmlParser.parseHoNumber("12."))
        assertEquals(3, LawXmlParser.parseHoNumber("  3.  "))
    }

    @Test
    fun `padSix zero-pads to six digits`() {
        assertEquals("000001", LawXmlParser.padSix(1))
        assertEquals("000023", LawXmlParser.padSix(23))
        assertEquals("001000", LawXmlParser.padSix(1000))
    }

    @Test
    fun `all rows have a well-formed jo field`() {
        for (row in body.articles) {
            assertEquals(6, row.jo.length, "jo must be 6 chars, got \"${row.jo}\"")
            assertTrue(row.jo.all { it.isDigit() }, "jo must be digits, got \"${row.jo}\"")
        }
    }

    @Test
    fun `the header row exists exactly once per (조, 가지)`() {
        // 제43조 has sub-articles 제43조의2..제43조의8; each has its own header.
        val perLocator = body.articles
            .filter { it.hang == null }
            .groupBy { it.jo to it.joBranch }
        for ((key, rows) in perLocator) {
            assertEquals(
                1, rows.size,
                "expected exactly 1 header row per (jo, 가지), found ${rows.size} for $key",
            )
        }
    }

    @Test
    fun `제43조 has sub-articles via 조문가지번호`() {
        val article43 = body.articles.filter { it.jo == "000043" && it.hang == null }
        assertTrue(
            article43.size >= 2,
            "제43조 should expose primary + at least one sub-article via 가지번호, got ${article43.size}",
        )
        val branches = article43.map { it.joBranch }.toSet()
        assertTrue(null in branches, "제43조 main article (joBranch=null) must be present")
        assertTrue(2 in branches, "제43조의2 (joBranch=2) must be present")
    }

    @Test
    fun `article body is trimmed - no leading or trailing whitespace`() {
        val sample = body.articles.take(50)
        for (row in sample) {
            assertEquals(row.body, row.body.trim(), "body must be trimmed, row=$row")
        }
    }

    @Test
    fun `domain is populated`() {
        assertNotNull(body.law.department)
        assertTrue(body.law.department!!.contains("고용노동부"), "got: ${body.law.department}")
    }
}
