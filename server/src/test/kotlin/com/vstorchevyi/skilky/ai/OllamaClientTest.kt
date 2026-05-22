package com.vstorchevyi.skilky.ai

import com.vstorchevyi.skilky.config.AppConfig
import com.vstorchevyi.skilky.errors.AiUnavailableException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.io.IOException
import kotlin.test.Test

/**
 * [OllamaClient] against a Ktor [MockEngine] — no real Ollama. The contract
 * under test: a well-formed reply parses, and every other outcome (bad
 * status, bad envelope, non-JSON content, dead transport) collapses to one
 * [AiUnavailableException] so callers see a single typed failure.
 */
class OllamaClientTest {
    @Test
    fun `chatJson parses the assistant content of a well-formed response`() {
        runBlocking {
            // Arrange
            val sut = createSut(jsonEngine(body = chatResponse(content = """{"items":[]}""")))

            // Act
            val result = sut.chatJson("system", "user", emptyFormat())

            // Assert
            result.containsKey("items") shouldBe true
        }
    }

    @Test
    fun `chatJson maps a non-2xx status to AiUnavailableException`() {
        runBlocking {
            // Arrange
            val sut =
                createSut(jsonEngine(status = HttpStatusCode.InternalServerError, body = "upstream error"))

            // Act + Assert
            shouldThrow<AiUnavailableException> { sut.chatJson("system", "user", emptyFormat()) }
        }
    }

    @Test
    fun `chatJson maps a malformed envelope to AiUnavailableException`() {
        runBlocking {
            // Arrange — 200 OK, but the body is not an Ollama chat response
            val sut = createSut(jsonEngine(body = """{"unexpected":"shape"}"""))

            // Act + Assert
            shouldThrow<AiUnavailableException> { sut.chatJson("system", "user", emptyFormat()) }
        }
    }

    @Test
    fun `chatJson maps non-JSON assistant content to AiUnavailableException`() {
        runBlocking {
            // Arrange — valid envelope, but the model did not return JSON
            val sut = createSut(jsonEngine(body = chatResponse(content = "not json at all")))

            // Act + Assert
            shouldThrow<AiUnavailableException> { sut.chatJson("system", "user", emptyFormat()) }
        }
    }

    @Test
    fun `chatJson rejects assistant content that is a JSON array, not an object`() {
        runBlocking {
            // Arrange — parseable JSON, but the top-level element is an array
            val sut = createSut(jsonEngine(body = chatResponse(content = "[1, 2, 3]")))

            // Act + Assert
            shouldThrow<AiUnavailableException> { sut.chatJson("system", "user", emptyFormat()) }
        }
    }

    @Test
    fun `chatJson maps a transport failure to AiUnavailableException`() {
        runBlocking {
            // Arrange — the engine never answers
            val sut = createSut(MockEngine { throw IOException("connection refused") })

            // Act + Assert
            shouldThrow<AiUnavailableException> { sut.chatJson("system", "user", emptyFormat()) }
        }
    }

    private fun createSut(engine: MockEngine): OllamaClient =
        OllamaClient(
            config =
                AppConfig.AiConfig(
                    baseUrl = "http://ollama.test",
                    model = "gemma4:e4b",
                    timeoutSeconds = 30,
                    keepAlive = "5m",
                ),
            httpClient =
                HttpClient(engine) {
                    install(ContentNegotiation) {
                        json(Json { ignoreUnknownKeys = true })
                    }
                },
        )

    private fun jsonEngine(
        status: HttpStatusCode = HttpStatusCode.OK,
        body: String,
    ): MockEngine =
        MockEngine {
            respond(
                content = body,
                status = status,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }

    /** Wire body of `/api/chat` with [content] as the assistant message text. */
    private fun chatResponse(content: String): String =
        buildJsonObject {
            put("model", "gemma4:e4b")
            putJsonObject("message") {
                put("role", "assistant")
                put("content", content)
            }
            put("done", true)
        }.toString()

    private fun emptyFormat(): JsonObject = JsonObject(emptyMap())
}
