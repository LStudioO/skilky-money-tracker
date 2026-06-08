package com.vstorchevyi.skilky.data.remote

import com.vstorchevyi.skilky.api.ApiRoutes
import com.vstorchevyi.skilky.api.AuthResponse
import com.vstorchevyi.skilky.api.RefreshRequest
import com.vstorchevyi.skilky.data.local.TokenStorage
import com.vstorchevyi.skilky.data.mapper.toDomain
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.AuthCircuitBreaker
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json

/** The Ktor engine for the current platform: OkHttp, Darwin, or CIO. */
internal expect fun httpClientEngine(): HttpClientEngineFactory<*>

/**
 * Builds the shared HTTP client for production: the platform engine plus
 * [skilkyClientConfig]. Tests that want to drive the same config from a
 * MockEngine call `skilkyClientConfig` directly.
 */
internal fun createHttpClient(
    tokenStorage: TokenStorage,
    sessionEvents: SessionEvents,
): HttpClient =
    HttpClient(httpClientEngine()) {
        skilkyClientConfig(tokenStorage, sessionEvents)
    }

/**
 * Installs JSON content negotiation, a request timeout, the base URL, and
 * Ktor's Bearer auth plugin (proactive attach + 401 rotation backed by
 * [tokenStorage]). `expectSuccess` makes non-2xx responses throw so the
 * repository layer can map them to [com.vstorchevyi.skilky.domain.model.AppError].
 * The 401 path still goes through the Auth plugin first, which gets one
 * refresh attempt before the failure propagates.
 */
internal fun HttpClientConfig<*>.skilkyClientConfig(
    tokenStorage: TokenStorage,
    sessionEvents: SessionEvents,
) {
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
    install(Auth) {
        bearer {
            loadTokens {
                tokenStorage.read()?.let { session ->
                    BearerTokens(session.accessToken, session.refreshToken)
                }
            }
            refreshTokens {
                refresh(tokenStorage, sessionEvents, client)
            }
            sendWithoutRequest { request ->
                // Login, register, and refresh themselves carry no Bearer
                // header. Every other path goes out with one attached.
                !request.url.build().encodedPath.startsWith(AUTH_PATH_PREFIX)
            }
        }
    }
    defaultRequest {
        url(ApiConfig.BASE_URL)
        contentType(ContentType.Application.Json)
    }
}

private suspend fun refresh(
    tokenStorage: TokenStorage,
    sessionEvents: SessionEvents,
    client: HttpClient,
): BearerTokens? {
    val current = tokenStorage.read()
    if (current == null) {
        sessionEvents.emitSignedOut()
        return null
    }
    return try {
        val response =
            client.post(ApiRoutes.Auth.REFRESH) {
                markAsRefreshRequest()
                setBody(RefreshRequest(current.refreshToken))
            }
        val rotated = response.body<AuthResponse>().toDomain()
        tokenStorage.save(rotated)
        BearerTokens(rotated.accessToken, rotated.refreshToken)
    } catch (e: CancellationException) {
        throw e
    } catch (
        @Suppress("TooGenericExceptionCaught", "SwallowedException") e: Exception,
    ) {
        // Refresh itself failed (server rejected the token, the network
        // dropped, the JSON didn't parse). Drop the persisted session and
        // signal the UI; the auth plugin will let the original 401 propagate.
        tokenStorage.clear()
        sessionEvents.emitSignedOut()
        null
    }
}

/**
 * Tags an outgoing request so Ktor's Auth plugin will not try to re-add a
 * Bearer header to it. Wrapping the attribute write keeps the import burden
 * local to this file.
 */
private fun HttpRequestBuilder.markAsRefreshRequest() {
    attributes.put(AuthCircuitBreaker, Unit)
}

private const val REQUEST_TIMEOUT_MILLIS = 30_000L
private const val AUTH_PATH_PREFIX = "${ApiRoutes.BASE}/auth/"
