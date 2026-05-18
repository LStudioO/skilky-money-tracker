package com.vstorchevyi.skilky.security

import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlin.test.Test

class PasswordHasherTest {
    @Test
    fun `verify accepts the same password used to hash`() {
        // Arrange
        val sut = createSut()
        val password = "secret123"

        // Act
        val hash = sut.hash(password)

        // Assert
        sut.verify(password, hash) shouldBe true
    }

    @Test
    fun `verify rejects a different password`() {
        // Arrange
        val sut = createSut()
        val hash = sut.hash("secret123")

        // Act
        val verified = sut.verify("wrong-password", hash)

        // Assert
        verified shouldBe false
    }

    @Test
    fun `two hashes of the same password differ because of the salt`() {
        // Arrange
        val sut = createSut()
        val password = "secret123"

        // Act
        val a = sut.hash(password)
        val b = sut.hash(password)

        // Assert
        withClue("BCrypt embeds a fresh salt per call; the hash strings must differ") {
            a shouldNotBe b
        }
        sut.verify(password, a) shouldBe true
        sut.verify(password, b) shouldBe true
    }

    @Test
    fun `verify is case sensitive`() {
        // Arrange
        val sut = createSut()
        val hash = sut.hash("Secret123")

        // Act
        val verified = sut.verify("secret123", hash)

        // Assert
        verified shouldBe false
    }

    // BCrypt cost is configurable so tests can pin a fast value. Production
    // uses 12; the library code path is the same at any cost.
    private fun createSut(cost: Int = 4) = PasswordHasher(cost = cost)
}
