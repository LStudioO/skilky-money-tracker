package com.vstorchevyi.skilky.routes

import com.vstorchevyi.skilky.api.ApiErrorResponse
import com.vstorchevyi.skilky.api.ApiRoutes
import com.vstorchevyi.skilky.api.AuthResponse
import com.vstorchevyi.skilky.api.LoginRequest
import com.vstorchevyi.skilky.api.RefreshRequest
import com.vstorchevyi.skilky.api.RegisterRequest
import com.vstorchevyi.skilky.module
import com.vstorchevyi.skilky.support.PostgresContainer
import com.vstorchevyi.skilky.support.jsonClient
import com.vstorchevyi.skilky.support.useTestConfigWithDb
import io.kotest.assertions.withClue
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldNotBeBlank
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlin.test.BeforeTest
import kotlin.test.Test

/**
 * End-to-end tests against a real Postgres via Testcontainers.
 * Schema is reset before every test so they can run in any order.
 */
class AuthRoutesIntegrationTest {
    @BeforeTest
    fun resetSchema() {
        PostgresContainer.reset()
    }

    @Test
    fun `register issues 201 with access and refresh tokens`() =
        runAuthTest { sut ->
            // Act
            val response = sut.register("vlad@example.com", "secret123", "Vlad")

            // Assert
            response.status shouldBe HttpStatusCode.Created
            val body = response.body<AuthResponse>()
            body.token.shouldNotBeBlank()
            body.refreshToken.shouldNotBeBlank()
            body.user.email shouldBe "vlad@example.com"
            body.user.displayName shouldBe "Vlad"
            body.user.defaultCurrency shouldBe "UAH"
        }

    @Test
    fun `register with an invalid email is 422`() =
        runAuthTest { sut ->
            val response = sut.register("not-an-email", "secret123", "Vlad")

            response.status shouldBe HttpStatusCode.UnprocessableEntity
            response.body<ApiErrorResponse>().error.code shouldBe "VALIDATION_ERROR"
        }

    @Test
    fun `register with a duplicate email is 409`() =
        runAuthTest { sut ->
            // Arrange
            sut.register("vlad@example.com", "secret123", "Vlad")

            // Act
            val response = sut.register("vlad@example.com", "secret123", "Vlad")

            // Assert
            response.status shouldBe HttpStatusCode.Conflict
            response.body<ApiErrorResponse>().error.code shouldBe "CONFLICT"
        }

    @Test
    fun `concurrent register with the same email - exactly one succeeds`() =
        runAuthTest { sut ->
            // Arrange + Act: race the pre-check then INSERT. The DB unique
            // index is the only authority that can guarantee uniqueness.
            val responses =
                coroutineScope {
                    listOf(
                        async { sut.register("race@example.com", "secret123", "A") },
                        async { sut.register("race@example.com", "secret123", "B") },
                    ).awaitAll()
                }

            // Assert
            val successes = responses.count { it.status == HttpStatusCode.Created }
            val conflicts = responses.count { it.status == HttpStatusCode.Conflict }
            withClue("exactly one of the concurrent registers should win") {
                successes shouldBe 1
            }
            withClue("the loser should see a clean 409, not a 500 from the DB constraint") {
                conflicts shouldBe 1
            }
        }

    @Test
    fun `login with correct credentials returns 200 and a new token pair`() =
        runAuthTest { sut ->
            // Arrange
            val registered = sut.register("vlad@example.com", "secret123", "Vlad").body<AuthResponse>()

            // Act
            val response = sut.login("vlad@example.com", "secret123")

            // Assert
            response.status shouldBe HttpStatusCode.OK
            val body = response.body<AuthResponse>()
            body.user.id shouldBe registered.user.id
            withClue("login should mint a fresh refresh token, not reuse the one from register") {
                body.refreshToken shouldNotBe registered.refreshToken
            }
        }

    @Test
    fun `login with wrong password is 401`() =
        runAuthTest { sut ->
            // Arrange
            sut.register("vlad@example.com", "secret123", "Vlad")

            // Act
            val response = sut.login("vlad@example.com", "wrong-password1")

            // Assert
            response.status shouldBe HttpStatusCode.Unauthorized
            response.body<ApiErrorResponse>().error.code shouldBe "UNAUTHORIZED"
        }

    @Test
    fun `login for unknown user returns the same 401 envelope as wrong password`() =
        runAuthTest { sut ->
            // Same envelope for unknown-user and wrong-password defends
            // against user enumeration via response shape.
            val response = sut.login("nobody@example.com", "secret123")

            response.status shouldBe HttpStatusCode.Unauthorized
            response.body<ApiErrorResponse>().error.code shouldBe "UNAUTHORIZED"
        }

    @Test
    fun `refresh rotates the token and invalidates the old one`() =
        runAuthTest { sut ->
            // Arrange
            val initial = sut.register("vlad@example.com", "secret123", "Vlad").body<AuthResponse>()

            // Act
            val rotated = sut.refresh(initial.refreshToken).body<AuthResponse>()
            val replay = sut.refresh(initial.refreshToken)

            // Assert
            rotated.refreshToken shouldNotBe initial.refreshToken
            withClue("the old refresh token must not be reusable after rotation") {
                replay.status shouldBe HttpStatusCode.Unauthorized
            }
        }

    @Test
    fun `refresh with an unknown token is 401`() =
        runAuthTest { sut ->
            val response = sut.refresh("not-a-real-token")

            response.status shouldBe HttpStatusCode.Unauthorized
        }

    @Test
    fun `error envelopes always carry a request id`() =
        runAuthTest { sut ->
            val response = sut.login("nobody@example.com", "secret123")

            val body = response.body<ApiErrorResponse>()
            body.requestId.shouldNotBeNull()
            body.requestId.shouldNotBeBlank()
        }

    private fun runAuthTest(block: suspend ApplicationTestBuilder.(HttpClient) -> Unit) =
        testApplication {
            useTestConfigWithDb()
            application { module() }
            block(jsonClient())
        }

    // --- Request builders -------------------------------------------------

    private suspend fun HttpClient.register(
        email: String,
        password: String,
        displayName: String,
    ): HttpResponse =
        post(ApiRoutes.Auth.REGISTER) {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest(email, password, displayName))
        }

    private suspend fun HttpClient.login(
        email: String,
        password: String,
    ): HttpResponse =
        post(ApiRoutes.Auth.LOGIN) {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(email, password))
        }

    private suspend fun HttpClient.refresh(refreshToken: String): HttpResponse =
        post(ApiRoutes.Auth.REFRESH) {
            contentType(ContentType.Application.Json)
            setBody(RefreshRequest(refreshToken))
        }
}
