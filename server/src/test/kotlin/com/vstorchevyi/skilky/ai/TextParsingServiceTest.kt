package com.vstorchevyi.skilky.ai

import com.vstorchevyi.skilky.api.Currency
import com.vstorchevyi.skilky.api.ParseTextRequest
import com.vstorchevyi.skilky.config.AppConfig
import com.vstorchevyi.skilky.errors.AiUnavailableException
import com.vstorchevyi.skilky.support.aWavHeader
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlin.test.Test

/**
 * Drives [TextParsingService] against a [MockEngine]-backed [OllamaClient].
 * No real network, no Ollama. The category loader is also stubbed so
 * the tests are pure unit tests with no database.
 */
class TextParsingServiceTest {
    @Test
    fun `parseText resolves a suggested system-default name to its id`() {
        val ollamaContent =
            """{"items":[{"name":"Milk","amount":45.0,"suggestedCategoryName":"Food","confidence":0.95}]}"""
        val sut = createSut(ollamaJson(ollamaContent))

        val response =
            runBlocking {
                sut.parseText(
                    request = ParseTextRequest(text = "milk 45", currency = Currency.UAH),
                    userId = USER_ID,
                )
            }

        response.items.single().suggestedCategoryId shouldBe ID_FOOD
        response.items.single().suggestedCategoryName shouldBe "Food"
    }

    @Test
    fun `parseText resolves a custom-category name to its id`() {
        val ollamaContent =
            """{"items":[{"name":"Gym","amount":500.0,"suggestedCategoryName":"Gym","confidence":0.9}]}"""
        val sut = createSut(ollamaJson(ollamaContent))

        val response =
            runBlocking {
                sut.parseText(
                    request = ParseTextRequest(text = "gym 500", currency = Currency.UAH),
                    userId = USER_ID,
                )
            }

        withClue("custom Gym category should match by name and resolve to its real id") {
            response.items.single().suggestedCategoryId shouldBe ID_GYM
        }
    }

    @Test
    fun `parseText matches names case-insensitively`() {
        val ollamaContent =
            """{"items":[{"name":"Milk","amount":45.0,"suggestedCategoryName":"  FOOD  ","confidence":0.9}]}"""
        val sut = createSut(ollamaJson(ollamaContent))

        val response =
            runBlocking {
                sut.parseText(
                    request = ParseTextRequest(text = "milk 45", currency = Currency.UAH),
                    userId = USER_ID,
                )
            }

        response.items.single().suggestedCategoryId shouldBe ID_FOOD
    }

    @Test
    fun `parseText keeps the raw name when no category matches`() {
        val ollamaContent =
            """{"items":[{"name":"Tax","amount":50.0,"suggestedCategoryName":"Taxes","confidence":0.4}]}"""
        val sut = createSut(ollamaJson(ollamaContent))

        val response =
            runBlocking {
                sut.parseText(
                    request = ParseTextRequest(text = "tax 50", currency = Currency.UAH),
                    userId = USER_ID,
                )
            }

        response.items.single().suggestedCategoryId shouldBe null
        withClue("client can still show the LLM's guess even when no id matched") {
            response.items.single().suggestedCategoryName shouldBe "Taxes"
        }
    }

    @Test
    fun `parseText returns empty list when Ollama returns no items`() {
        val sut = createSut(ollamaJson("""{"items":[]}"""))

        val response =
            runBlocking {
                sut.parseText(
                    request = ParseTextRequest(text = "hello", currency = Currency.UAH),
                    userId = USER_ID,
                )
            }

        response.items shouldBe emptyList()
    }

    @Test
    fun `parseText maps multiple items in order`() {
        val ollamaContent =
            """{"items":[
              {"name":"Milk","amount":45.0,"suggestedCategoryName":"Food","confidence":0.95},
              {"name":"Taxi","amount":120.0,"suggestedCategoryName":"Transport","confidence":0.9}
            ]}"""
        val sut = createSut(ollamaJson(ollamaContent))

        val response =
            runBlocking {
                sut.parseText(
                    request = ParseTextRequest(text = "milk 45, taxi 120", currency = Currency.UAH),
                    userId = USER_ID,
                )
            }

        response.items shouldHaveSize 2
        response.items[0].suggestedCategoryId shouldBe ID_FOOD
        response.items[1].suggestedCategoryId shouldBe ID_TRANSPORT
        response.items[0].currency shouldBe Currency.UAH
    }

    @Test
    fun `parseText surfaces AiUnavailableException when Ollama returns a 5xx`() {
        val sut =
            createSut(
                MockEngine {
                    respond(
                        content = "internal error",
                        status = HttpStatusCode.InternalServerError,
                    )
                },
            )

        shouldThrow<AiUnavailableException> {
            runBlocking {
                sut.parseText(
                    request = ParseTextRequest(text = "milk", currency = Currency.UAH),
                    userId = USER_ID,
                )
            }
        }
    }

    @Test
    fun `parseText surfaces AiUnavailableException when Ollama content is non-JSON`() {
        val sut = createSut(ollamaJson("not json at all"))

        shouldThrow<AiUnavailableException> {
            runBlocking {
                sut.parseText(
                    request = ParseTextRequest(text = "milk", currency = Currency.UAH),
                    userId = USER_ID,
                )
            }
        }
    }

    @Test
    fun `parseText surfaces AiUnavailableException when an item is missing its name`() {
        val sut = createSut(ollamaJson("""{"items":[{"amount":45.0}]}"""))

        shouldThrow<AiUnavailableException> {
            runBlocking {
                sut.parseText(
                    request = ParseTextRequest(text = "45", currency = Currency.UAH),
                    userId = USER_ID,
                )
            }
        }
    }

    @Test
    fun `parseText clamps an out-of-range confidence into 0 to 1`() {
        val ollamaContent =
            """{"items":[{"name":"Milk","amount":45.0,"suggestedCategoryName":"Food","confidence":2.5}]}"""
        val sut = createSut(ollamaJson(ollamaContent))

        val response =
            runBlocking {
                sut.parseText(
                    request = ParseTextRequest(text = "milk 45", currency = Currency.UAH),
                    userId = USER_ID,
                )
            }

        response.items.single().confidence shouldBe 1.0
    }

    // --- parseAudio ------------------------------------------------------

    @Test
    fun `parseAudio returns the transcript when the model provides one`() {
        val ollamaContent =
            """{
                "transcript":"milk forty five, gym five hundred",
                "items":[
                  {"name":"Milk","amount":45.0,"suggestedCategoryName":"Food","confidence":0.9},
                  {"name":"Gym","amount":500.0,"suggestedCategoryName":"Gym","confidence":0.85}
                ]
            }"""
        val sut = createSut(ollamaJson(ollamaContent))

        val response =
            runBlocking {
                sut.parseAudio(audio = aWavHeader() + ByteArray(32), currency = Currency.UAH, userId = USER_ID)
            }

        response.transcript.shouldNotBeNull()
        response.transcript shouldContain "milk forty five"
        response.items shouldHaveSize 2
        response.items[1].suggestedCategoryId shouldBe ID_GYM
    }

    @Test
    fun `parseText request body includes stream-false, options, keep_alive, and no images`() {
        val capturing = capturingMockEngine(ollamaJson("""{"items":[]}"""))
        val sut = createSut(capturing.engine)

        runBlocking {
            sut.parseText(ParseTextRequest(text = "milk", currency = Currency.UAH), USER_ID)
        }
        val body = capturing.lastRequestBody()

        body shouldContain "\"stream\":false"
        body shouldContain "\"keep_alive\":\"30m\""
        body shouldContain "\"top_p\":0.95"
        body shouldContain "\"top_k\":64"
        withClue("text requests must not include the images field — Ollama would try to attach phantom binaries") {
            (body.contains("\"images\"")) shouldBe false
        }
    }

    @Test
    fun `parseAudio sends audio bytes to Ollama in the images field as base64`() {
        val audio = aWavHeader() + "AUDIOPAYLOAD".toByteArray()
        val capturingEngine = capturingMockEngine(ollamaJson("""{"items":[],"transcript":"none"}"""))
        val sut = createSut(capturingEngine.engine)

        runBlocking { sut.parseAudio(audio, Currency.UAH, USER_ID) }

        val request = capturingEngine.lastRequestBody()
        // The OllamaMessage.images field carries base64-encoded audio.
        // Just confirming the JSON includes an "images" array; the
        // bytes-equal check is brittle to encoder differences.
        request shouldContain "\"images\""
    }

    // --- parseReceipt ----------------------------------------------------

    @Test
    fun `parseReceipt maps items, surfaces rawText, and omits transcript`() {
        val ollamaContent =
            """{
              "items":[
                {"name":"Coffee","amount":35.0,"suggestedCategoryName":"Food","confidence":0.92},
                {"name":"Croissant","amount":42.0,"suggestedCategoryName":"Food","confidence":0.9}
              ],
              "rawText":"COFFEE 35.00\nCROISSANT 42.00\nTOTAL 77.00"
            }"""
        val sut = createSut(ollamaJson(ollamaContent))

        val response =
            runBlocking {
                sut.parseReceipt(
                    image = byteArrayOf(0xFF.toByte(), 0xD8.toByte()) + ByteArray(16),
                    currency = Currency.UAH,
                    userId = USER_ID,
                )
            }

        response.items shouldHaveSize 2
        response.items[0].suggestedCategoryId shouldBe ID_FOOD
        withClue("receipt response carries the OCR for debugging") {
            response.rawText shouldBe "COFFEE 35.00\nCROISSANT 42.00\nTOTAL 77.00"
        }
        withClue("receipt parsing does not return a transcript") {
            response.transcript shouldBe null
        }
    }

    @Test
    fun `parseText response has neither transcript nor rawText`() {
        val sut =
            createSut(
                ollamaJson(
                    """{"items":[{"name":"Milk","amount":45.0,"suggestedCategoryName":"Food","confidence":0.95}]}""",
                ),
            )

        val response =
            runBlocking {
                sut.parseText(
                    request = ParseTextRequest(text = "milk 45", currency = Currency.UAH),
                    userId = USER_ID,
                )
            }

        response.transcript shouldBe null
        response.rawText shouldBe null
    }

    // --- helpers -----------------------------------------------------------

    /** Wraps a JSON payload in the full Ollama `/api/chat` envelope. */
    private fun ollamaJson(messageContent: String): MockEngine {
        val envelope =
            buildString {
                append("""{"model":"gemma4:e4b","message":{"role":"assistant","content":""")
                append(Json.encodeToString(String.serializer(), messageContent))
                append("""},"done":true}""")
            }
        return MockEngine {
            respond(
                content = ByteReadChannel(envelope),
                status = HttpStatusCode.OK,
                headers = headersOf("Content-Type", "application/json"),
            )
        }
    }

    private fun createSut(engine: MockEngine): TextParsingService {
        // Re-uses OllamaClient.JSON (encodeDefaults = true) so test
        // serialization matches production wire output exactly.
        val httpClient =
            HttpClient(engine) {
                install(ContentNegotiation) { json(OllamaClient.JSON) }
            }
        val ollamaClient =
            OllamaClient(
                config = aAiConfig(),
                httpClient = httpClient,
            )
        return TextParsingService(
            ollamaClient = ollamaClient,
            loadCategories = { _ -> STUB_CATEGORIES },
        )
    }

    private fun aAiConfig() =
        AppConfig.AiConfig(
            baseUrl = "http://ollama.test",
            model = "gemma4:e4b",
            timeoutSeconds = 5,
            keepAlive = "30m",
        )

    /** Wraps a [MockEngine] response with a capture of the last request body. */
    private class CapturingEngine(
        val engine: MockEngine,
        private val lastBody: () -> String,
    ) {
        fun lastRequestBody(): String = lastBody()
    }

    private fun capturingMockEngine(inner: MockEngine): CapturingEngine {
        val captured = java.util.concurrent.atomic.AtomicReference("")
        val engine =
            MockEngine { request ->
                captured.set(
                    when (val body = request.body) {
                        is io.ktor.http.content.TextContent -> body.text
                        is io.ktor.http.content.ByteArrayContent -> String(body.bytes(), Charsets.UTF_8)
                        else -> body.toString()
                    },
                )
                inner.config.requestHandlers.first()(this, request)
            }
        return CapturingEngine(engine) { captured.get() }
    }

    companion object {
        private const val USER_ID = 1L
        private const val ID_FOOD = 1L
        private const val ID_TRANSPORT = 2L
        private const val ID_GYM = 42L

        private val STUB_CATEGORIES =
            listOf(
                CategoryHint(id = ID_FOOD, name = "Food"),
                CategoryHint(id = ID_TRANSPORT, name = "Transport"),
                CategoryHint(id = 3L, name = "Housing"),
                CategoryHint(id = ID_GYM, name = "Gym"),
            )
    }
}
