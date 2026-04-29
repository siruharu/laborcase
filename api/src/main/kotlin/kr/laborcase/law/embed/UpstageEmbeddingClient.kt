package kr.laborcase.law.embed

import com.fasterxml.jackson.annotation.JsonInclude
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClient

/**
 * Thin HTTP client for Upstage's OpenAI-compatible embedding endpoint.
 *
 * Two models, one vector space (ADR-0003):
 *   - solar-embedding-1-large-passage — use for article bodies
 *   - solar-embedding-1-large-query   — use for user queries at runtime
 *
 * We pass arrays of inputs in a single call whenever possible; Upstage bills
 * per token, but network round-trips dominate wall time for short calls so
 * batching matters for full-sync throughput.
 */
class UpstageEmbeddingClient(
    apiKey: String,
    private val baseUrl: String = "https://api.upstage.ai/v1",
    // SimpleClientHttpRequestFactory uses JDK URLConnection (HTTP/1.1 only).
    // The default JdkClientHttpRequestFactory tries HTTP/2 which WireMock 3
    // sometimes rejects with RST_STREAM during POSTs — production Upstage
    // endpoints happily accept HTTP/1.1 so we simply pin it.
    private val restClient: RestClient = RestClient.builder()
        .requestFactory(SimpleClientHttpRequestFactory())
        .build(),
    private val maxAttempts: Int = 3,
    private val initialBackoffMillis: Long = 300,
) {

    private val log = LoggerFactory.getLogger(UpstageEmbeddingClient::class.java)

    // Trim defensively: Secret Manager values seeded via
    // `gcloud secrets versions add --data-file=-` keep the trailing newline
    // from the paste. curl tolerates that, but Java HttpURLConnection rejects
    // newline characters inside header values
    // ("Illegal character(s) in message header value").
    private val apiKey: String = apiKey.trim()

    init {
        require(this.apiKey.isNotBlank()) { "Upstage API key must be non-blank" }
    }

    fun embedPassages(texts: List<String>): List<FloatArray> =
        embed(inputs = texts, model = MODEL_PASSAGE)

    fun embedQuery(text: String): FloatArray =
        embed(inputs = listOf(text), model = MODEL_QUERY).first()

    private fun embed(inputs: List<String>, model: String): List<FloatArray> {
        require(inputs.isNotEmpty()) { "inputs must not be empty" }

        var attempt = 0
        var backoff = initialBackoffMillis
        while (true) {
            attempt += 1
            try {
                val response = restClient.post()
                    .uri("$baseUrl/embeddings")
                    .header("Authorization", "Bearer $apiKey")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(EmbedRequest(input = inputs, model = model))
                    .retrieve()
                    .body(EmbedResponse::class.java)
                    ?: error("empty body from Upstage $model")

                return response.data
                    .sortedBy { it.index }
                    .map { FloatArray(it.embedding.size) { i -> it.embedding[i] } }
            } catch (e: HttpClientErrorException) {
                if ((e.statusCode.value() == 429 || e.statusCode.value() == 408) && attempt < maxAttempts) {
                    log.warn("Upstage {} on {}, backoff {}ms ({}/{})", e.statusCode, model, backoff, attempt, maxAttempts)
                    Thread.sleep(backoff)
                    backoff *= 2
                    continue
                }
                throw e
            } catch (e: HttpServerErrorException) {
                if (attempt < maxAttempts) {
                    log.warn("Upstage {} on {}, backoff {}ms ({}/{})", e.statusCode, model, backoff, attempt, maxAttempts)
                    Thread.sleep(backoff)
                    backoff *= 2
                    continue
                }
                throw e
            } catch (e: ResourceAccessException) {
                if (attempt < maxAttempts) {
                    log.warn("Upstage network {}, backoff {}ms ({}/{})", e.message, backoff, attempt, maxAttempts)
                    Thread.sleep(backoff)
                    backoff *= 2
                    continue
                }
                throw e
            }
        }
    }

    companion object {
        const val MODEL_PASSAGE = "solar-embedding-1-large-passage"
        const val MODEL_QUERY = "solar-embedding-1-large-query"

        // Declared sizes are fixed by Upstage spec (ADR-0003).
        const val EMBEDDING_DIMS = 4096
    }
}

// ----- wire DTOs (OpenAI-compatible payloads) -----

@JsonInclude(JsonInclude.Include.NON_NULL)
internal data class EmbedRequest(
    val input: List<String>,
    val model: String,
)

internal data class EmbedResponse(
    val data: List<EmbedItem>,
    val model: String,
    val usage: Usage? = null,
)

internal data class EmbedItem(
    val index: Int,
    val embedding: List<Float>,
)

internal data class Usage(
    val promptTokens: Int,
    val totalTokens: Int,
)
