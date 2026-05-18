package com.vstorchevyi.skilky.repository

import com.vstorchevyi.skilky.db.DatabaseFactory
import com.vstorchevyi.skilky.db.tables.RefreshTokensTable
import com.vstorchevyi.skilky.db.tables.UsersTable
import com.vstorchevyi.skilky.security.TokenHasher
import com.vstorchevyi.skilky.support.newRepoFixture
import io.kotest.assertions.withClue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldNotBeBlank
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

class RefreshTokenRepositoryIntegrationTest {
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
    fun `create returns a non-blank raw token and stores its hash`() {
        runBlocking {
            // Arrange
            val sut = createSut()
            val user = givenUser("vlad@example.com")

            // Act
            val raw = sut.create(userId = user.id, ttlDays = 90)

            // Assert
            raw.shouldNotBeBlank()
            val storedHash = readStoredHashFor(user.id)
            withClue("the raw token must never appear in the DB column") {
                storedHash shouldNotBe raw
            }
            storedHash shouldBe HASHER.hash(raw)
        }
    }

    @Test
    fun `findValid returns the record for a freshly created token`() {
        runBlocking {
            // Arrange
            val sut = createSut()
            val user = givenUser("vlad@example.com")
            val raw = sut.create(userId = user.id, ttlDays = 90)

            // Act
            val record = sut.findValid(raw)

            // Assert
            record.shouldNotBeNull()
            record.userId shouldBe user.id
            record.tokenHash shouldBe HASHER.hash(raw)
        }
    }

    @Test
    fun `findValid returns null for an unknown raw token`() {
        runBlocking {
            // Arrange
            val sut = createSut()

            // Act
            val record = sut.findValid("not-a-real-token")

            // Assert
            record.shouldBeNull()
        }
    }

    @Test
    fun `findValid returns null for an expired token`() {
        runBlocking {
            // Arrange
            val sut = createSut()
            val user = givenUser("vlad@example.com")
            val raw = sut.create(userId = user.id, ttlDays = 1)
            backdateTokensFor(user.id, daysAgo = 2)

            // Act
            val record = sut.findValid(raw)

            // Assert
            record.shouldBeNull()
        }
    }

    @Test
    fun `delete removes the row`() {
        runBlocking {
            // Arrange
            val sut = createSut()
            val user = givenUser("vlad@example.com")
            val raw = sut.create(userId = user.id, ttlDays = 90)
            val record = sut.findValid(raw).shouldNotBeNull()

            // Act
            sut.delete(record.id)

            // Assert
            sut.findValid(raw).shouldBeNull()
        }
    }

    @Test
    fun `deleteAllForUser removes only the target user's tokens`() {
        runBlocking {
            // Arrange
            val sut = createSut()
            val a = givenUser("a@example.com")
            val b = givenUser("b@example.com")
            val tokenA = sut.create(userId = a.id, ttlDays = 90)
            val tokenB = sut.create(userId = b.id, ttlDays = 90)

            // Act
            sut.deleteAllForUser(a.id)

            // Assert
            sut.findValid(tokenA).shouldBeNull()
            withClue("other users' tokens must not be affected by deleteAllForUser") {
                sut.findValid(tokenB).shouldNotBeNull()
            }
        }
    }

    @Test
    fun `deleting a user cascades to their refresh tokens`() {
        runBlocking {
            // Arrange
            val sut = createSut()
            val user = givenUser("vlad@example.com")
            val raw = sut.create(userId = user.id, ttlDays = 90)

            // Act
            deleteUser(user.id)

            // Assert
            withClue("ReferenceOption.CASCADE on user_id must remove orphan tokens") {
                sut.findValid(raw).shouldBeNull()
            }
        }
    }

    private fun createSut() = RefreshTokenRepository(factory, HASHER)

    private suspend fun givenUser(email: String) = users.create(email, "hash", "User")

    private suspend fun readStoredHashFor(userId: Long): String =
        factory.dbQuery {
            RefreshTokensTable
                .selectAll()
                .where { RefreshTokensTable.userId eq userId }
                .single()[RefreshTokensTable.token]
        }

    private suspend fun backdateTokensFor(
        userId: Long,
        daysAgo: Long,
    ) {
        factory.dbQuery {
            RefreshTokensTable.update({ RefreshTokensTable.userId eq userId }) {
                it[RefreshTokensTable.expiresAt] = Clock.System.now().minus(daysAgo.days)
            }
        }
    }

    private suspend fun deleteUser(id: Long) {
        factory.dbQuery {
            UsersTable.deleteWhere { UsersTable.id eq id }
        }
    }

    companion object {
        private val HASHER = TokenHasher(pepper = "test-pepper")
    }
}
