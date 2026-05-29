package com.vstorchevyi.skilky.data.remote

import com.vstorchevyi.skilky.api.ApiRoutes
import com.vstorchevyi.skilky.data.local.TokenStorage
import com.vstorchevyi.skilky.domain.model.AuthSession
import com.vstorchevyi.skilky.domain.model.User
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.request.HttpResponseData
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val PROTECTED_PATH = "${ApiRoutes.BASE}/expenses"

class AuthBearerPluginTest {
    @Test
    fun `401 with WWW-Authenticate triggers a refresh and retry with the rotated token`() =
        runTest {
            // Arrange
            val storage = FakeTokenStorage(initial = anAuthSession())
            val events = SessionEvents()
            val recordedHeaders = mutableListOf<String?>()
            val engine =
                MockEngine { request ->
                    when (request.url.encodedPath) {
                        ApiRoutes.Auth.REFRESH -> {
                            respondJson(
                                """
                                {"token":"new-access","refreshToken":"new-refresh",
                                 "user":{"id":1,"email":"v@example.com","displayName":"V","defaultCurrency":"UAH"}}
                                """.trimIndent(),
                            )
                        }

                        PROTECTED_PATH -> {
                            val auth = request.headers[HttpHeaders.Authorization]
                            recordedHeaders += auth
                            if (auth == "Bearer new-access") {
                                respondJson("[]")
                            } else {
                                respondUnauthorizedBearer()
                            }
                        }

                        else -> {
                            respondError(HttpStatusCode.NotFound)
                        }
                    }
                }
            val client = createTestClient(engine, storage, events)

            // Act
            val status = client.get(PROTECTED_PATH).status

            // Assert
            assertEquals(HttpStatusCode.OK, status)
            assertEquals(listOf<String?>("Bearer old-access", "Bearer new-access"), recordedHeaders)
            val refreshed = storage.read()
            assertNotNull(refreshed)
            assertEquals("new-access", refreshed.accessToken)
            assertEquals("new-refresh", refreshed.refreshToken)
        }

    @Test
    fun `failed refresh clears the storage and emits SignedOut`() =
        runTest {
            // Arrange
            val storage = FakeTokenStorage(initial = anAuthSession())
            val events = SessionEvents()
            val engine =
                MockEngine { request ->
                    when (request.url.encodedPath) {
                        ApiRoutes.Auth.REFRESH -> {
                            respond(
                                content = ByteReadChannel(""),
                                status = HttpStatusCode.Unauthorized,
                            )
                        }

                        PROTECTED_PATH -> {
                            respondUnauthorizedBearer()
                        }

                        else -> {
                            respondError(HttpStatusCode.NotFound)
                        }
                    }
                }
            val client = createTestClient(engine, storage, events)

            // Subscribe first: SessionEvents has replay=0, so a late subscriber
            // would miss the emission that the refresher is about to make.
            val signedOut = async(Dispatchers.Unconfined) { events.signedOut.first() }
            yield()

            // Act
            assertFailsWith<ClientRequestException> { client.get(PROTECTED_PATH) }

            // Assert
            assertNull(storage.read())
            withTimeout(1_000) { signedOut.await() }
        }

    @Test
    fun `auth endpoints go out without a Bearer header`() =
        runTest {
            // Arrange
            val storage = FakeTokenStorage(initial = anAuthSession())
            val events = SessionEvents()
            val authHeaders = mutableListOf<String?>()
            val engine =
                MockEngine { request ->
                    authHeaders += request.headers[HttpHeaders.Authorization]
                    respondJson("{}")
                }
            val client = createTestClient(engine, storage, events)

            // Act
            client.get(ApiRoutes.Auth.LOGIN)
            client.get(ApiRoutes.Auth.REGISTER)

            // Assert
            assertTrue(authHeaders.all { it == null }, "Auth endpoints must not carry a Bearer header, got $authHeaders")
        }

    private fun createTestClient(
        engine: MockEngine,
        storage: TokenStorage,
        events: SessionEvents,
    ): HttpClient =
        HttpClient(engine) {
            skilkyClientConfig(tokenStorage = storage, sessionEvents = events)
        }

    private fun anAuthSession(): AuthSession =
        AuthSession(
            accessToken = "old-access",
            refreshToken = "old-refresh",
            user =
                User(
                    id = 1,
                    email = "v@example.com",
                    displayName = "V",
                    defaultCurrency = "UAH",
                ),
        )

    private fun MockRequestHandleScope.respondJson(body: String): HttpResponseData =
        respond(
            content = body,
            status = HttpStatusCode.OK,
            headers = headersOf(HttpHeaders.ContentType, "application/json"),
        )

    private fun MockRequestHandleScope.respondUnauthorizedBearer(): HttpResponseData =
        respond(
            content = "",
            status = HttpStatusCode.Unauthorized,
            headers = headersOf(HttpHeaders.WWWAuthenticate, "Bearer realm=skilky"),
        )
}

private class FakeTokenStorage(
    initial: AuthSession? = null,
) : TokenStorage {
    private var session: AuthSession? = initial

    override suspend fun save(session: AuthSession) {
        this.session = session
    }

    override suspend fun read(): AuthSession? = session

    override suspend fun clear() {
        session = null
    }
}
