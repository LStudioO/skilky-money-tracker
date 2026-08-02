package com.vstorchevyi.skilky.data.repository

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.vstorchevyi.skilky.api.Currency
import com.vstorchevyi.skilky.data.local.ExpenseDao
import com.vstorchevyi.skilky.data.local.ExpenseEntity
import com.vstorchevyi.skilky.data.local.SkilkyDatabase
import com.vstorchevyi.skilky.data.remote.ExpenseApi
import com.vstorchevyi.skilky.domain.model.AppError
import com.vstorchevyi.skilky.domain.model.Either
import com.vstorchevyi.skilky.domain.model.ExpenseInput
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.MockRequestHandler
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ExpenseRepositoryImplTest {
    private lateinit var tempDir: File
    private lateinit var database: SkilkyDatabase

    @BeforeTest
    fun setUp() {
        tempDir = Files.createTempDirectory("expense-repo-test").toFile()
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
    fun `refresh replaces the local cache with the server page`() =
        runTest {
            // Arrange
            val sut =
                createSut(
                    handler = { _ ->
                        respondJson(
                            """
                            {
                              "items": [
                                {
                                  "id": 1, "name": "Milk", "amount": 45.0, "currency": "UAH",
                                  "category": {"id": 1, "name": "Food", "icon": "🍎", "color": "#FF6B6B", "isDefault": true, "nameKey": "food"},
                                  "note": null, "inputType": "TEXT", "date": "2026-06-08", "createdAt": "2026-06-08T10:00:00Z"
                                },
                                {
                                  "id": 2, "name": "Taxi", "amount": 120.0, "currency": "UAH",
                                  "category": {"id": 2, "name": "Transport", "icon": "🚕", "color": "#4ECDC4", "isDefault": true, "nameKey": "transport"},
                                  "note": "to office", "inputType": "TEXT", "date": "2026-06-07", "createdAt": "2026-06-07T08:00:00Z"
                                }
                              ],
                              "total": 2, "page": 0, "size": 50
                            }
                            """.trimIndent(),
                        )
                    },
                )

            // Act
            val result = sut.refresh()

            // Assert
            assertIs<Either.Right<Unit>>(result)
            val cached = sut.getExpenses().first()
            assertEquals(2, cached.size)
            // DAO returns newest date first.
            assertEquals("Milk", cached.first().name)
            assertEquals("Food", cached.first().category.name)
            assertEquals("to office", cached.last().note)
        }

    @Test
    fun `refresh maps a 5xx into AppError Network and leaves the cache alone`() =
        runTest {
            // Arrange
            val sut =
                createSut(
                    handler = { _ ->
                        respond(
                            content = "",
                            status = HttpStatusCode.InternalServerError,
                        )
                    },
                )

            // Act
            val result = sut.refresh()

            // Assert
            assertEquals(Either.Left(AppError.Network), result)
            assertEquals(emptyList(), sut.getExpenses().first())
        }

    @Test
    fun `refresh maps a Room failure into AppError Storage`() =
        runTest {
            // Arrange
            val sut =
                createSut(
                    handler = { _ ->
                        respondJson(
                            """
                            {
                              "items": [],
                              "total": 0, "page": 0, "size": 50
                            }
                            """.trimIndent(),
                        )
                    },
                    dao = FailingExpenseDao(),
                )

            // Act
            val result = sut.refresh()

            // Assert
            assertEquals(Either.Left(AppError.Storage), result)
        }

    @Test
    fun `create maps a cache write failure to Storage after the server accepts it`() =
        runTest {
            // Arrange
            var requestCount = 0
            val sut =
                createSut(
                    handler = { _ ->
                        requestCount += 1
                        respondJson(
                            """
                            {
                              "items": [{
                                "id": 1, "name": "Milk", "amount": 45.0, "currency": "UAH",
                                "category": {"id": 1, "name": "Food", "icon": "🍎", "color": "#FF6B6B", "isDefault": true, "nameKey": "food"},
                                "note": null, "inputType": "TEXT", "date": "2026-06-08", "createdAt": "2026-06-08T10:00:00Z"
                              }],
                              "total": 1, "page": 0, "size": 1
                            }
                            """.trimIndent(),
                        )
                    },
                    dao = FailingExpenseDao(),
                )

            // Act
            val result =
                sut.create(
                    ExpenseInput(
                        name = "Milk",
                        amount = 45.0,
                        currency = Currency.UAH,
                        categoryId = 1,
                        note = null,
                        date = LocalDate(2026, 6, 8),
                    ),
                )

            // Assert
            assertEquals(Either.Left(AppError.Storage), result)
            assertEquals(1, requestCount)
        }

    private fun createSut(
        handler: MockRequestHandler,
        dao: ExpenseDao = database.expenseDao(),
    ): ExpenseRepositoryImpl {
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
        return ExpenseRepositoryImpl(dao = dao, api = ExpenseApi(client))
    }

    private fun MockRequestHandleScope.respondJson(body: String) =
        respond(
            content = body,
            status = HttpStatusCode.OK,
            headers = headersOf(HttpHeaders.ContentType, "application/json"),
        )

    private class FailingExpenseDao : ExpenseDao {
        override fun getAll(): Flow<List<ExpenseEntity>> = flowOf(emptyList())

        override fun getById(id: Long): Flow<ExpenseEntity?> = flowOf(null)

        override suspend fun upsertAll(items: List<ExpenseEntity>) {
            error("disk unavailable")
        }

        override suspend fun deleteById(id: Long) {
            error("disk unavailable")
        }

        override suspend fun clear() = Unit
    }
}
