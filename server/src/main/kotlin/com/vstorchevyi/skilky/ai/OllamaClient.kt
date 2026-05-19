package com.vstorchevyi.skilky.ai

import com.vstorchevyi.skilky.config.AppConfig
import com.vstorchevyi.skilky.errors.AiUnavailableException
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.io.IOException
import java.util.Base64

/**
 * Thin wrapper over Ollama's `/api/chat` endpoint.
 *
 * Translates wire failures into [AiUnavailableException] so callers see
 * one typed error regardless of whether the model is missing, the
 * service is down, or the request timed out. The exception bubbles to
 * `StatusPages` and renders as `503 AI_UNAVAILABLE`.
 *
 * Owns its [HttpClient] and closes it on [close]. The `module()` wiring
 * subscribes the close to `ApplicationStopped` so the connection pool
 * shuts down cleanly on Ctrl-C and on test teardown.
 */
class OllamaClient(
    private val config: AppConfig.AiConfig,
    private val httpClient: HttpClient = defaultHttpClient(config),
) : AutoCloseable {
    /**
     * Returns the assistant message content as a parsed [JsonObject].
     * The caller asked Ollama for structured output via [responseFormat],
     * so the content is JSON; if it is not, we surface that as
     * [AiUnavailableException] (the model misbehaved).
     *
     * @param inputs optional binary blobs (WAV audio or image bytes) the
     *   model should consider alongside [userPrompt]. Base64-encoded
     *   into the request's `images` field — Ollama detects audio vs
     *   image by magic bytes. Empty for text-only calls.
     */
    suspend fun chatJson(
        systemPrompt: String,
        userPrompt: String,
        responseFormat: JsonObject,
        inputs: List<ByteArray> = emptyList(),
    ): JsonObject {
        val response = sendChat(systemPrompt, userPrompt, responseFormat, inputs)
        if (!response.status.isSuccessfulOllamaStatus()) {
            throw AiUnavailableException(
                "Ollama returned ${response.status.value}",
            )
        }
        val body =
            runCatching { response.body<OllamaChatResponse>() }
                .getOrElse { cause ->
                    throw AiUnavailableException("Ollama returned a malformed envelope: ${cause.message}", cause)
                }
        return runCatching { JSON.parseToJsonElement(body.message.content).jsonObjectOrThrow() }
            .getOrElse { cause ->
                throw AiUnavailableException("Ollama returned non-JSON content: ${cause.message}", cause)
            }
    }

    override fun close() {
        httpClient.close()
    }

    private suspend fun sendChat(
        systemPrompt: String,
        userPrompt: String,
        responseFormat: JsonObject,
        inputs: List<ByteArray>,
    ): HttpResponse =
        try {
            httpClient.post("${config.baseUrl.trimEnd('/')}/api/chat") {
                contentType(ContentType.Application.Json)
                setBody(
                    OllamaChatRequest(
                        model = config.model,
                        messages =
                            listOf(
                                OllamaMessage(role = "system", content = systemPrompt),
                                OllamaMessage(
                                    role = "user",
                                    content = userPrompt,
                                    images =
                                        if (inputs.isEmpty()) {
                                            null
                                        } else {
                                            inputs.map { Base64.getEncoder().encodeToString(it) }
                                        },
                                ),
                            ),
                        format = responseFormat,
                        stream = false,
                        options = GEMMA4_SAMPLING,
                        keepAlive = config.keepAlive,
                    ),
                )
            }
        } catch (cause: IOException) {
            throw AiUnavailableException("Cannot reach Ollama at ${config.baseUrl}: ${cause.message}", cause)
        } catch (cause: HttpRequestTimeoutException) {
            throw AiUnavailableException("Ollama timed out: ${cause.message}", cause)
        }

    private fun HttpStatusCode.isSuccessfulOllamaStatus(): Boolean = value in HTTP_OK..HTTP_OK_MAX

    private fun kotlinx.serialization.json.JsonElement.jsonObjectOrThrow(): JsonObject =
        this as? JsonObject
            ?: error("Top-level element is ${this::class.simpleName}, expected JsonObject")

    companion object {
        private const val HTTP_OK = 200
        private const val HTTP_OK_MAX = 299
        private const val MILLIS_PER_SECOND = 1000L

        // Google's recommended Gemma 4 sampling defaults
        // (https://ollama.com/library/gemma4). Promoted to config later
        // if other models need different values.
        private val GEMMA4_SAMPLING =
            OllamaOptions(
                temperature = 1.0,
                topP = 0.95,
                topK = 64,
            )

        internal val JSON: Json =
            Json {
                ignoreUnknownKeys = true
                explicitNulls = false
                // Required so `stream = false` on OllamaChatRequest makes it
                // into the wire body — without it, defaults are dropped and
                // Ollama falls back to streaming NDJSON.
                encodeDefaults = true
            }

        fun defaultHttpClient(config: AppConfig.AiConfig): HttpClient =
            HttpClient(CIO) {
                install(ContentNegotiation) { json(JSON) }
                install(HttpTimeout) {
                    val total = config.timeoutSeconds * MILLIS_PER_SECOND
                    requestTimeoutMillis = total
                    connectTimeoutMillis = total
                    socketTimeoutMillis = total
                }
            }
    }
}
