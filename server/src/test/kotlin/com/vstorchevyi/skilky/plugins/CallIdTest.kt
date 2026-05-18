package com.vstorchevyi.skilky.plugins

import com.vstorchevyi.skilky.api.ApiErrorResponse
import com.vstorchevyi.skilky.module
import com.vstorchevyi.skilky.support.jsonClient
import com.vstorchevyi.skilky.support.useTestConfig
import io.kotest.assertions.withClue
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.server.testing.testApplication
import kotlin.test.Test

class CallIdTest {
    @Test
    fun `inbound X-Request-Id is echoed in the response header and the envelope`() =
        testApplication {
            // Arrange
            useTestConfig()
            application { module() }
            val sut = jsonClient()
            val inboundId = "client-supplied-id-123"

            // Act
            val response =
                sut.get("/api/v1/does-not-exist") {
                    header("X-Request-Id", inboundId)
                }

            // Assert
            response.headers["X-Request-Id"] shouldBe inboundId
            val body = response.body<ApiErrorResponse>()
            body.requestId shouldBe inboundId
        }

    @Test
    fun `each request without inbound id gets a freshly generated one`() =
        testApplication {
            // Arrange
            useTestConfig()
            application { module() }
            val sut = jsonClient()

            // Act
            val first = sut.get("/api/v1/does-not-exist").body<ApiErrorResponse>()
            val second = sut.get("/api/v1/does-not-exist").body<ApiErrorResponse>()

            // Assert
            first.requestId.shouldNotBeNull()
            second.requestId.shouldNotBeNull()
            withClue("each request should have a unique server-generated id") {
                first.requestId shouldNotBe second.requestId
            }
        }
}
