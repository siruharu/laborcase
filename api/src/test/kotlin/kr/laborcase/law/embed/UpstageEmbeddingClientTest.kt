package kr.laborcase.law.embed

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class UpstageEmbeddingClientTest {

    private lateinit var server: WireMockServer
    private lateinit var client: UpstageEmbeddingClient

    @BeforeEach
    fun start() {
        server = WireMockServer(WireMockConfiguration.options().dynamicPort())
        server.start()
        client = UpstageEmbeddingClient(
            apiKey = "test-key", // pragma: allowlist secret -- wiremock only
            baseUrl = "http://localhost:${server.port()}/v1",
            initialBackoffMillis = 5,
        )
    }

    @AfterEach
    fun stop() {
        server.stop()
    }

    @Test
    fun `embedPassages sends model=solar-embedding-1-large-passage and bearer header`() {
        server.stubFor(
            post(urlPathEqualTo("/v1/embeddings"))
                .withHeader("Authorization", equalTo("Bearer test-key"))
                .withRequestBody(matchingJsonPath("$.model", equalTo(UpstageEmbeddingClient.MODEL_PASSAGE)))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(responseJson(listOf(floatArrayOf(0.1f, 0.2f, 0.3f)))),
                ),
        )

        val result = client.embedPassages(listOf("body"))
        assertEquals(1, result.size)
        assertEquals(3, result.first().size)
        assertEquals(0.1f, result.first()[0])
    }

    @Test
    fun `embedQuery uses query model`() {
        server.stubFor(
            post(urlPathEqualTo("/v1/embeddings"))
                .withRequestBody(matchingJsonPath("$.model", equalTo(UpstageEmbeddingClient.MODEL_QUERY)))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(responseJson(listOf(floatArrayOf(0.9f, 0.8f)))),
                ),
        )

        val vec = client.embedQuery("해고")
        assertEquals(2, vec.size)
        assertEquals(0.9f, vec[0])
    }

    @Test
    fun `retries on 429 then succeeds`() {
        server.stubFor(
            post(urlPathEqualTo("/v1/embeddings"))
                .inScenario("retry").whenScenarioStateIs("Started")
                .willSetStateTo("hit-once")
                .willReturn(aResponse().withStatus(429)),
        )
        server.stubFor(
            post(urlPathEqualTo("/v1/embeddings"))
                .inScenario("retry").whenScenarioStateIs("hit-once")
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(responseJson(listOf(floatArrayOf(1f, 2f)))),
                ),
        )

        val v = client.embedPassages(listOf("a")).first()
        assertTrue(v.isNotEmpty())
        assertEquals(2, server.allServeEvents.count { it.request.url == "/v1/embeddings" })
    }

    @Test
    fun `multiple inputs are returned in input order even if API returns unsorted indices`() {
        server.stubFor(
            post(urlPathEqualTo("/v1/embeddings"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                            """
                            {"model":"${UpstageEmbeddingClient.MODEL_PASSAGE}",
                             "data":[
                               {"index":1,"embedding":[0.2,0.2]},
                               {"index":0,"embedding":[0.1,0.1]}
                             ]}
                            """.trimIndent(),
                        ),
                ),
        )

        val out = client.embedPassages(listOf("first", "second"))
        assertEquals(0.1f, out[0][0])
        assertEquals(0.2f, out[1][0])
    }

    private fun responseJson(embeddings: List<FloatArray>): String {
        val data = embeddings.mapIndexed { i, v ->
            """{"index":$i,"embedding":[${v.joinToString(",")}]}"""
        }.joinToString(",")
        return """{"model":"${UpstageEmbeddingClient.MODEL_PASSAGE}","data":[$data]}"""
    }
}
