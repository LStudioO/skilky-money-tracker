package com.vstorchevyi.skilky.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.url
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/** The Ktor engine for the current platform: OkHttp, Darwin, or CIO. */
internal expect fun httpClientEngine(): HttpClientEngineFactory<*>

/**
 * Builds the shared HTTP client: JSON content negotiation, a request timeout,
 * and the base URL baked in so call sites pass only the route path.
 * `expectSuccess` makes a non-2xx response throw, which the repositories turn
 * into a typed [com.vstorchevyi.skilky.domain.model.AppError].
 */
internal fun createHttpClient(): HttpClient =
    HttpClient(httpClientEngine()) {
        expectSuccess = true
        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                    explicitNulls = false
                },
            )
        }
        install(HttpTimeout) {
            requestTimeoutMillis = REQUEST_TIMEOUT_MILLIS
        }
        defaultRequest {
            url(ApiConfig.BASE_URL)
            contentType(ContentType.Application.Json)
        }
    }

private const val REQUEST_TIMEOUT_MILLIS = 30_000L
