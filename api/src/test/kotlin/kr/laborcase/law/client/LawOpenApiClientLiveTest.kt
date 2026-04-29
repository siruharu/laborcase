package kr.laborcase.law.client

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable

// Live smoke test against the real DRF endpoint.
//
// Runs only when OC_LAW is exported AND the caller is on the NAT IP
// registered with 법제처 (34.64.141.102). After registering that IP the OC
// becomes IP-restricted, so invocations from a developer laptop return the
// "사용자 정보 검증에 실패" auth-failure HTML and this test deliberately
// fails. The intended runner is the Cloud Run Job delivered in Task 6.
//
// Run locally with:
//   RUN_LIVE_TESTS=true OC_LAW=<oc> ./gradlew :api:test \
//     --tests "*LawOpenApiClientLiveTest"
@Tag("live")
@EnabledIfEnvironmentVariable(named = "OC_LAW", matches = ".+")
class LawOpenApiClientLiveTest {

    private val oc: String get() = requireNotNull(System.getenv("OC_LAW"))
    private val client: LawOpenApiClient
        get() = LawOpenApiClient(urlBuilder = LawOpenApiUrlBuilder(oc = oc))

    @Test
    fun `real searchLaws returns 근로기준법 as first hit with lsId 001872`() {
        val hits = client.searchLaws("근로기준법", display = 3)
        assertTrue(hits.isNotEmpty(), "expected at least one hit for 근로기준법")
        val mainLaw = hits.firstOrNull { it.nameKr == "근로기준법" }
        assertTrue(mainLaw != null, "first 근로기준법 result must be the main act")
        assertTrue(mainLaw!!.lsId == "001872", "lsId should be 001872, got ${mainLaw.lsId}")
    }

    @Test
    fun `real fetchArticle returns 제23조 해고 등의 제한`() {
        val resp = client.fetchArticle(lsId = "001872", locator = ArticleLocator(jo = 23))
        assertTrue(resp.xml.contains("해고 등의 제한"), "response missing expected article title")
        assertTrue(resp.xml.contains("<조문번호>23</조문번호>"))
    }
}
