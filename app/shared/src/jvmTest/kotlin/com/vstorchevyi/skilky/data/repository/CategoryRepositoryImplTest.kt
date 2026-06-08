package com.vstorchevyi.skilky.data.repository

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.vstorchevyi.skilky.api.ApiRoutes
import com.vstorchevyi.skilky.data.local.SkilkyDatabase
import com.vstorchevyi.skilky.data.remote.CategoryApi
import com.vstorchevyi.skilky.domain.model.AppError
import com.vstorchevyi.skilky.domain.model.Either
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.url
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class CategoryRepositoryImplTest {
    private lateinit var tempDir: File
    private lateinit var database: SkilkyDatabase

    @BeforeTest
    fun setUp() {
        tempDir = Files.createTempDirectory("category-repo-test").toFile()
        database =
            Room.databaseBuilder<SkilkyDatabase>(
                name = tempDir.resolve("repo.db").absolutePath,
            )
                .setDriver(BundledSQLiteDriver())
                .build()
    }

    @AfterTest
    fun tearDown() {
        database.close()
        tempDir.deleteRecursively()
    }

    @Test
    fun `refresh replaces the local cache with the server payload`() =
        runTest {
            // Arrange
            val sut =
                createSut(
                    handler = { _ ->
                        respondJson(
                            """
                            [
                              {"id":1,"name":"Food","icon":"🍎","color":"#FF6B6B","isDefault":true,"nameKey":"food"},
                              {"id":2,"name":"Coffee","icon":"☕","color":"#8B4513","isDefault":false}
                            ]
                            """.trimIndent(),
                        )
                    },
                )

            // Act
            val result = sut.refresh()

            // Assert
            assertIs<Either.Right<Unit>>(result)
            val cached = sut.getCategories().first()
            assertEquals(listOf("Food", "Coffee"), cached.map { it.name })
        }

    @Test
    fun `create posts then upserts the returned category into the cache`() =
        runTest {
            // Arrange
            val recorded = mutableListOf<HttpRequestData>()
            val sut =
                createSut(
                    handler = { request ->
                        recorded += request
                        respondJson(
                            """{"id":42,"name":"Coffee","icon":"☕","color":"#8B4513","isDefault":false}""",
                        )
                    },
                )

            // Act
            val result = sut.create(name = "Coffee", icon = "☕", color = "#8B4513")

            // Assert
            assertIs<Either.Right<Any>>(result)
            assertEquals(HttpMethod.Post, recorded.single().method)
            val cached = sut.getCategories().first()
            assertEquals(42L, cached.single().id)
            assertEquals("Coffee", cached.single().name)
        }

    @Test
    fun `update puts to the keyed URL and mirrors the response`() =
        runTest {
            // Arrange
            val recorded = mutableListOf<HttpRequestData>()
            val sut =
                createSut(
                    handler = { request ->
                        recorded += request
                        respondJson(
                            """{"id":7,"name":"Coffee","icon":"☕","color":"#5C2C0F","isDefault":false}""",
                        )
                    },
                )

            // Act
            sut.update(id = 7, name = "Coffee", icon = "☕", color = "#5C2C0F")

            // Assert
            assertEquals(HttpMethod.Put, recorded.single().method)
            assertEquals("${ApiRoutes.Categories.ROOT}/7", recorded.single().url.encodedPath)
            val cached = sut.getCategories().first().single()
            assertEquals("#5C2C0F", cached.color)
        }

    @Test
    fun `delete removes the row when the server returns 2xx`() =
        runTest {
            // Arrange
            val sut =
                createSut(
                    handler = { request ->
                        if (request.method == HttpMethod.Delete) {
                            respond(content = "", status = HttpStatusCode.NoContent)
                        } else {
                            respondJson("""{"id":1,"name":"X","icon":"X","color":"#000","isDefault":false}""")
                        }
                    },
                )
            sut.create(name = "X", icon = "X", color = "#000")

            // Act
            sut.delete(1L)

            // Assert
            assertTrue(sut.getCategories().first().isEmpty())
        }

    @Test
    fun `create maps a 409 Conflict into AppError Conflict and leaves the cache alone`() =
        runTest {
            // Arrange
            val sut =
                createSut(
                    handler = { _ ->
                        respond(
                            content = "",
                            status = HttpStatusCode.Conflict,
                        )
                    },
                )

            // Act
            val result = sut.create(name = "Coffee", icon = "☕", color = "#8B4513")

            // Assert
            assertEquals(Either.Left(AppError.Conflict), result)
            assertTrue(sut.getCategories().first().isEmpty())
        }

    private fun createSut(handler: io.ktor.client.engine.mock.MockRequestHandler): CategoryRepositoryImpl {
        val engine = MockEngine(handler)
        val client =
            HttpClient(engine) {
                expectSuccess = true
                install(ContentNegotiation) {
                    json(
                        Json {
                            ignoreUnknownKeys = true
                            explicitNulls = false
                        },
                    )
                }
                defaultRequest {
                    url("http://localhost")
                    contentType(ContentType.Application.Json)
                }
            }
        return CategoryRepositoryImpl(
            dao = database.categoryDao(),
            api = CategoryApi(client),
        )
    }

    private fun io.ktor.client.engine.mock.MockRequestHandleScope.respondJson(body: String) =
        respond(
            content = body,
            status = HttpStatusCode.OK,
            headers = headersOf(HttpHeaders.ContentType, "application/json"),
        )
}
