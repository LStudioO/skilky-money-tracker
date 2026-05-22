package com.vstorchevyi.skilky.routes

import com.vstorchevyi.skilky.api.ApiRoutes
import com.vstorchevyi.skilky.api.AuthResponse
import com.vstorchevyi.skilky.api.CategoryBreakdownItem
import com.vstorchevyi.skilky.api.CategoryDto
import com.vstorchevyi.skilky.api.DefaultCategoryKeys
import com.vstorchevyi.skilky.api.ExpenseBatchRequest
import com.vstorchevyi.skilky.api.MonthlySummaryResponse
import com.vstorchevyi.skilky.api.RegisterRequest
import com.vstorchevyi.skilky.api.TrendGranularity
import com.vstorchevyi.skilky.api.TrendResponse
import com.vstorchevyi.skilky.module
import com.vstorchevyi.skilky.support.PostgresContainer
import com.vstorchevyi.skilky.support.aCreateCategoryRequest
import com.vstorchevyi.skilky.support.aRegisterRequest
import com.vstorchevyi.skilky.support.anExpenseBatchRequest
import com.vstorchevyi.skilky.support.anExpenseRequest
import com.vstorchevyi.skilky.support.jsonClient
import com.vstorchevyi.skilky.support.useTestConfigWithDb
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import java.util.UUID
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.time.Clock

/**
 * End-to-end analytics flows against Postgres (Testcontainers). Aggregation
 * correctness is asserted with explicit `year`/`month`/`from`/`to` params so
 * the assertions do not depend on the date the suite runs. The trend bucket
 * math is covered separately and date-independently in [AnalyticsPeriodsTest].
 */
class AnalyticsRoutesIntegrationTest {
    @BeforeTest
    fun resetSchema() {
        PostgresContainer.reset()
    }

    @Test
    fun `GET monthly sums expenses in the requested month`() =
        runAnalyticsTest { sut ->
            // Arrange
            val auth = sut.registerFreshUser()
            val foodId = sut.foodCategoryId(auth.token)
            sut.postExpenses(auth.token, batchOf(foodId, 45.0, 45.0))

            // Act
            val response = sut.getMonthly(auth.token, year = 2026, month = 3)

            // Assert
            response.status shouldBe HttpStatusCode.OK
            val body = response.body<MonthlySummaryResponse>()
            body.grandTotal shouldBe 90.0
            body.totalByCategory.single().category shouldBe "Food"
            body.totalByCategory.single().amount shouldBe 90.0
        }

    @Test
    fun `GET monthly excludes expenses outside the requested month`() =
        runAnalyticsTest { sut ->
            // Arrange
            val auth = sut.registerFreshUser()
            val foodId = sut.foodCategoryId(auth.token)
            sut.postExpenses(auth.token, batchOf(foodId, 45.0))

            // Act — the expense is dated March 2026, query a different month
            val response = sut.getMonthly(auth.token, year = 2026, month = 4)

            // Assert
            val body = response.body<MonthlySummaryResponse>()
            body.grandTotal shouldBe 0.0
            body.totalByCategory shouldHaveSize 0
        }

    @Test
    fun `GET monthly localizes category names from Accept-Language`() =
        runAnalyticsTest { sut ->
            // Arrange
            val auth = sut.registerFreshUser()
            val foodId = sut.foodCategoryId(auth.token)
            sut.postExpenses(auth.token, batchOf(foodId, 45.0))

            // Act
            val response =
                sut.getMonthly(auth.token, year = 2026, month = 3, acceptLanguage = "uk-UA")

            // Assert
            response.body<MonthlySummaryResponse>().totalByCategory.single().category shouldBe "Їжа"
        }

    @Test
    fun `GET monthly rejects an out-of-range month`() =
        runAnalyticsTest { sut ->
            // Arrange
            val auth = sut.registerFreshUser()

            // Act
            val response = sut.getMonthly(auth.token, year = 2026, month = 13)

            // Assert
            response.status shouldBe HttpStatusCode.UnprocessableEntity
        }

    @Test
    fun `GET monthly rejects a malformed year`() =
        runAnalyticsTest { sut ->
            // Arrange
            val auth = sut.registerFreshUser()

            // Act — a non-numeric year is a client error, not a silent default
            val response =
                sut.get("${ApiRoutes.Analytics.MONTHLY}?year=foo&month=3") {
                    header(HttpHeaders.Authorization, "Bearer ${auth.token}")
                }

            // Assert
            response.status shouldBe HttpStatusCode.UnprocessableEntity
        }

    @Test
    fun `GET monthly rejects a malformed month`() =
        runAnalyticsTest { sut ->
            // Arrange
            val auth = sut.registerFreshUser()

            // Act
            val response =
                sut.get("${ApiRoutes.Analytics.MONTHLY}?month=foo") {
                    header(HttpHeaders.Authorization, "Bearer ${auth.token}")
                }

            // Assert
            response.status shouldBe HttpStatusCode.UnprocessableEntity
        }

    @Test
    fun `GET monthly without a token returns 401`() =
        runAnalyticsTest { sut ->
            // Act
            val response = sut.get(ApiRoutes.Analytics.MONTHLY)

            // Assert
            response.status shouldBe HttpStatusCode.Unauthorized
        }

    @Test
    fun `GET breakdown splits spend by category with percentages`() =
        runAnalyticsTest { sut ->
            // Arrange — Food 75, custom category 25, both in March 2026
            val auth = sut.registerFreshUser()
            val foodId = sut.foodCategoryId(auth.token)
            val gymId = sut.createCategory(auth.token)
            sut.postExpenses(
                auth.token,
                anExpenseBatchRequest(
                    listOf(
                        anExpenseRequest(categoryId = foodId, amount = 75.0, clientId = aUuid()),
                        anExpenseRequest(categoryId = gymId, amount = 25.0, clientId = aUuid()),
                    ),
                ),
            )

            // Act
            val response =
                sut.getBreakdown(auth.token, from = "2026-03-01", to = "2026-03-31")

            // Assert
            response.status shouldBe HttpStatusCode.OK
            val items = response.body<List<CategoryBreakdownItem>>()
            items shouldHaveSize 2
            // Sorted by amount descending: Food first.
            items.first().category shouldBe "Food"
            items.first().amount shouldBe 75.0
            items.first().percentage shouldBe 75.0
            items.first().count shouldBe 1
            items.last().percentage shouldBe 25.0
        }

    @Test
    fun `GET breakdown percentages sum to exactly 100 across equal categories`() =
        runAnalyticsTest { sut ->
            // Arrange — three categories with equal spend in March 2026
            val auth = sut.registerFreshUser()
            val foodId = sut.foodCategoryId(auth.token)
            val gymId = sut.createCategory(auth.token, name = "Gym")
            val booksId = sut.createCategory(auth.token, name = "Books")
            sut.postExpenses(
                auth.token,
                anExpenseBatchRequest(
                    listOf(
                        anExpenseRequest(categoryId = foodId, amount = 10.0, clientId = aUuid()),
                        anExpenseRequest(categoryId = gymId, amount = 10.0, clientId = aUuid()),
                        anExpenseRequest(categoryId = booksId, amount = 10.0, clientId = aUuid()),
                    ),
                ),
            )

            // Act
            val response = sut.getBreakdown(auth.token, from = "2026-03-01", to = "2026-03-31")

            // Assert — independent rounding would sum to 99.9
            val items = response.body<List<CategoryBreakdownItem>>()
            items shouldHaveSize 3
            items.sumOf { it.percentage } shouldBe (100.0 plusOrMinus 1e-6)
            items.map { it.percentage }.sorted() shouldBe listOf(33.3, 33.3, 33.4)
        }

    @Test
    fun `GET breakdown rejects a from date after the to date`() =
        runAnalyticsTest { sut ->
            // Arrange
            val auth = sut.registerFreshUser()

            // Act
            val response =
                sut.getBreakdown(auth.token, from = "2026-03-31", to = "2026-03-01")

            // Assert
            response.status shouldBe HttpStatusCode.UnprocessableEntity
        }

    @Test
    fun `GET trend returns one point per requested period`() =
        runAnalyticsTest { sut ->
            // Arrange
            val auth = sut.registerFreshUser()

            // Act
            val response = sut.getTrend(auth.token, granularity = "monthly", periods = 4)

            // Assert
            response.status shouldBe HttpStatusCode.OK
            val body = response.body<TrendResponse>()
            body.granularity shouldBe TrendGranularity.MONTHLY
            body.points shouldHaveSize 4
        }

    @Test
    fun `GET trend totals the current month bucket from stored expenses`() =
        runAnalyticsTest { sut ->
            // Arrange — an expense dated today lands in the single trend bucket
            val auth = sut.registerFreshUser()
            val foodId = sut.foodCategoryId(auth.token)
            val today = Clock.System.now().toLocalDateTime(TimeZone.UTC).date
            sut.postExpenses(
                auth.token,
                anExpenseBatchRequest(
                    listOf(
                        anExpenseRequest(categoryId = foodId, amount = 60.0, clientId = aUuid(), date = today),
                    ),
                ),
            )

            // Act
            val response = sut.getTrend(auth.token, granularity = "monthly", periods = 1)

            // Assert
            response.body<TrendResponse>().points.single().total shouldBe 60.0
        }

    @Test
    fun `GET trend rejects an unknown granularity`() =
        runAnalyticsTest { sut ->
            // Arrange
            val auth = sut.registerFreshUser()

            // Act
            val response = sut.getTrend(auth.token, granularity = "daily", periods = 6)

            // Assert
            response.status shouldBe HttpStatusCode.UnprocessableEntity
        }

    @Test
    fun `GET trend rejects a non-positive period count`() =
        runAnalyticsTest { sut ->
            // Arrange
            val auth = sut.registerFreshUser()

            // Act
            val response = sut.getTrend(auth.token, granularity = "monthly", periods = 0)

            // Assert
            response.status shouldBe HttpStatusCode.UnprocessableEntity
        }

    @Test
    fun `GET trend rejects a malformed period count`() =
        runAnalyticsTest { sut ->
            // Arrange
            val auth = sut.registerFreshUser()

            // Act — a non-numeric periods value must fail, not fall back to the default
            val response =
                sut.get("${ApiRoutes.Analytics.TREND}?granularity=monthly&periods=foo") {
                    header(HttpHeaders.Authorization, "Bearer ${auth.token}")
                }

            // Assert
            response.status shouldBe HttpStatusCode.UnprocessableEntity
        }

    private fun runAnalyticsTest(block: suspend ApplicationTestBuilder.(HttpClient) -> Unit) =
        testApplication {
            useTestConfigWithDb()
            application { module() }
            block(jsonClient())
        }

    // --- Fixtures ---------------------------------------------------------

    private fun aUuid(): String = UUID.randomUUID().toString()

    private fun batchOf(
        categoryId: Long,
        vararg amounts: Double,
    ): ExpenseBatchRequest =
        anExpenseBatchRequest(
            amounts.map { amount ->
                anExpenseRequest(
                    categoryId = categoryId,
                    amount = amount,
                    clientId = aUuid(),
                    date = LocalDate(2026, 3, 21),
                )
            },
        )

    private suspend fun HttpClient.registerFreshUser(): AuthResponse =
        register(aRegisterRequest(email = "${UUID.randomUUID()}@example.com", displayName = "Test User"))

    private suspend fun HttpClient.foodCategoryId(token: String): Long =
        get(ApiRoutes.Categories.ROOT) {
            header(HttpHeaders.Authorization, "Bearer $token")
        }.body<List<CategoryDto>>().first { it.nameKey == DefaultCategoryKeys.FOOD }.id

    private suspend fun HttpClient.createCategory(
        token: String,
        name: String = "Gym",
    ): Long =
        post(ApiRoutes.Categories.ROOT) {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody(aCreateCategoryRequest(name = name))
        }.body<CategoryDto>().id

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

    private suspend fun HttpClient.postExpenses(
        token: String,
        body: ExpenseBatchRequest,
    ): HttpResponse {
        val response =
            post(ApiRoutes.Expenses.ROOT) {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "Bearer $token")
                setBody(body)
            }
        require(response.status == HttpStatusCode.Created) { "seed expenses failed: ${response.status}" }
        return response
    }

    private suspend fun HttpClient.getMonthly(
        token: String,
        year: Int,
        month: Int,
        acceptLanguage: String? = null,
    ): HttpResponse =
        get("${ApiRoutes.Analytics.MONTHLY}?year=$year&month=$month") {
            header(HttpHeaders.Authorization, "Bearer $token")
            if (acceptLanguage != null) header(HttpHeaders.AcceptLanguage, acceptLanguage)
        }

    private suspend fun HttpClient.getBreakdown(
        token: String,
        from: String,
        to: String,
    ): HttpResponse =
        get("${ApiRoutes.Analytics.BREAKDOWN}?from=$from&to=$to") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

    private suspend fun HttpClient.getTrend(
        token: String,
        granularity: String,
        periods: Int,
    ): HttpResponse =
        get("${ApiRoutes.Analytics.TREND}?granularity=$granularity&periods=$periods") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
}
