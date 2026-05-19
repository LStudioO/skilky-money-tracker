package com.vstorchevyi.skilky.routes

import com.vstorchevyi.skilky.api.ApiRoutes
import com.vstorchevyi.skilky.api.AuthResponse
import com.vstorchevyi.skilky.api.CategoryDto
import com.vstorchevyi.skilky.api.CreateCategoryRequest
import com.vstorchevyi.skilky.api.DefaultCategoryKeys
import com.vstorchevyi.skilky.api.ExpenseBatchRequest
import com.vstorchevyi.skilky.api.ExpenseListResponse
import com.vstorchevyi.skilky.api.ExpenseRequest
import com.vstorchevyi.skilky.api.ExpenseResponse
import com.vstorchevyi.skilky.api.RegisterRequest
import com.vstorchevyi.skilky.module
import com.vstorchevyi.skilky.support.PostgresContainer
import com.vstorchevyi.skilky.support.aCreateCategoryRequest
import com.vstorchevyi.skilky.support.aRegisterRequest
import com.vstorchevyi.skilky.support.anExpenseBatchRequest
import com.vstorchevyi.skilky.support.anExpenseRequest
import com.vstorchevyi.skilky.support.jsonClient
import com.vstorchevyi.skilky.support.useTestConfigWithDb
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.datetime.LocalDate
import java.util.UUID
import kotlin.test.BeforeTest
import kotlin.test.Test

/**
 * End-to-end category + expense flows against Postgres (Testcontainers).
 *
 * Each `@Test` uses **one** Arrange / Act / Assert cycle. Shared HTTP helpers live at the bottom;
 * `sut` is the [HttpClient] inside [runExpenseCategoryTest].
 */
class ExpenseCategoryRoutesIntegrationTest {
    @BeforeTest
    fun resetSchema() {
        PostgresContainer.reset()
    }

    @Test
    fun `GET categories returns nine seeded defaults`() =
        runExpenseCategoryTest { sut ->
            // Arrange
            val auth = sut.registerFreshUser()

            // Act
            val categories = sut.getCategories(auth.token)

            // Assert
            categories shouldHaveSize 9
            categories.any { it.nameKey == DefaultCategoryKeys.FOOD } shouldBe true
        }

    @Test
    fun `POST categories returns 201 with body`() =
        runExpenseCategoryTest { sut ->
            // Arrange
            val auth = sut.registerFreshUser()

            // Act
            val response = sut.postCategory(auth.token, aCreateCategoryRequest())

            // Assert
            response.status shouldBe HttpStatusCode.Created
            response.body<CategoryDto>().name shouldBe "Gym"
        }

    @Test
    fun `POST expenses batch returns 201`() =
        runExpenseCategoryTest { sut ->
            // Arrange
            val auth = sut.registerFreshUser()
            val foodId = sut.foodCategoryId(auth.token)
            val clientId = UUID.randomUUID().toString()
            val batch =
                anExpenseBatchRequest(
                    listOf(
                        anExpenseRequest(
                            categoryId = foodId,
                            clientId = clientId,
                            date = LocalDate(2026, 3, 21),
                        ),
                    ),
                )

            // Act
            val response = sut.postExpenses(auth.token, batch)

            // Assert
            response.status shouldBe HttpStatusCode.Created
            response.body<ExpenseListResponse>().items.single().name shouldBe "Milk"
        }

    @Test
    fun `POST expenses duplicate clientId returns same server id`() =
        runExpenseCategoryTest { sut ->
            // Arrange
            val auth = sut.registerFreshUser()
            val foodId = sut.foodCategoryId(auth.token)
            val clientId = UUID.randomUUID().toString()
            val day = LocalDate(2026, 3, 21)
            val batch =
                anExpenseBatchRequest(
                    listOf(anExpenseRequest(categoryId = foodId, clientId = clientId, date = day)),
                )
            val firstResponse = sut.postExpenses(auth.token, batch)
            require(firstResponse.status == HttpStatusCode.Created) {
                "first batch failed: ${firstResponse.status}"
            }
            val firstId = firstResponse.body<ExpenseListResponse>().items.single().id

            // Act
            val duplicate = sut.postExpenses(auth.token, batch)

            // Assert
            duplicate.status shouldBe HttpStatusCode.Created
            duplicate.body<ExpenseListResponse>().items.single().id shouldBe firstId
        }

    @Test
    fun `GET expenses after one batch returns single row`() =
        runExpenseCategoryTest { sut ->
            // Arrange
            val auth = sut.registerFreshUser()
            val foodId = sut.foodCategoryId(auth.token)
            val clientId = UUID.randomUUID().toString()
            val day = LocalDate(2026, 3, 21)
            sut.postExpenses(
                auth.token,
                anExpenseBatchRequest(
                    listOf(anExpenseRequest(categoryId = foodId, clientId = clientId, date = day)),
                ),
            )

            // Act
            val response = sut.getExpenses(auth.token)

            // Assert
            response.status shouldBe HttpStatusCode.OK
            response.body<ExpenseListResponse>().items shouldHaveSize 1
        }

    @Test
    fun `PUT expense updates name`() =
        runExpenseCategoryTest { sut ->
            // Arrange
            val auth = sut.registerFreshUser()
            val foodId = sut.foodCategoryId(auth.token)
            val gymId =
                sut.postCategory(auth.token, aCreateCategoryRequest()).body<CategoryDto>().id
            val clientId = UUID.randomUUID().toString()
            val day = LocalDate(2026, 3, 21)
            val expenseId =
                sut
                    .postExpenses(
                        auth.token,
                        anExpenseBatchRequest(
                            listOf(
                                anExpenseRequest(categoryId = foodId, clientId = clientId, date = day),
                            ),
                        ),
                    ).body<ExpenseListResponse>()
                    .items
                    .single()
                    .id
            val update =
                anExpenseRequest(
                    name = "Milk 2%",
                    amount = 46.0,
                    categoryId = gymId,
                    note = "note",
                    clientId = clientId,
                    date = day,
                )

            // Act
            val response = sut.putExpense(auth.token, expenseId, update)

            // Assert
            response.status shouldBe HttpStatusCode.OK
            response.body<ExpenseResponse>().name shouldBe "Milk 2%"
        }

    @Test
    fun `DELETE expense returns 204`() =
        runExpenseCategoryTest { sut ->
            // Arrange
            val auth = sut.registerFreshUser()
            val foodId = sut.foodCategoryId(auth.token)
            val clientId = UUID.randomUUID().toString()
            val day = LocalDate(2026, 3, 21)
            val expenseId =
                sut
                    .postExpenses(
                        auth.token,
                        anExpenseBatchRequest(
                            listOf(
                                anExpenseRequest(categoryId = foodId, clientId = clientId, date = day),
                            ),
                        ),
                    ).body<ExpenseListResponse>()
                    .items
                    .single()
                    .id

            // Act
            val response = sut.deleteExpense(auth.token, expenseId)

            // Assert
            response.status shouldBe HttpStatusCode.NoContent
        }

    @Test
    fun `DELETE system category returns 403`() =
        runExpenseCategoryTest { sut ->
            // Arrange
            val auth = sut.registerFreshUser()
            val foodId = sut.foodCategoryId(auth.token)

            // Act
            val response = sut.deleteCategory(auth.token, foodId)

            // Assert
            response.status shouldBe HttpStatusCode.Forbidden
        }

    @Test
    fun `DELETE custom category returns 204`() =
        runExpenseCategoryTest { sut ->
            // Arrange
            val auth = sut.registerFreshUser()
            val gymId =
                sut.postCategory(auth.token, aCreateCategoryRequest()).body<CategoryDto>().id

            // Act
            val response = sut.deleteCategory(auth.token, gymId)

            // Assert
            response.status shouldBe HttpStatusCode.NoContent
        }

    @Test
    fun `GET categories with uk Accept-Language localizes food`() =
        runExpenseCategoryTest { sut ->
            // Arrange
            val auth = sut.registerFreshUser()

            // Act
            val categories = sut.getCategories(auth.token, acceptLanguage = "uk-UA,en;q=0.8")

            // Assert
            categories.first { it.nameKey == DefaultCategoryKeys.FOOD }.name shouldBe "Їжа"
        }

    @Test
    fun `GET categories with en Accept-Language shows food in English`() =
        runExpenseCategoryTest { sut ->
            // Arrange
            val auth = sut.registerFreshUser()

            // Act
            val categories = sut.getCategories(auth.token, acceptLanguage = "en-US")

            // Assert
            categories.first { it.nameKey == DefaultCategoryKeys.FOOD }.name shouldBe "Food"
        }

    @Test
    fun `GET categories without token returns 401`() =
        runExpenseCategoryTest { sut ->
            // Act
            val response = sut.get(ApiRoutes.Categories.ROOT)

            // Assert
            response.status shouldBe HttpStatusCode.Unauthorized
        }

    @Test
    fun `POST expenses without token returns 401`() =
        runExpenseCategoryTest { sut ->
            // Act
            val response =
                sut.post(ApiRoutes.Expenses.ROOT) {
                    contentType(ContentType.Application.Json)
                    setBody(anExpenseBatchRequest())
                }

            // Assert
            response.status shouldBe HttpStatusCode.Unauthorized
        }

    @Test
    fun `POST expenses with empty batch returns 422`() =
        runExpenseCategoryTest { sut ->
            // Arrange
            val auth = sut.registerFreshUser()

            // Act
            val response =
                sut.postExpenses(auth.token, ExpenseBatchRequest(items = emptyList()))

            // Assert
            response.status shouldBe HttpStatusCode.UnprocessableEntity
        }

    @Test
    fun `POST expenses with more than 100 items returns 422`() =
        runExpenseCategoryTest { sut ->
            // Arrange
            val auth = sut.registerFreshUser()
            val foodId = sut.foodCategoryId(auth.token)
            val oversize =
                anExpenseBatchRequest(
                    items =
                        List(101) {
                            anExpenseRequest(
                                categoryId = foodId,
                                clientId = UUID.randomUUID().toString(),
                            )
                        },
                )

            // Act
            val response = sut.postExpenses(auth.token, oversize)

            // Assert
            response.status shouldBe HttpStatusCode.UnprocessableEntity
        }

    @Test
    fun `POST expenses with non-UUID clientId returns 422`() =
        runExpenseCategoryTest { sut ->
            // Arrange
            val auth = sut.registerFreshUser()
            val foodId = sut.foodCategoryId(auth.token)
            val batch =
                anExpenseBatchRequest(
                    listOf(anExpenseRequest(categoryId = foodId, clientId = "not-a-uuid")),
                )

            // Act
            val response = sut.postExpenses(auth.token, batch)

            // Assert
            response.status shouldBe HttpStatusCode.UnprocessableEntity
        }

    @Test
    fun `GET expenses with malformed from date returns 422`() =
        runExpenseCategoryTest { sut ->
            // Arrange
            val auth = sut.registerFreshUser()

            // Act
            val response =
                sut.get("${ApiRoutes.Expenses.ROOT}?from=garbage") {
                    header(HttpHeaders.Authorization, "Bearer ${auth.token}")
                }

            // Assert
            response.status shouldBe HttpStatusCode.UnprocessableEntity
        }

    @Test
    fun `PUT expense on unknown id returns 404`() =
        runExpenseCategoryTest { sut ->
            // Arrange
            val auth = sut.registerFreshUser()
            val foodId = sut.foodCategoryId(auth.token)
            val body =
                anExpenseRequest(
                    categoryId = foodId,
                    clientId = UUID.randomUUID().toString(),
                )

            // Act
            val response = sut.putExpense(auth.token, id = 999_999, body = body)

            // Assert
            response.status shouldBe HttpStatusCode.NotFound
        }

    @Test
    fun `DELETE expense on unknown id returns 404`() =
        runExpenseCategoryTest { sut ->
            // Arrange
            val auth = sut.registerFreshUser()

            // Act
            val response = sut.deleteExpense(auth.token, id = 999_999)

            // Assert
            response.status shouldBe HttpStatusCode.NotFound
        }

    @Test
    fun `PUT expense belonging to another user returns 404`() =
        runExpenseCategoryTest { sut ->
            // Arrange — owner creates an expense
            val owner = sut.registerFreshUser()
            val foodId = sut.foodCategoryId(owner.token)
            val expenseId =
                sut
                    .postExpenses(
                        owner.token,
                        anExpenseBatchRequest(
                            listOf(
                                anExpenseRequest(
                                    categoryId = foodId,
                                    clientId = UUID.randomUUID().toString(),
                                ),
                            ),
                        ),
                    ).body<ExpenseListResponse>()
                    .items
                    .single()
                    .id
            // …a different user tries to mutate it.
            val intruder = sut.registerFreshUser()
            val intruderFood = sut.foodCategoryId(intruder.token)
            val update =
                anExpenseRequest(
                    categoryId = intruderFood,
                    clientId = UUID.randomUUID().toString(),
                )

            // Act
            val response = sut.putExpense(intruder.token, expenseId, update)

            // Assert: 404, not 403 — don't leak that the id exists in another tenant.
            response.status shouldBe HttpStatusCode.NotFound
        }

    @Test
    fun `DELETE custom category belonging to another user returns 404`() =
        runExpenseCategoryTest { sut ->
            // Arrange — owner creates a custom category
            val owner = sut.registerFreshUser()
            val categoryId =
                sut
                    .postCategory(owner.token, aCreateCategoryRequest())
                    .body<CategoryDto>()
                    .id
            // …intruder tries to delete it
            val intruder = sut.registerFreshUser()

            // Act
            val response = sut.deleteCategory(intruder.token, categoryId)

            // Assert: 404 (foreign tenant), not 403 (system).
            response.status shouldBe HttpStatusCode.NotFound
        }

    private fun runExpenseCategoryTest(block: suspend ApplicationTestBuilder.(HttpClient) -> Unit) =
        testApplication {
            useTestConfigWithDb()
            application { module() }
            block(jsonClient())
        }

    private suspend fun HttpClient.registerFreshUser(): AuthResponse =
        register(
            aRegisterRequest(
                email = "${UUID.randomUUID()}@example.com",
                displayName = "Test User",
            ),
        )

    private suspend fun HttpClient.foodCategoryId(token: String): Long =
        getCategories(token).first { it.nameKey == DefaultCategoryKeys.FOOD }.id

    // --- Request builders -------------------------------------------------

    private suspend fun HttpClient.register(request: RegisterRequest): AuthResponse {
        val response =
            post(ApiRoutes.Auth.REGISTER) {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        require(response.status == HttpStatusCode.Created) { "register failed: ${response.status}" }
        return response.body()
    }

    private suspend fun HttpClient.getCategories(
        token: String,
        acceptLanguage: String? = null,
    ): List<CategoryDto> =
        get(ApiRoutes.Categories.ROOT) {
            header(HttpHeaders.Authorization, "Bearer $token")
            if (acceptLanguage != null) {
                header(HttpHeaders.AcceptLanguage, acceptLanguage)
            }
        }.body()

    private suspend fun HttpClient.postCategory(
        token: String,
        body: CreateCategoryRequest,
    ): HttpResponse =
        post(ApiRoutes.Categories.ROOT) {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody(body)
        }

    private suspend fun HttpClient.postExpenses(
        token: String,
        body: ExpenseBatchRequest,
    ): HttpResponse =
        post(ApiRoutes.Expenses.ROOT) {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody(body)
        }

    private suspend fun HttpClient.getExpenses(token: String): HttpResponse =
        get(ApiRoutes.Expenses.ROOT) {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

    private suspend fun HttpClient.putExpense(
        token: String,
        id: Long,
        body: ExpenseRequest,
    ): HttpResponse =
        put("${ApiRoutes.Expenses.ROOT}/$id") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody(body)
        }

    private suspend fun HttpClient.deleteExpense(
        token: String,
        id: Long,
    ): HttpResponse =
        delete("${ApiRoutes.Expenses.ROOT}/$id") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

    private suspend fun HttpClient.deleteCategory(
        token: String,
        id: Long,
    ): HttpResponse =
        delete("${ApiRoutes.Categories.ROOT}/$id") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
}
