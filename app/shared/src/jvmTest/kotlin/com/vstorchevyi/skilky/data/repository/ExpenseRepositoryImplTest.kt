package com.vstorchevyi.skilky.data.repository

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.vstorchevyi.skilky.api.Currency
import com.vstorchevyi.skilky.api.ExpenseBatchRequest
import com.vstorchevyi.skilky.data.local.CategoryEntity
import com.vstorchevyi.skilky.data.local.ExpenseDao
import com.vstorchevyi.skilky.data.local.ExpenseEntity
import com.vstorchevyi.skilky.data.local.SkilkyDatabase
import com.vstorchevyi.skilky.data.remote.ExpenseApi
import com.vstorchevyi.skilky.domain.model.AppError
import com.vstorchevyi.skilky.domain.model.Either
import com.vstorchevyi.skilky.domain.model.Expense
import com.vstorchevyi.skilky.domain.model.ExpenseInput
import com.vstorchevyi.skilky.domain.model.ExpenseSyncStatus
import com.vstorchevyi.skilky.domain.model.getOrNull
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
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
import kotlinx.coroutines.flow.flow
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
import kotlin.test.assertTrue

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
    fun `expense list cache read failure emits Storage`() =
        runTest {
            val sut = createSut(handler = { respondJson("{}") }, dao = FailingReadExpenseDao())

            assertEquals(Either.Left(AppError.Storage), sut.getExpenses().first())
        }

    @Test
    fun `single expense cache read failure emits Storage`() =
        runTest {
            val sut = createSut(handler = { respondJson("{}") }, dao = FailingReadExpenseDao())

            assertEquals(Either.Left(AppError.Storage), sut.getExpense(1).first())
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
            val cached = requireNotNull(sut.getExpenses().first().getOrNull())
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
            assertEquals(emptyList(), sut.getExpenses().first().getOrNull())
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
    fun `create stays cached and queued when upload fails`() =
        runTest {
            seedFoodCategory()
            val sut =
                createSut(
                    handler = { _ ->
                        respond(
                            content = "",
                            status = HttpStatusCode.InternalServerError,
                        )
                    },
                )

            val result = sut.create(milkInput())

            val created = assertIs<Either.Right<Expense>>(result).value
            assertTrue(created.id < 0)
            assertEquals(listOf("Milk"), sut.getExpenses().first().getOrNull()?.map { it.name })
            val queued = database.syncQueueDao().observeAll().first().single()
            assertEquals(created.id, queued.localExpenseId)
            assertEquals(1, queued.retryCount)
        }

    @Test
    fun `retry reuses client id and replaces pending expense`() =
        runTest {
            seedFoodCategory()
            val requests = mutableListOf<ExpenseBatchRequest>()
            var requestCount = 0
            val sut =
                createSut(
                    handler = { request ->
                        requests +=
                            Json.decodeFromString<ExpenseBatchRequest>(
                                request.body.toByteArray().decodeToString(),
                            )
                        requestCount += 1
                        if (requestCount == 1) {
                            respond(content = "", status = HttpStatusCode.InternalServerError)
                        } else {
                            respondJson(createdMilkResponse())
                        }
                    },
                )

            sut.create(milkInput())
            val result = sut.syncPending()

            assertIs<Either.Right<Unit>>(result)
            assertEquals(2, requests.size)
            assertEquals(requests[0].items.single().clientId, requests[1].items.single().clientId)
            assertEquals(emptyList(), database.syncQueueDao().observeAll().first())
            val cached = requireNotNull(sut.getExpenses().first().getOrNull()).single()
            assertEquals(1L, cached.id)
            assertEquals("Milk", cached.name)
        }

    @Test
    fun `fifth failed upload marks expense failed and manual retry can sync it`() =
        runTest {
            seedFoodCategory()
            var requestCount = 0
            val sut =
                createSut(
                    handler = { _ ->
                        requestCount += 1
                        if (requestCount <= 5) {
                            respond(content = "", status = HttpStatusCode.InternalServerError)
                        } else {
                            respondJson(createdMilkResponse())
                        }
                    },
                )

            val created = assertIs<Either.Right<Expense>>(sut.create(milkInput())).value
            repeat(4) { sut.syncPending() }

            assertEquals(
                ExpenseSyncStatus.FAILED,
                sut.getExpense(created.id).first().getOrNull()?.syncStatus,
            )

            val result = sut.retryPending(created.id)

            assertIs<Either.Right<Unit>>(result)
            assertEquals(emptyList(), database.syncQueueDao().observeAll().first())
            assertEquals(1L, sut.getExpenses().first().getOrNull()?.single()?.id)
        }

    @Test
    fun `delete pending removes queue item and local expense`() =
        runTest {
            seedFoodCategory()
            val sut =
                createSut(
                    handler = { _ ->
                        respond(content = "", status = HttpStatusCode.InternalServerError)
                    },
                )
            val created = assertIs<Either.Right<Expense>>(sut.create(milkInput())).value

            val result = sut.deletePending(created.id)

            assertIs<Either.Right<Unit>>(result)
            assertEquals(emptyList(), database.syncQueueDao().observeAll().first())
            assertEquals(emptyList(), sut.getExpenses().first().getOrNull())
        }

    private suspend fun seedFoodCategory() {
        database.categoryDao().upsertAll(
            listOf(
                CategoryEntity(
                    id = 1,
                    name = "Food",
                    icon = "🍎",
                    color = "#FF6B6B",
                    isDefault = true,
                    nameKey = "food",
                    updatedAt = 0,
                ),
            ),
        )
    }

    private fun milkInput() =
        ExpenseInput(
            name = "Milk",
            amount = 45.0,
            currency = Currency.UAH,
            categoryId = 1,
            note = null,
            date = LocalDate(2026, 6, 8),
        )

    private fun createdMilkResponse(): String =
        """
        {
          "items": [{
            "id": 1, "name": "Milk", "amount": 45.0, "currency": "UAH",
            "category": {"id": 1, "name": "Food", "icon": "🍎", "color": "#FF6B6B", "isDefault": true, "nameKey": "food"},
            "note": null, "inputType": "TEXT", "date": "2026-06-08", "createdAt": "2026-06-08T10:00:00Z"
          }],
          "total": 1, "page": 0, "size": 1
        }
        """.trimIndent()

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
        return ExpenseRepositoryImpl(
            dao = dao,
            categoryDao = database.categoryDao(),
            syncQueueDao = database.syncQueueDao(),
            api = ExpenseApi(client),
        )
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

        override suspend fun clearSynced() = Unit
    }

    private class FailingReadExpenseDao : ExpenseDao {
        override fun getAll(): Flow<List<ExpenseEntity>> = flow { error("disk unavailable") }

        override fun getById(id: Long): Flow<ExpenseEntity?> = flow { error("disk unavailable") }

        override suspend fun upsertAll(items: List<ExpenseEntity>) = Unit

        override suspend fun deleteById(id: Long) = Unit

        override suspend fun clear() = Unit

        override suspend fun clearSynced() = Unit
    }
}
