package com.vstorchevyi.skilky.repository

import com.vstorchevyi.skilky.db.DatabaseFactory
import com.vstorchevyi.skilky.errors.ConflictException
import com.vstorchevyi.skilky.support.newRepoFixture
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class UserRepositoryIntegrationTest {
    private lateinit var factory: DatabaseFactory

    @BeforeTest
    fun setUp() {
        factory = newRepoFixture()
    }

    @AfterTest
    fun tearDown() {
        factory.close()
    }

    @Test
    fun `create then findById round-trips the user`() {
        runBlocking {
            // Arrange
            val sut = createSut()

            // Act
            val created = sut.create("vlad@example.com", "hash", "Vlad")
            val found = sut.findById(created.id)

            // Assert
            found shouldBe created
        }
    }

    @Test
    fun `findByEmail finds the created user`() {
        runBlocking {
            // Arrange
            val sut = createSut()
            sut.create("vlad@example.com", "hash", "Vlad")

            // Act
            val found = sut.findByEmail("vlad@example.com")

            // Assert
            found.shouldNotBe(null)
            found?.displayName shouldBe "Vlad"
        }
    }

    @Test
    fun `findByEmail returns null for an absent email`() {
        runBlocking {
            // Arrange
            val sut = createSut()

            // Act
            val found = sut.findByEmail("nobody@example.com")

            // Assert
            found.shouldBeNull()
        }
    }

    @Test
    fun `findById returns null for an absent id`() {
        runBlocking {
            // Arrange
            val sut = createSut()

            // Act
            val found = sut.findById(999_999)

            // Assert
            found.shouldBeNull()
        }
    }

    @Test
    fun `defaultCurrency defaults to UAH when not supplied`() {
        runBlocking {
            // Arrange
            val sut = createSut()

            // Act
            val user = sut.create("vlad@example.com", "hash", "Vlad")

            // Assert
            user.defaultCurrency shouldBe "UAH"
        }
    }

    @Test
    fun `create with duplicate email throws ConflictException`() {
        runBlocking {
            // Arrange
            val sut = createSut()
            sut.create("vlad@example.com", "hash", "Vlad")

            // Act + Assert
            shouldThrow<ConflictException> {
                sut.create("vlad@example.com", "hash", "Vlad")
            }
        }
    }

    @Test
    fun `create assigns distinct ids to different users`() {
        runBlocking {
            // Arrange
            val sut = createSut()

            // Act
            val a = sut.create("a@example.com", "hash", "A")
            val b = sut.create("b@example.com", "hash", "B")

            // Assert
            a.id shouldNotBe b.id
        }
    }

    private fun createSut() = UserRepository(factory)
}
