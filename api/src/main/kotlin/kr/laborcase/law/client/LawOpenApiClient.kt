package kr.laborcase.law.client

import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatusCode
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClient
import java.net.URI
import java.time.Instant

/**
 * Client for the 법제처 국가법령정보 DRF Open API.
 *
 * Responsibilities:
 *   - Attach OC + enforce type=XML (via LawOpenApiUrlBuilder).
 *   - Retry transient errors (429/5xx) up to 3 times with exponential backoff.
 *   - Return raw XML responses with request context so upstream parsers
 *     (LawXmlParser in Task 4) own all XML → domain conversion.
 *
 * 4xx other than 429 are surfaced immediately — they indicate caller bugs
 * (missing params, wrong lsId, OC rejected) and retrying only delays the fix.
 *
 * lsHistory is currently gated by our OC approval and raises
 * UnsupportedOperationException. When the 연혁 permission is added
 * (docs/research/drf-schema-notes.md §lsHistory 권한 누락), replace the
 * stub body with fetchHistory(uri).
 */
class LawOpenApiClient(
    private val urlBuilder: LawOpenApiUrlBuilder,
    private val restClient: RestClient = RestClient.create(),
    private val maxAttempts: Int = 3,
    private val initialBackoffMillis: Long = 300,
    private val searchResponseParser: LawSearchResponseParser = LawSearchResponseParser(),
) {

    private val log = LoggerFactory.getLogger(LawOpenApiClient::class.java)

    fun searchLaws(query: String, display: Int = 10, page: Int = 1): List<LawSearchHit> {
        val uri = urlBuilder.searchLaw(query, display = display, page = page)
        val body = fetchXml(uri)
        return searchResponseParser.parse(body)
    }

    fun fetchLawByLsId(lsId: String, effectiveYmd: String? = null): LawXmlResponse {
        val uri = urlBuilder.fetchLaw(lsId, effectiveYmd)
        return LawXmlResponse(xml = fetchXml(uri), requestUri = uri, retrievedAt = Instant.now())
    }

    fun fetchArticle(
        lsId: String,
        locator: ArticleLocator,
        effectiveYmd: String? = null,
    ): LawXmlResponse {
        val uri = urlBuilder.fetchArticle(lsId, locator, effectiveYmd)
        return LawXmlResponse(xml = fetchXml(uri), requestUri = uri, retrievedAt = Instant.now())
    }

    fun fetchHistory(@Suppress("UNUSED_PARAMETER") query: String): LawXmlResponse {
        throw UnsupportedOperationException(
            "lsHistory is not enabled on the current OC approval. " +
                "See docs/research/drf-schema-notes.md §lsHistory 권한 누락. " +
                "Use searchLaws() + lsi_seq comparison for revision detection.",
        )
    }

    private fun fetchXml(uri: URI): String {
        var attempt = 0
        var backoff = initialBackoffMillis
        while (true) {
            attempt += 1
            try {
                val body = restClient.get()
                    .uri(uri)
                    .retrieve()
                    .body(String::class.java)
                    ?: error("empty body from $uri")

                if (body.contains("미신청된 목록/본문") || body.contains("사용자 정보 검증에 실패")) {
                    // Surface as a non-retryable auth/permission error.
                    throw LawOpenApiException(
                        "DRF returned permission/auth failure HTML for $uri (OC not registered or endpoint not approved).",
                        uri = uri,
                    )
                }
                return body
            } catch (e: HttpClientErrorException) {
                if (e.statusCode == HttpStatusCode.valueOf(429) && attempt < maxAttempts) {
                    log.warn("DRF 429, backing off {}ms (attempt {}/{})", backoff, attempt, maxAttempts)
                    Thread.sleep(backoff)
                    backoff *= 2
                    continue
                }
                throw e
            } catch (e: HttpServerErrorException) {
                if (attempt < maxAttempts) {
                    log.warn("DRF {}, backing off {}ms (attempt {}/{})", e.statusCode, backoff, attempt, maxAttempts)
                    Thread.sleep(backoff)
                    backoff *= 2
                    continue
                }
                throw e
            } catch (e: ResourceAccessException) {
                if (attempt < maxAttempts) {
                    log.warn("DRF network error {}, backing off {}ms (attempt {}/{})", e.message, backoff, attempt, maxAttempts)
                    Thread.sleep(backoff)
                    backoff *= 2
                    continue
                }
                throw e
            }
        }
    }
}

data class LawXmlResponse(
    val xml: String,
    val requestUri: URI,
    val retrievedAt: Instant,
)

class LawOpenApiException(message: String, val uri: URI) : RuntimeException(message)
