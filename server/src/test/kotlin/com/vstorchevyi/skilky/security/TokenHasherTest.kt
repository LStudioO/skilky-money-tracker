package com.vstorchevyi.skilky.security

import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldMatch
import kotlin.test.Test

class TokenHasherTest {
    @Test
    fun `hashing the same input twice yields the same hash`() {
        // Arrange
        val sut = createSut()

        // Act
        val a = sut.hash("some-token")
        val b = sut.hash("some-token")

        // Assert
        withClue("deterministic hashing is required so we can look up by hash in the DB") {
            a shouldBe b
        }
    }

    @Test
    fun `different inputs produce different hashes`() {
        // Arrange
        val sut = createSut()

        // Act
        val a = sut.hash("token-a")
        val b = sut.hash("token-b")

        // Assert
        a shouldNotBe b
    }

    @Test
    fun `different peppers produce different hashes for the same input`() {
        // Arrange: separate SUTs with distinct peppers.
        val sut = createSut(pepper = "pepper-one")
        val other = createSut(pepper = "pepper-two")

        // Act
        val a = sut.hash("token")
        val b = other.hash("token")

        // Assert
        withClue("the pepper is the keyed defence; same input + different pepper must diverge") {
            a shouldNotBe b
        }
    }

    @Test
    fun `output is 64 lowercase hex characters`() {
        // Arrange
        val sut = createSut()

        // Act
        val hash = sut.hash("token")

        // Assert: HMAC-SHA256 emits 32 bytes; hex doubles that to 64.
        hash shouldMatch Regex("^[0-9a-f]{64}\$")
    }

    private fun createSut(pepper: String = "test-pepper") = TokenHasher(pepper = pepper)
}
