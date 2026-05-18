package com.vstorchevyi.skilky.security

import com.vstorchevyi.skilky.errors.ValidationException
import com.vstorchevyi.skilky.support.aRegisterRequest
import io.kotest.assertions.throwables.shouldNotThrow
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class ValidatorsTest {
    @Test
    fun `valid request passes`() {
        // Arrange
        val request = aRegisterRequest()

        // Act + Assert
        shouldNotThrow<ValidationException> { validateRegisterRequest(request) }
    }

    @Test
    fun `plus-tagged email is accepted`() {
        val request = aRegisterRequest(email = "vlad+test@example.com")

        shouldNotThrow<ValidationException> { validateRegisterRequest(request) }
    }

    @Test
    fun `email without at-sign is rejected`() {
        // Arrange
        val request = aRegisterRequest(email = "not-an-email")

        // Act
        val ex = shouldThrow<ValidationException> { validateRegisterRequest(request) }

        // Assert
        ex.message shouldBe "Invalid email format"
    }

    @Test
    fun `email without TLD is rejected`() {
        val request = aRegisterRequest(email = "vlad@example")

        shouldThrow<ValidationException> { validateRegisterRequest(request) }
    }

    @Test
    fun `email exceeding 254 characters is rejected`() {
        // Arrange: 255 chars, one over the cap.
        val request = aRegisterRequest(email = "a".repeat(250) + "@b.co")

        shouldThrow<ValidationException> { validateRegisterRequest(request) }
    }

    @Test
    fun `password shorter than 8 chars is rejected`() {
        val request = aRegisterRequest(password = "abc12")

        shouldThrow<ValidationException> { validateRegisterRequest(request) }
    }

    @Test
    fun `password without a letter is rejected`() {
        val request = aRegisterRequest(password = "12345678")

        shouldThrow<ValidationException> { validateRegisterRequest(request) }
    }

    @Test
    fun `password without a digit is rejected`() {
        val request = aRegisterRequest(password = "abcdefgh")

        shouldThrow<ValidationException> { validateRegisterRequest(request) }
    }

    @Test
    fun `blank display name is rejected`() {
        val request = aRegisterRequest(displayName = "   ")

        shouldThrow<ValidationException> { validateRegisterRequest(request) }
    }

    @Test
    fun `display name over 100 chars is rejected`() {
        val request = aRegisterRequest(displayName = "v".repeat(101))

        shouldThrow<ValidationException> { validateRegisterRequest(request) }
    }
}
