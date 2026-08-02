package com.vstorchevyi.skilky.data.remote

import com.vstorchevyi.skilky.api.ApiRoutes
import com.vstorchevyi.skilky.api.Currency
import com.vstorchevyi.skilky.api.ParseTextRequest
import com.vstorchevyi.skilky.api.ParseTextResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.timeout
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders

/** The authenticated AI parsing endpoints used by the shared client. */
internal class ParseApi(
    private val httpClient: HttpClient,
) {
    suspend fun parseText(request: ParseTextRequest): ParseTextResponse =
        httpClient
            .post(ApiRoutes.Parse.TEXT) { setBody(request) }
            .body()

    suspend fun parseAudio(
        bytes: ByteArray,
        currency: Currency,
    ): ParseTextResponse =
        httpClient
            .post(ApiRoutes.Parse.AUDIO) {
                timeout {
                    requestTimeoutMillis = AUDIO_REQUEST_TIMEOUT_MILLIS
                }
                setBody(
                    MultiPartFormDataContent(
                        formData {
                            append("currency", currency.code)
                            append(
                                "file",
                                bytes,
                                Headers.build {
                                    append(HttpHeaders.ContentType, AUDIO_WAV_CONTENT_TYPE)
                                    append(HttpHeaders.ContentDisposition, "filename=\"audio.wav\"")
                                },
                            )
                        },
                    ),
                )
            }
            .body()

    suspend fun parseReceipt(
        bytes: ByteArray,
        currency: Currency,
    ): ParseTextResponse {
        val isPng = bytes.isPng()
        val contentType = if (isPng) ContentType.Image.PNG else ContentType.Image.JPEG
        val fileName = if (isPng) "receipt.png" else "receipt.jpg"

        return httpClient
            .post(ApiRoutes.Parse.RECEIPT) {
                setBody(
                    MultiPartFormDataContent(
                        formData {
                            append("currency", currency.code)
                            append(
                                "file",
                                bytes,
                                Headers.build {
                                    append(HttpHeaders.ContentType, contentType.toString())
                                    append(HttpHeaders.ContentDisposition, "filename=\"$fileName\"")
                                },
                            )
                        },
                    ),
                )
            }
            .body()
    }

    private fun ByteArray.isPng(): Boolean =
        size >= PNG_SIGNATURE.size && PNG_SIGNATURE.indices.all { index -> this[index] == PNG_SIGNATURE[index] }

    private companion object {
        const val AUDIO_REQUEST_TIMEOUT_MILLIS = 180_000L
        const val AUDIO_WAV_CONTENT_TYPE = "audio/wav"
        val PNG_SIGNATURE = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
    }
}
