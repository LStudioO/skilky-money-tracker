package com.vstorchevyi.skilky.plugins

import com.vstorchevyi.skilky.api.ApiErrorResponse
import com.vstorchevyi.skilky.module
import com.vstorchevyi.skilky.support.jsonClient
import com.vstorchevyi.skilky.support.useTestConfig
import io.kotest.assertions.withClue
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlin.test.Test

class StatusPagesTest {
    @Test
    fun `unknown route returns 404 envelope with code and request id`() =
        testApplication {
            // Arrange
            useTestConfig()
            application { module() }
            val sut = jsonClient()

            // Act
            val response = sut.get("/api/v1/does-not-exist")

            // Assert
            response.status shouldBe HttpStatusCode.NotFound
            val body = response.body<ApiErrorResponse>()
            body.error.code shouldBe "NOT_FOUND"
            withClue("every error envelope should include a request id for support correlation") {
                body.requestId.shouldNotBeNull()
            }
        }

    @Test
    fun `response includes X-Request-Id header`() =
        testApplication {
            // Arrange
            useTestConfig()
            application { module() }
            val sut = jsonClient()

            // Act
            val response = sut.get("/api/v1/does-not-exist")

            // Assert
            response.headers["X-Request-Id"].shouldNotBeNull()
        }
}
