package com.vstorchevyi.skilky.repository

import com.vstorchevyi.skilky.db.DatabaseFactory
import com.vstorchevyi.skilky.db.tables.CategoriesTable
import com.vstorchevyi.skilky.errors.ConflictException
import com.vstorchevyi.skilky.errors.ValidationException
import com.vstorchevyi.skilky.support.anExpenseRequest
import com.vstorchevyi.skilky.support.newRepoFixture
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

/**
 * [ExpenseRepository] against a real Postgres (Testcontainers). Route tests
 * already exercise the HTTP path; this pins the repository logic that is
 * awkward to reach through HTTP: clientId idempotency, owner scoping, and the
 * unique-violation mapping.
 */
class ExpenseRepositoryIntegrationTest {
    private lateinit var factory: DatabaseFactory
    private lateinit var users: UserRepository

    @BeforeTest
    fun setUp() {
        factory = newRepoFixture()
        users = UserRepository(factory)
    }

    @AfterTest
    fun tearDown() {
        factory.close()
    }

    @Test
    fun `createBatch inserts every item`() {
        runBlocking {
            // Arrange
            val sut = createSut()
            val userId = givenUserId("vlad@example.com")
            val categoryId = aSystemCategoryId()

            // Act
            val created =
                sut.createBatch(
                    userId,
                    listOf(
                        anExpenseRequest(name = "Milk", categoryId = categoryId),
                        anExpenseRequest(name = "Bread", categoryId = categoryId),
                    ),
                )

            // Assert
            created.map { it.expense.name } shouldBe listOf("Milk", "Bread")
        }
    }

    @Test
    fun `createBatch coalesces a repeated clientId onto the existing row`() {
        runBlocking {
            // Arrange
            val sut = createSut()
            val userId = givenUserId("vlad@example.com")
            val categoryId = aSystemCategoryId()
            val clientId = aUuid()
            val first =
                sut.createBatch(
                    userId,
                    listOf(anExpenseRequest(name = "Original", categoryId = categoryId, clientId = clientId)),
                ).single()

            // Act — same clientId, different data
            val second =
                sut.createBatch(
                    userId,
                    listOf(anExpenseRequest(name = "Changed", categoryId = categoryId, clientId = clientId)),
                ).single()

            // Assert — the second call returns the first row untouched
            second.expense.id shouldBe first.expense.id
            second.expense.name shouldBe "Original"
            sut.list(userId, null, null, null, 0, DEFAULT_SIZE).second shouldBe 1
        }
    }

    @Test
    fun `createBatch rejects an unknown category id`() {
        runBlocking {
            // Arrange
            val sut = createSut()
            val userId = givenUserId("vlad@example.com")

            // Act + Assert
            shouldThrow<ValidationException> {
                sut.createBatch(userId, listOf(anExpenseRequest(categoryId = UNKNOWN_CATEGORY_ID)))
            }
        }
    }

    @Test
    fun `list orders by expense date descending`() {
        runBlocking {
            // Arrange
            val sut = createSut()
            val userId = givenUserId("vlad@example.com")
            val categoryId = aSystemCategoryId()
            sut.createBatch(
                userId,
                listOf(
                    anExpenseRequest(name = "Jan", categoryId = categoryId, date = LocalDate(2026, 1, 10)),
                    anExpenseRequest(name = "Mar", categoryId = categoryId, date = LocalDate(2026, 3, 10)),
                    anExpenseRequest(name = "Feb", categoryId = categoryId, date = LocalDate(2026, 2, 10)),
                ),
            )

            // Act
            val (items, _) = sut.list(userId, null, null, null, 0, DEFAULT_SIZE)

            // Assert
            items.map { it.expense.name } shouldBe listOf("Mar", "Feb", "Jan")
        }
    }

    @Test
    fun `list filters by date range`() {
        runBlocking {
            // Arrange
            val sut = createSut()
            val userId = givenUserId("vlad@example.com")
            val categoryId = aSystemCategoryId()
            sut.createBatch(
                userId,
                listOf(
                    anExpenseRequest(categoryId = categoryId, date = LocalDate(2026, 1, 10)),
                    anExpenseRequest(categoryId = categoryId, date = LocalDate(2026, 3, 10)),
                ),
            )

            // Act
            val (items, total) =
                sut.list(userId, LocalDate(2026, 2, 1), LocalDate(2026, 4, 1), null, 0, DEFAULT_SIZE)

            // Assert
            total shouldBe 1
            items.single().expense.date shouldBe LocalDate(2026, 3, 10)
        }
    }

    @Test
    fun `list returns nothing for a different user`() {
        runBlocking {
            // Arrange
            val sut = createSut()
            val owner = givenUserId("owner@example.com")
            val other = givenUserId("other@example.com")
            sut.createBatch(owner, listOf(anExpenseRequest(categoryId = aSystemCategoryId())))

            // Act
            val (items, total) = sut.list(other, null, null, null, 0, DEFAULT_SIZE)

            // Assert
            total shouldBe 0
            items shouldHaveSize 0
        }
    }

    @Test
    fun `update changes the stored fields`() {
        runBlocking {
            // Arrange
            val sut = createSut()
            val userId = givenUserId("vlad@example.com")
            val categoryId = aSystemCategoryId()
            val clientId = aUuid()
            val created =
                sut.createBatch(
                    userId,
                    listOf(anExpenseRequest(name = "Milk", categoryId = categoryId, clientId = clientId)),
                ).single()

            // Act
            val updated =
                sut.update(
                    userId,
                    created.expense.id,
                    anExpenseRequest(name = "Oat milk", amount = 60.0, categoryId = categoryId, clientId = clientId),
                )

            // Assert
            updated.shouldNotBeNull()
            updated.expense.name shouldBe "Oat milk"
            updated.expense.amount.toDouble() shouldBe 60.0
        }
    }

    @Test
    fun `update returns null for an expense owned by another user`() {
        runBlocking {
            // Arrange
            val sut = createSut()
            val owner = givenUserId("owner@example.com")
            val intruder = givenUserId("intruder@example.com")
            val categoryId = aSystemCategoryId()
            val expense =
                sut.createBatch(owner, listOf(anExpenseRequest(categoryId = categoryId))).single()

            // Act
            val result =
                sut.update(intruder, expense.expense.id, anExpenseRequest(categoryId = categoryId))

            // Assert
            result.shouldBeNull()
        }
    }

    @Test
    fun `update throws ConflictException when the clientId is already taken`() {
        runBlocking {
            // Arrange
            val sut = createSut()
            val userId = givenUserId("vlad@example.com")
            val categoryId = aSystemCategoryId()
            val clientA = aUuid()
            val created =
                sut.createBatch(
                    userId,
                    listOf(
                        anExpenseRequest(categoryId = categoryId, clientId = clientA),
                        anExpenseRequest(categoryId = categoryId, clientId = aUuid()),
                    ),
                )

            // Act + Assert — move the second expense onto the first one's clientId
            shouldThrow<ConflictException> {
                sut.update(
                    userId,
                    created[1].expense.id,
                    anExpenseRequest(categoryId = categoryId, clientId = clientA),
                )
            }
        }
    }

    @Test
    fun `delete removes an owned expense and ignores a foreign one`() {
        runBlocking {
            // Arrange
            val sut = createSut()
            val owner = givenUserId("owner@example.com")
            val intruder = givenUserId("intruder@example.com")
            val expense =
                sut.createBatch(owner, listOf(anExpenseRequest(categoryId = aSystemCategoryId()))).single()

            // Act + Assert
            sut.delete(intruder, expense.expense.id) shouldBe false
            sut.delete(owner, expense.expense.id) shouldBe true
        }
    }

    private fun createSut() = ExpenseRepository(factory)

    private suspend fun givenUserId(email: String): Long = users.create(email, "hash", "User").id

    /** Id of any seeded system default category, usable as a valid FK target. */
    private suspend fun aSystemCategoryId(): Long =
        factory.dbQuery {
            CategoriesTable
                .selectAll()
                .where { CategoriesTable.userId eq null }
                .first()[CategoriesTable.id]
                .value
        }

    private fun aUuid(): String = UUID.randomUUID().toString()

    private companion object {
        const val DEFAULT_SIZE = 50
        const val UNKNOWN_CATEGORY_ID = 999_999L
    }
}
