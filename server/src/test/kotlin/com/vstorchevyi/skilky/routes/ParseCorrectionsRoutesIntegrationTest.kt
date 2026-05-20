package com.vstorchevyi.skilky.routes

import com.vstorchevyi.skilky.api.ApiRoutes
import com.vstorchevyi.skilky.api.AuthResponse
import com.vstorchevyi.skilky.api.Currency
import com.vstorchevyi.skilky.api.ParseCorrectionRequest
import com.vstorchevyi.skilky.api.ParseModality
import com.vstorchevyi.skilky.api.ParsedExpenseItem
import com.vstorchevyi.skilky.api.RegisterRequest
import com.vstorchevyi.skilky.module
import com.vstorchevyi.skilky.support.PostgresContainer
import com.vstorchevyi.skilky.support.jsonClient
import com.vstorchevyi.skilky.support.useTestConfigWithDb
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlin.test.BeforeTest
import kotlin.test.Test

/**
 * Route-level tests for `POST /api/v1/parse/corrections`.
 *
 * Backs onto a real Postgres via Testcontainers because the endpoint
 * persists straight to `parse_corrections`. The auth token is minted by
 * registering a user through `/auth/register`, the same path real
 * clients use.
 */
class ParseCorrectionsRoutesIntegrationTest {
    @BeforeTest
    fun resetSchema() {
        PostgresContainer.reset()
    }

    @Test
    fun `unauthenticated request is 401`() =
        runRouteTest { sut ->
            val response = sut.postCorrection(token = null)

            response.status shouldBe HttpStatusCode.Unauthorized
        }

    @Test
    fun `authenticated happy path returns 204`() =
        runRouteTest { sut ->
            val token = sut.registerAndGetToken()

            val response = sut.postCorrection(token = token)

            response.status shouldBe HttpStatusCode.NoContent
        }

    @Test
    fun `empty original list is rejected with 422`() =
        runRouteTest { sut ->
            val token = sut.registerAndGetToken()

            val response =
                sut.postCorrection(
                    token = token,
                    body =
                        ParseCorrectionRequest(
                            modality = ParseModality.TEXT,
                            currency = Currency.UAH,
                            original = emptyList(),
                            final = emptyList(),
                        ),
                )

            response.status shouldBe HttpStatusCode.UnprocessableEntity
        }

    @Test
    fun `empty final list is accepted (user discarded all parsed items)`() =
        runRouteTest { sut ->
            val token = sut.registerAndGetToken()

            val response =
                sut.postCorrection(
                    token = token,
                    body =
                        ParseCorrectionRequest(
                            modality = ParseModality.RECEIPT,
                            currency = Currency.USD,
                            original = listOf(anItem(name = "Coffee", amount = 35.0)),
                            final = emptyList(),
                        ),
                )

            response.status shouldBe HttpStatusCode.NoContent
        }

    private fun runRouteTest(block: suspend ApplicationTestBuilder.(HttpClient) -> Unit) =
        testApplication {
            useTestConfigWithDb()
            application { module() }
            block(jsonClient())
        }

    private suspend fun HttpClient.registerAndGetToken(): String {
        val response =
            post(ApiRoutes.Auth.REGISTER) {
                contentType(ContentType.Application.Json)
                setBody(RegisterRequest("vlad@example.com", "secret123", "Vlad"))
            }
        return response.body<AuthResponse>().token
    }

    private suspend fun HttpClient.postCorrection(
        token: String?,
        body: ParseCorrectionRequest = aCorrectionRequest(),
    ): HttpResponse =
        post(ApiRoutes.Parse.CORRECTIONS) {
            contentType(ContentType.Application.Json)
            if (token != null) bearerAuth(token)
            setBody(body)
        }

    private fun aCorrectionRequest() =
        ParseCorrectionRequest(
            modality = ParseModality.TEXT,
            currency = Currency.UAH,
            original = listOf(anItem(name = "milk", amount = 45.0)),
            final = listOf(anItem(name = "Milk", amount = 40.0, suggestedCategoryId = 1L)),
        )

    private fun anItem(
        name: String,
        amount: Double,
        suggestedCategoryId: Long? = null,
    ) = ParsedExpenseItem(
        name = name,
        amount = amount,
        currency = Currency.UAH,
        suggestedCategoryId = suggestedCategoryId,
        suggestedCategoryName = name,
        confidence = 0.9,
    )
}
