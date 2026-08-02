package com.vstorchevyi.skilky.data.repository

import com.vstorchevyi.skilky.data.local.TokenStorage
import com.vstorchevyi.skilky.data.remote.AuthApi
import com.vstorchevyi.skilky.domain.model.AppError
import com.vstorchevyi.skilky.domain.model.AuthSession
import com.vstorchevyi.skilky.domain.model.Either
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.url
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class AuthRepositoryImplTest {
    @Test
    fun `login maps a token storage failure to Storage after authentication`() =
        runTest {
            // Arrange
            val storage = FailingTokenStorage()
            val engine =
                MockEngine {
                    respond(
                        content =
                            """
                            {
                              "token": "access",
                              "refreshToken": "refresh",
                              "user": {
                                "id": 1,
                                "email": "v@example.com",
                                "displayName": "Vlad",
                                "defaultCurrency": "UAH"
                              }
                            }
                            """.trimIndent(),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            val client =
                HttpClient(engine) {
                    expectSuccess = true
                    install(ContentNegotiation) {
                        json(Json { ignoreUnknownKeys = true })
                    }
                    defaultRequest {
                        url("http://localhost")
                        contentType(ContentType.Application.Json)
                    }
                }
            val sut = AuthRepositoryImpl(AuthApi(client), storage)

            // Act
            val result = sut.login(email = "v@example.com", password = "password")

            // Assert
            assertEquals(Either.Left(AppError.Storage), result)
            assertEquals(1, storage.saveCalls)
        }

    private class FailingTokenStorage : TokenStorage {
        var saveCalls = 0

        override suspend fun save(session: AuthSession) {
            saveCalls += 1
            error("disk unavailable")
        }

        override suspend fun read(): AuthSession? = null

        override suspend fun clear() = Unit
    }
}
