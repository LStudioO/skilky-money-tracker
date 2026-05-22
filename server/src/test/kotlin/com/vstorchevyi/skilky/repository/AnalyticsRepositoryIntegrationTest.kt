package com.vstorchevyi.skilky.repository

import com.vstorchevyi.skilky.api.Currency
import com.vstorchevyi.skilky.db.DatabaseFactory
import com.vstorchevyi.skilky.support.anExpenseRequest
import com.vstorchevyi.skilky.support.newRepoFixture
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

/**
 * [AnalyticsRepository] against a real Postgres (Testcontainers). Checks the
 * aggregate SQL directly: per-category grouping, the currency and date-range
 * filters, and the per-range trend totals.
 */
class AnalyticsRepositoryIntegrationTest {
    private lateinit var factory: DatabaseFactory
    private lateinit var users: UserRepository
    private lateinit var categories: CategoryRepository
    private lateinit var expenses: ExpenseRepository

    @BeforeTest
    fun setUp() {
        factory = newRepoFixture()
        users = UserRepository(factory)
        categories = CategoryRepository(factory)
        expenses = ExpenseRepository(factory)
    }

    @AfterTest
    fun tearDown() {
        factory.close()
    }

    @Test
    fun `spendByCategory sums amounts and counts rows per category`() {
        runBlocking {
            // Arrange
            val sut = createSut()
            val userId = givenUserId("vlad@example.com")
            val food = givenCategory(userId, "Food")
            val travel = givenCategory(userId, "Travel")
            expenses.createBatch(
                userId,
                listOf(
                    anExpenseRequest(categoryId = food, amount = 10.0, date = MARCH),
                    anExpenseRequest(categoryId = food, amount = 20.0, date = MARCH),
                    anExpenseRequest(categoryId = travel, amount = 5.0, date = MARCH),
                ),
            )

            // Act
            val spend = sut.spendByCategory(userId, Currency.UAH, MARCH_START, MARCH_END)

            // Assert
            spend shouldHaveSize 2
            val foodSpend = spend.first { it.categoryId == food }
            foodSpend.total.toDouble() shouldBe 30.0
            foodSpend.count shouldBe 2L
            spend.first { it.categoryId == travel }.total.toDouble() shouldBe 5.0
        }
    }

    @Test
    fun `spendByCategory only counts the requested currency`() {
        runBlocking {
            // Arrange
            val sut = createSut()
            val userId = givenUserId("vlad@example.com")
            val food = givenCategory(userId, "Food")
            expenses.createBatch(
                userId,
                listOf(
                    anExpenseRequest(categoryId = food, amount = 10.0, currency = Currency.UAH, date = MARCH),
                    anExpenseRequest(categoryId = food, amount = 99.0, currency = Currency.USD, date = MARCH),
                ),
            )

            // Act
            val spend = sut.spendByCategory(userId, Currency.UAH, MARCH_START, MARCH_END)

            // Assert: the USD expense is excluded
            spend.single().total.toDouble() shouldBe 10.0
        }
    }

    @Test
    fun `spendByCategory only counts expenses inside the date range`() {
        runBlocking {
            // Arrange
            val sut = createSut()
            val userId = givenUserId("vlad@example.com")
            val food = givenCategory(userId, "Food")
            expenses.createBatch(
                userId,
                listOf(
                    anExpenseRequest(categoryId = food, amount = 10.0, date = LocalDate(2026, 1, 15)),
                    anExpenseRequest(categoryId = food, amount = 20.0, date = MARCH),
                ),
            )

            // Act
            val spend = sut.spendByCategory(userId, Currency.UAH, MARCH_START, MARCH_END)

            // Assert
            spend.single().total.toDouble() shouldBe 20.0
        }
    }

    @Test
    fun `spendByCategory is empty when no expense matches`() {
        runBlocking {
            // Arrange
            val sut = createSut()
            val userId = givenUserId("vlad@example.com")

            // Act
            val spend = sut.spendByCategory(userId, Currency.UAH, MARCH_START, MARCH_END)

            // Assert
            spend shouldHaveSize 0
        }
    }

    @Test
    fun `totalsForRanges returns a total per range in input order`() {
        runBlocking {
            // Arrange
            val sut = createSut()
            val userId = givenUserId("vlad@example.com")
            val food = givenCategory(userId, "Food")
            expenses.createBatch(
                userId,
                listOf(
                    anExpenseRequest(categoryId = food, amount = 10.0, date = LocalDate(2026, 1, 15)),
                    anExpenseRequest(categoryId = food, amount = 30.0, date = MARCH),
                ),
            )
            val ranges =
                listOf(
                    LocalDate(2026, 1, 1) to LocalDate(2026, 1, 31),
                    LocalDate(2026, 2, 1) to LocalDate(2026, 2, 28),
                    LocalDate(2026, 3, 1) to LocalDate(2026, 3, 31),
                )

            // Act
            val totals = sut.totalsForRanges(userId, Currency.UAH, ranges)

            // Assert: January 10, February empty, March 30
            totals.map { it.toDouble() } shouldBe listOf(10.0, 0.0, 30.0)
        }
    }

    private fun createSut() = AnalyticsRepository(factory)

    private suspend fun givenUserId(email: String): Long = users.create(email, "hash", "User").id

    private suspend fun givenCategory(
        userId: Long,
        name: String,
    ): Long = categories.create(userId, name, "material:label", "#607D8B").id

    private companion object {
        val MARCH = LocalDate(2026, 3, 15)
        val MARCH_START = LocalDate(2026, 3, 1)
        val MARCH_END = LocalDate(2026, 3, 31)
    }
}
