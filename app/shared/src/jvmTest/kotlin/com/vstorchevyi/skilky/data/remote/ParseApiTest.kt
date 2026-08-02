package com.vstorchevyi.skilky.data.remote

import com.vstorchevyi.skilky.api.ApiRoutes
import com.vstorchevyi.skilky.api.Currency
import com.vstorchevyi.skilky.api.ParseTextRequest
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.plugins.HttpTimeoutCapability
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.url
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ParseApiTest {
    @Test
    fun `text parse uses an extended timeout`() =
        runTest {
            // Arrange
            var requestTimeoutMillis: Long? = null
            var socketTimeoutMillis: Long? = null
            val engine =
                MockEngine { request ->
                    val timeout = request.getCapabilityOrNull(HttpTimeoutCapability)
                    requestTimeoutMillis = timeout?.requestTimeoutMillis
                    socketTimeoutMillis = timeout?.socketTimeoutMillis
                    respond(
                        content =
                            """
                            {
                              "items": [{"name": "Pie", "amount": 15.55, "currency": "UAH"}]
                            }
                            """.trimIndent(),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            val client = testClient(engine)

            // Act
            val result =
                ParseApi(client).parseText(
                    ParseTextRequest(text = "Pie for 15.55 UAH", currency = Currency.UAH),
                )

            // Assert
            assertEquals(90_000L, requestTimeoutMillis)
            assertEquals(90_000L, socketTimeoutMillis)
            assertEquals("Pie", result.items.single().name)
        }

    @Test
    fun `audio parse uploads wav as multipart form data`() =
        runTest {
            // Arrange
            val audio = wavBytes()
            var requestMethod: HttpMethod? = null
            var requestPath = ""
            var requestContentType = ""
            var requestBody = byteArrayOf()
            var requestTimeoutMillis: Long? = null
            var socketTimeoutMillis: Long? = null
            val engine =
                MockEngine { request ->
                    requestMethod = request.method
                    requestPath = request.url.encodedPath
                    requestContentType = requireNotNull(request.body.contentType).toString()
                    requestBody = request.body.toByteArray()
                    val timeout = request.getCapabilityOrNull(HttpTimeoutCapability)
                    requestTimeoutMillis = timeout?.requestTimeoutMillis
                    socketTimeoutMillis = timeout?.socketTimeoutMillis
                    respond(
                        content =
                            """
                            {
                              "items": [{"name": "Taxi", "amount": 120.0, "currency": "UAH"}],
                              "transcript": "taxi 120"
                            }
                            """.trimIndent(),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            val client = testClient(engine)

            // Act
            val result = ParseApi(client).parseAudio(audio, Currency.UAH)

            // Assert
            val bodyText = requestBody.toString(Charsets.ISO_8859_1)
            assertEquals(HttpMethod.Post, requestMethod)
            assertEquals(ApiRoutes.Parse.AUDIO, requestPath)
            assertTrue(requestContentType.startsWith("multipart/form-data"))
            assertTrue(bodyText.contains("name=currency"))
            assertTrue(bodyText.contains("UAH"))
            assertTrue(bodyText.contains("filename=\"audio.wav\""))
            assertTrue(bodyText.contains("Content-Type: audio/wav"))
            assertTrue(requestBody.containsSequence(audio))
            assertEquals(180_000L, requestTimeoutMillis)
            assertEquals(180_000L, socketTimeoutMillis)
            assertEquals("Taxi", result.items.single().name)
            assertEquals("taxi 120", result.transcript)
        }

    @Test
    fun `receipt parse uploads png as multipart form data`() =
        runTest {
            // Arrange
            val image = pngBytes()
            var requestMethod: HttpMethod? = null
            var requestPath = ""
            var requestContentType = ""
            var requestBody = byteArrayOf()
            val engine =
                MockEngine { request ->
                    requestMethod = request.method
                    requestPath = request.url.encodedPath
                    requestContentType = requireNotNull(request.body.contentType).toString()
                    requestBody = request.body.toByteArray()
                    respond(
                        content =
                            """
                            {
                              "items": [{"name": "Milk", "amount": 45.0, "currency": "UAH"}],
                              "rawText": "MILK 45.00"
                            }
                            """.trimIndent(),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            val client = testClient(engine)

            // Act
            val result = ParseApi(client).parseReceipt(image, Currency.UAH)

            // Assert
            val bodyText = requestBody.toString(Charsets.ISO_8859_1)
            assertEquals(HttpMethod.Post, requestMethod)
            assertEquals(ApiRoutes.Parse.RECEIPT, requestPath)
            assertTrue(requestContentType.startsWith("multipart/form-data"))
            assertTrue(bodyText.contains("name=currency"))
            assertTrue(bodyText.contains("UAH"))
            assertTrue(bodyText.contains("filename=\"receipt.png\""))
            assertTrue(bodyText.contains("Content-Type: image/png"))
            assertTrue(requestBody.containsSequence(image))
            assertEquals("Milk", result.items.single().name)
            assertEquals("MILK 45.00", result.rawText)
        }

    private fun pngBytes(): ByteArray = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3)

    private fun wavBytes(): ByteArray =
        byteArrayOf(
            0x52,
            0x49,
            0x46,
            0x46,
            0,
            0,
            0,
            0,
            0x57,
            0x41,
            0x56,
            0x45,
            1,
            2,
            3,
        )

    private fun testClient(engine: MockEngine): HttpClient =
        HttpClient(engine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
            defaultRequest {
                url("http://localhost")
                contentType(ContentType.Application.Json)
            }
        }

    private fun ByteArray.containsSequence(sequence: ByteArray): Boolean =
        indices.any { start ->
            start + sequence.size <= size && sequence.indices.all { offset -> this[start + offset] == sequence[offset] }
        }
}
