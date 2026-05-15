package com.vstorchevyi.skilky

import com.vstorchevyi.skilky.api.ApiErrorResponse
import com.vstorchevyi.skilky.api.ApiRoutes
import com.vstorchevyi.skilky.api.HealthResponse
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals

class ApplicationTest {
    @Test
    fun `health endpoint returns ok with version`() =
        testApplication {
            configureForTest()
            val client = jsonClient()

            val response = client.get(ApiRoutes.HEALTH)

            assertEquals(HttpStatusCode.OK, response.status)
            val body = response.body<HealthResponse>()
            assertEquals("ok", body.status)
            assertEquals("1.0.0", body.version)
        }

    @Test
    fun `unknown route returns 404 with error envelope`() =
        testApplication {
            configureForTest()
            val client = jsonClient()

            val response = client.get("/api/v1/does-not-exist")

            assertEquals(HttpStatusCode.NotFound, response.status)
            val body = response.body<ApiErrorResponse>()
            assertEquals("NOT_FOUND", body.error.code)
        }
}

private fun ApplicationTestBuilder.configureForTest() {
    environment {
        config =
            MapApplicationConfig(
                "skilky.api.version" to "1.0.0",
                "skilky.cors.allowedHosts" to "*",
            )
    }
    application { module() }
}

private fun ApplicationTestBuilder.jsonClient() =
    createClient {
        install(ContentNegotiation) { json() }
    }
