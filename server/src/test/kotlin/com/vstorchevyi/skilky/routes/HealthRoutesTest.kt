package com.vstorchevyi.skilky.routes

import com.vstorchevyi.skilky.api.ApiRoutes
import com.vstorchevyi.skilky.api.HealthResponse
import com.vstorchevyi.skilky.module
import com.vstorchevyi.skilky.support.jsonClient
import com.vstorchevyi.skilky.support.useTestConfig
import io.kotest.matchers.shouldBe
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlin.test.Test

class HealthRoutesTest {
    @Test
    fun `health returns 200 with version from config`() =
        testApplication {
            // Arrange
            useTestConfig()
            application { module() }
            val sut = jsonClient()

            // Act
            val response = sut.get(ApiRoutes.HEALTH)

            // Assert
            response.status shouldBe HttpStatusCode.OK
            val body = response.body<HealthResponse>()
            body.status shouldBe "ok"
            body.version shouldBe "1.0.0"
        }

    @Test
    fun `health-db is absent when DB is not configured`() =
        testApplication {
            // Arrange: no database block in the test config.
            useTestConfig()
            application { module() }
            val sut = jsonClient()

            // Act
            val response = sut.get(ApiRoutes.HEALTH_DB)

            // Assert: the route is not registered, so the catch-all
            // not-found handler responds with the envelope.
            response.status shouldBe HttpStatusCode.NotFound
        }
}
