package com.vstorchevyi.skilky.repository

import com.vstorchevyi.skilky.api.DefaultCategoryKeys
import com.vstorchevyi.skilky.db.DatabaseFactory
import com.vstorchevyi.skilky.db.tables.CategoriesTable
import com.vstorchevyi.skilky.support.anExpenseRequest
import com.vstorchevyi.skilky.support.newRepoFixture
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

/**
 * [CategoryRepository] against a real Postgres (Testcontainers). Covers the
 * visibility scope, ownership rules behind `updateCustom` / `deleteCustomIfOwned`,
 * and the expense reassignment that runs when a custom category is deleted.
 */
class CategoryRepositoryIntegrationTest {
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
    fun `listVisible returns the seeded system defaults for a fresh user`() {
        runBlocking {
            // Arrange
            val sut = createSut()
            val userId = givenUserId("vlad@example.com")

            // Act
            val visible = sut.listVisible(userId)

            // Assert
            visible shouldHaveSize SYSTEM_CATEGORY_COUNT
            visible.all { it.ownerUserId == null } shouldBe true
        }
    }

    @Test
    fun `listVisible includes the user's own custom category`() {
        runBlocking {
            // Arrange
            val sut = createSut()
            val userId = givenUserId("vlad@example.com")
            val custom = sut.create(userId, "Gym", "material:fitness_center", "#009688")

            // Act
            val visible = sut.listVisible(userId)

            // Assert
            visible shouldHaveSize SYSTEM_CATEGORY_COUNT + 1
            visible.any { it.id == custom.id } shouldBe true
        }
    }

    @Test
    fun `listVisible excludes another user's custom category`() {
        runBlocking {
            // Arrange
            val sut = createSut()
            val owner = givenUserId("owner@example.com")
            val other = givenUserId("other@example.com")
            sut.create(owner, "Gym", "material:fitness_center", "#009688")

            // Act
            val visible = sut.listVisible(other)

            // Assert
            visible shouldHaveSize SYSTEM_CATEGORY_COUNT
        }
    }

    @Test
    fun `create persists a custom category owned by the user`() {
        runBlocking {
            // Arrange
            val sut = createSut()
            val userId = givenUserId("vlad@example.com")

            // Act
            val created = sut.create(userId, "Gym", "material:fitness_center", "#009688")

            // Assert
            val found = sut.findById(created.id)
            found.shouldNotBeNull()
            found.ownerUserId shouldBe userId
            found.isDefault shouldBe false
            found.name shouldBe "Gym"
        }
    }

    @Test
    fun `updateCustom changes an owned category`() {
        runBlocking {
            // Arrange
            val sut = createSut()
            val userId = givenUserId("vlad@example.com")
            val created = sut.create(userId, "Gym", "material:fitness_center", "#009688")

            // Act
            val updated = sut.updateCustom(userId, created.id, "Fitness", "material:sports", "#3F51B5")

            // Assert
            updated.shouldNotBeNull()
            updated.name shouldBe "Fitness"
            updated.color shouldBe "#3F51B5"
        }
    }

    @Test
    fun `updateCustom returns null for a system default`() {
        runBlocking {
            // Arrange
            val sut = createSut()
            val userId = givenUserId("vlad@example.com")
            val systemId = sut.listVisible(userId).first { it.ownerUserId == null }.id

            // Act
            val result = sut.updateCustom(userId, systemId, "Renamed", "material:sports", "#000000")

            // Assert
            result.shouldBeNull()
        }
    }

    @Test
    fun `updateCustom returns null for another user's category`() {
        runBlocking {
            // Arrange
            val sut = createSut()
            val owner = givenUserId("owner@example.com")
            val intruder = givenUserId("intruder@example.com")
            val owned = sut.create(owner, "Gym", "material:fitness_center", "#009688")

            // Act
            val result = sut.updateCustom(intruder, owned.id, "Renamed", "material:sports", "#000000")

            // Assert
            result.shouldBeNull()
        }
    }

    @Test
    fun `deleteCustomIfOwned deletes the category and reassigns its expenses to Other`() {
        runBlocking {
            // Arrange
            val sut = createSut()
            val expenses = ExpenseRepository(factory)
            val userId = givenUserId("vlad@example.com")
            val custom = sut.create(userId, "Gym", "material:fitness_center", "#009688")
            val expense =
                expenses.createBatch(userId, listOf(anExpenseRequest(categoryId = custom.id))).single()

            // Act
            val deleted = sut.deleteCustomIfOwned(userId, custom.id)

            // Assert
            deleted shouldBe true
            sut.findById(custom.id).shouldBeNull()
            val reassigned = expenses.findForUser(userId, expense.expense.id)
            reassigned.shouldNotBeNull()
            reassigned.expense.categoryId shouldBe otherCategoryId()
        }
    }

    @Test
    fun `deleteCustomIfOwned returns false for a system default`() {
        runBlocking {
            // Arrange
            val sut = createSut()
            val userId = givenUserId("vlad@example.com")
            val systemId = sut.listVisible(userId).first { it.ownerUserId == null }.id

            // Act + Assert
            sut.deleteCustomIfOwned(userId, systemId) shouldBe false
        }
    }

    private fun createSut() = CategoryRepository(factory)

    private suspend fun givenUserId(email: String): Long = users.create(email, "hash", "User").id

    private suspend fun otherCategoryId(): Long =
        factory.dbQuery {
            CategoriesTable
                .selectAll()
                .where {
                    (CategoriesTable.userId eq null) and (CategoriesTable.nameKey eq DefaultCategoryKeys.OTHER)
                }.first()[CategoriesTable.id]
                .value
        }

    private companion object {
        const val SYSTEM_CATEGORY_COUNT = 9
    }
}
