package com.vstorchevyi.skilky.routes

import com.vstorchevyi.skilky.ai.CategoryHint
import com.vstorchevyi.skilky.ai.OllamaClient
import com.vstorchevyi.skilky.ai.TextParsingService
import com.vstorchevyi.skilky.ai.TextParsingServiceOverrideKey
import com.vstorchevyi.skilky.api.ApiErrorResponse
import com.vstorchevyi.skilky.api.ApiRoutes
import com.vstorchevyi.skilky.api.Currency
import com.vstorchevyi.skilky.api.ParseTextRequest
import com.vstorchevyi.skilky.api.ParseTextResponse
import com.vstorchevyi.skilky.config.AppConfig
import com.vstorchevyi.skilky.module
import com.vstorchevyi.skilky.plugins.ParseRateLimitOverrideKey
import com.vstorchevyi.skilky.security.JwtTokenProvider
import com.vstorchevyi.skilky.support.aJwtConfig
import com.vstorchevyi.skilky.support.aWavHeader
import com.vstorchevyi.skilky.support.jsonClient
import com.vstorchevyi.skilky.support.useTestConfig
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.ktor.utils.io.ByteReadChannel
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlin.test.Test

/**
 * Route-level tests for `POST /api/v1/parse/text`.
 *
 * The Ollama backend is replaced by a [MockEngine]-backed
 * [TextParsingService] installed via [TextParsingServiceOverrideKey].
 * The category loader is stubbed in the override too, so the route runs
 * without a real Postgres.
 */
class ParseRoutesIntegrationTest {
    @Test
    fun `unauthenticated request is 401`() =
        runParseTest(installFakeService(stubItemsJson())) { sut ->
            val response =
                sut.post(ApiRoutes.Parse.TEXT) {
                    contentType(ContentType.Application.Json)
                    setBody(ParseTextRequest(text = "milk 45", currency = Currency.UAH))
                }

            response.status shouldBe HttpStatusCode.Unauthorized
        }

    @Test
    fun `authenticated happy path returns parsed items with resolved ids`() =
        runParseTest(installFakeService(stubItemsJson())) { sut ->
            // Act
            val response = sut.parseText("milk 45, gym 500", token = aValidToken())

            // Assert
            response.status shouldBe HttpStatusCode.OK
            val body = response.body<ParseTextResponse>()
            body.items shouldHaveSize 2
            body.items[0].name shouldBe "Milk"
            body.items[0].suggestedCategoryId shouldBe ID_FOOD
            body.items[0].suggestedCategoryName shouldBe "Food"
            body.items[1].suggestedCategoryId shouldBe ID_GYM
            body.items[1].suggestedCategoryName shouldBe "Gym"
            body.items[0].currency shouldBe Currency.UAH
        }

    @Test
    fun `blank text is 422`() =
        runParseTest(installFakeService(stubItemsJson())) { sut ->
            val response = sut.parseText("   ", token = aValidToken())

            response.status shouldBe HttpStatusCode.UnprocessableEntity
            response.body<ApiErrorResponse>().error.code shouldBe "VALIDATION_ERROR"
        }

    @Test
    fun `Ollama down surfaces as 503 AI_UNAVAILABLE`() =
        runParseTest(installFakeService(MockEngine { respond("nope", HttpStatusCode.BadGateway) })) { sut ->
            val response = sut.parseText("milk 45", token = aValidToken())

            response.status shouldBe HttpStatusCode.ServiceUnavailable
            response.body<ApiErrorResponse>().error.code shouldBe "AI_UNAVAILABLE"
        }

    @Test
    fun `parse audio happy path returns parsed items and transcript`() =
        runParseTest(installFakeService(stubAudioJson())) { sut ->
            val response = sut.parseAudio(aWavHeader() + ByteArray(32), token = aValidToken())

            response.status shouldBe HttpStatusCode.OK
            val body = response.body<ParseTextResponse>()
            body.items shouldHaveSize 1
            body.items.single().suggestedCategoryId shouldBe ID_FOOD
            body.transcript.shouldNotBeNull()
        }

    @Test
    fun `parse audio rejects a non-WAV upload with 422`() =
        runParseTest(installFakeService(stubAudioJson())) { sut ->
            val response = sut.parseAudio("not actually audio".toByteArray(), token = aValidToken())

            response.status shouldBe HttpStatusCode.UnprocessableEntity
            response.body<ApiErrorResponse>().error.code shouldBe "VALIDATION_ERROR"
        }

    @Test
    fun `parse audio unauthenticated is 401`() =
        runParseTest(installFakeService(stubAudioJson())) { sut ->
            val response = sut.parseAudio(aWavHeader() + ByteArray(32), token = null)

            response.status shouldBe HttpStatusCode.Unauthorized
        }

    @Test
    fun `parse receipt happy path returns parsed items plus rawText`() =
        runParseTest(installFakeService(stubReceiptJson())) { sut ->
            val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()) + ByteArray(32)

            val response = sut.parseReceipt(jpeg, token = aValidToken())

            response.status shouldBe HttpStatusCode.OK
            val body = response.body<ParseTextResponse>()
            body.items shouldHaveSize 2
            body.rawText.shouldNotBeNull()
            body.transcript shouldBe null
        }

    @Test
    fun `parse receipt rejects a PDF with 422`() =
        runParseTest(installFakeService(stubReceiptJson())) { sut ->
            val pdf = "%PDF-1.4 fake".toByteArray() + ByteArray(32)

            val response = sut.parseReceipt(pdf, token = aValidToken())

            response.status shouldBe HttpStatusCode.UnprocessableEntity
        }

    @Test
    fun `rate limit returns 429 after the per-user budget is exhausted`() =
        testApplication {
            useTestConfig()
            // 2 requests per minute, the 3rd fired in quick succession is 429.
            application { attributes.put(ParseRateLimitOverrideKey, 2) }
            installFakeService(stubItemsJson())()
            application { module() }
            val sut = jsonClient()
            val token = aValidToken()

            val first = sut.parseText("milk 45", token = token)
            val second = sut.parseText("milk 45", token = token)
            val third = sut.parseText("milk 45", token = token)

            first.status shouldBe HttpStatusCode.OK
            second.status shouldBe HttpStatusCode.OK
            third.status shouldBe HttpStatusCode.TooManyRequests
        }

    @Test
    fun `parse route is not registered when AI override is absent and config has no ai block`() =
        testApplication {
            // No override, no skilky.ai.* keys → service is null → route absent → 404.
            useTestConfig()
            application { module() }
            val sut = jsonClient()

            val response = sut.parseText("milk 45", token = aValidToken())

            response.status shouldBe HttpStatusCode.NotFound
            response.body<ApiErrorResponse>().error.code shouldBe "NOT_FOUND"
        }

    // --- test scaffolding -------------------------------------------------

    private fun runParseTest(
        installFakeService: ApplicationTestBuilder.() -> Unit,
        block: suspend ApplicationTestBuilder.(HttpClient) -> Unit,
    ) = testApplication {
        useTestConfig()
        installFakeService()
        application { module() }
        block(jsonClient())
    }

    private fun installFakeService(engine: MockEngine): ApplicationTestBuilder.() -> Unit =
        {
            application {
                val httpClient =
                    HttpClient(engine) {
                        install(ContentNegotiation) {
                            json(
                                Json {
                                    ignoreUnknownKeys = true
                                    explicitNulls = false
                                },
                            )
                        }
                    }
                val client =
                    OllamaClient(
                        config = AI_CONFIG,
                        httpClient = httpClient,
                    )
                val service =
                    TextParsingService(
                        ollamaClient = client,
                        loadCategories = { _ -> STUB_CATEGORIES },
                    )
                attributes.put(TextParsingServiceOverrideKey, service)
            }
        }

    private fun stubItemsJson(): MockEngine {
        val content =
            """{"items":[
              {"name":"Milk","amount":45.0,"suggestedCategoryName":"Food","confidence":0.95},
              {"name":"Gym","amount":500.0,"suggestedCategoryName":"Gym","confidence":0.9}
            ]}"""
        val envelope =
            buildString {
                append("""{"model":"gemma4:e4b","message":{"role":"assistant","content":""")
                append(Json.encodeToString(String.serializer(), content))
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

    private fun aValidToken(): String {
        val provider = JwtTokenProvider(aJwtConfig())
        return provider.createAccessToken(userId = 1L, email = "vlad@example.com")
    }

    private suspend fun HttpClient.parseText(
        text: String,
        currency: Currency = Currency.UAH,
        token: String? = null,
    ): HttpResponse =
        post(ApiRoutes.Parse.TEXT) {
            contentType(ContentType.Application.Json)
            if (token != null) bearerAuth(token)
            setBody(ParseTextRequest(text = text, currency = currency))
        }

    private suspend fun HttpClient.parseAudio(
        bytes: ByteArray,
        currency: Currency = Currency.UAH,
        token: String? = null,
    ): HttpResponse =
        post(ApiRoutes.Parse.AUDIO) {
            if (token != null) bearerAuth(token)
            setBody(multipart(bytes, contentType = "audio/wav", filename = "audio.wav", currency = currency))
        }

    private suspend fun HttpClient.parseReceipt(
        bytes: ByteArray,
        currency: Currency = Currency.UAH,
        token: String? = null,
    ): HttpResponse =
        post(ApiRoutes.Parse.RECEIPT) {
            if (token != null) bearerAuth(token)
            setBody(multipart(bytes, contentType = "image/jpeg", filename = "receipt.jpg", currency = currency))
        }

    private fun multipart(
        bytes: ByteArray,
        contentType: String,
        filename: String,
        currency: Currency,
    ) = MultiPartFormDataContent(
        formData {
            append(
                "file",
                bytes,
                Headers.build {
                    append(HttpHeaders.ContentType, contentType)
                    append(HttpHeaders.ContentDisposition, "filename=\"$filename\"")
                },
            )
            append("currency", currency.code)
        },
    )

    private fun stubAudioJson(): MockEngine =
        ollamaEnvelope(
            """{
                "transcript":"milk forty five",
                "items":[
                  {"name":"Milk","amount":45.0,"suggestedCategoryName":"Food","confidence":0.9}
                ]
            }""",
        )

    private fun stubReceiptJson(): MockEngine =
        ollamaEnvelope(
            """{
                "items":[
                  {"name":"Coffee","amount":35.0,"suggestedCategoryName":"Food","confidence":0.92},
                  {"name":"Croissant","amount":42.0,"suggestedCategoryName":"Food","confidence":0.9}
                ],
                "rawText":"COFFEE 35.00\nCROISSANT 42.00\nTOTAL 77.00"
            }""",
        )

    private fun ollamaEnvelope(messageContent: String): MockEngine {
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

    companion object {
        private const val ID_FOOD = 1L
        private const val ID_GYM = 42L

        private val AI_CONFIG =
            AppConfig.AiConfig(
                baseUrl = "http://ollama.test",
                model = "gemma4:e4b",
                timeoutSeconds = 5,
                keepAlive = "30m",
            )

        private val STUB_CATEGORIES =
            listOf(
                CategoryHint(id = ID_FOOD, name = "Food"),
                CategoryHint(id = 2L, name = "Transport"),
                CategoryHint(id = ID_GYM, name = "Gym"),
            )
    }
}
