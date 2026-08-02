package com.vstorchevyi.skilky.data.remote

import com.vstorchevyi.skilky.api.ApiRoutes
import com.vstorchevyi.skilky.api.Currency
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.url
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ParseApiTest {
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
            val client =
                HttpClient(engine) {
                    install(ContentNegotiation) {
                        json(Json { ignoreUnknownKeys = true })
                    }
                    defaultRequest { url("http://localhost") }
                }

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

    private fun ByteArray.containsSequence(sequence: ByteArray): Boolean =
        indices.any { start ->
            start + sequence.size <= size && sequence.indices.all { offset -> this[start + offset] == sequence[offset] }
        }
}
