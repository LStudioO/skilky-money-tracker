package com.vstorchevyi.skilky.security

import com.vstorchevyi.skilky.api.ExpenseBatchRequest
import com.vstorchevyi.skilky.errors.ValidationException
import com.vstorchevyi.skilky.support.anExpenseBatchRequest
import com.vstorchevyi.skilky.support.anExpenseRequest
import io.kotest.assertions.throwables.shouldNotThrow
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.string.shouldContain
import kotlinx.datetime.LocalDate
import java.util.UUID
import kotlin.test.Test

class ExpenseValidatorsTest {
    @Test
    fun `validateExpenseRequest accepts a sane request`() {
        // Arrange
        val request = anExpenseRequest()

        // Act + Assert
        shouldNotThrow<ValidationException> { validateExpenseRequest(request) }
    }

    @Test
    fun `validateExpenseRequest rejects empty name`() {
        // Arrange
        val request = anExpenseRequest(name = "   ")

        // Act + Assert
        val ex = shouldThrow<ValidationException> { validateExpenseRequest(request) }
        ex.message.orEmpty() shouldContain "Expense name"
    }

    @Test
    fun `validateExpenseRequest rejects name longer than 255 chars`() {
        // Arrange
        val request = anExpenseRequest(name = "x".repeat(256))

        // Act + Assert
        shouldThrow<ValidationException> { validateExpenseRequest(request) }
    }

    @Test
    fun `validateExpenseRequest rejects zero amount`() {
        // Arrange
        val request = anExpenseRequest(amount = 0.0)

        // Act + Assert
        shouldThrow<ValidationException> { validateExpenseRequest(request) }
    }

    @Test
    fun `validateExpenseRequest rejects negative amount`() {
        // Arrange
        val request = anExpenseRequest(amount = -1.0)

        // Act + Assert
        shouldThrow<ValidationException> { validateExpenseRequest(request) }
    }

    @Test
    fun `validateExpenseRequest rejects NaN amount`() {
        // Arrange
        val request = anExpenseRequest(amount = Double.NaN)

        // Act + Assert
        shouldThrow<ValidationException> { validateExpenseRequest(request) }
    }

    @Test
    fun `validateExpenseRequest rejects infinite amount`() {
        // Arrange
        val request = anExpenseRequest(amount = Double.POSITIVE_INFINITY)

        // Act + Assert
        shouldThrow<ValidationException> { validateExpenseRequest(request) }
    }

    @Test
    fun `validateExpenseRequest rejects non-UUID clientId`() {
        // Arrange
        val request = anExpenseRequest(clientId = "not-a-uuid")

        // Act + Assert
        val ex = shouldThrow<ValidationException> { validateExpenseRequest(request) }
        ex.message.orEmpty() shouldContain "clientId"
    }

    @Test
    fun `validateExpenseRequest accepts mixed-case UUID v4`() {
        // Arrange
        val request = anExpenseRequest(clientId = UUID.randomUUID().toString().uppercase())

        // Act + Assert
        shouldNotThrow<ValidationException> { validateExpenseRequest(request) }
    }

    @Test
    fun `validateExpenseBatch rejects empty items`() {
        // Arrange
        val batch = ExpenseBatchRequest(items = emptyList())

        // Act + Assert
        val ex = shouldThrow<ValidationException> { validateExpenseBatch(batch) }
        ex.message.orEmpty() shouldContain "At least one"
    }

    @Test
    fun `validateExpenseBatch rejects more than 100 items`() {
        // Arrange
        val batch =
            anExpenseBatchRequest(
                items = List(101) { anExpenseRequest(clientId = UUID.randomUUID().toString()) },
            )

        // Act + Assert
        val ex = shouldThrow<ValidationException> { validateExpenseBatch(batch) }
        ex.message.orEmpty() shouldContain "100"
    }

    @Test
    fun `validateExpenseBatch validates each item`() {
        // Arrange
        val batch =
            anExpenseBatchRequest(
                items =
                    listOf(
                        anExpenseRequest(),
                        anExpenseRequest(amount = -5.0),
                    ),
            )

        // Act + Assert
        shouldThrow<ValidationException> { validateExpenseBatch(batch) }
    }

    @Test
    fun `validateExpenseBatch rejects a clientId shared by two items`() {
        // Arrange — both items carry the same clientId, which would coalesce
        // into one row and silently drop the second item's data
        val sharedClientId = UUID.randomUUID().toString()
        val batch =
            anExpenseBatchRequest(
                items =
                    listOf(
                        anExpenseRequest(clientId = sharedClientId),
                        anExpenseRequest(clientId = sharedClientId),
                    ),
            )

        // Act + Assert
        val ex = shouldThrow<ValidationException> { validateExpenseBatch(batch) }
        ex.message.orEmpty() shouldContain "distinct clientId"
    }

    @Test
    fun `validateCreateCategory rejects empty name`() {
        // Act + Assert
        shouldThrow<ValidationException> { validateCreateCategory("  ", "icon", "#FFFFFF") }
    }

    @Test
    fun `validateCreateCategory rejects name over 100 chars`() {
        // Act + Assert
        shouldThrow<ValidationException> {
            validateCreateCategory("x".repeat(101), "icon", "#FFFFFF")
        }
    }

    @Test
    fun `validateCreateCategory rejects empty icon`() {
        // Act + Assert
        shouldThrow<ValidationException> { validateCreateCategory("Gym", "", "#FFFFFF") }
    }

    @Test
    fun `validateCreateCategory rejects non-hex color`() {
        // Act + Assert
        shouldThrow<ValidationException> { validateCreateCategory("Gym", "icon", "blue") }
    }

    @Test
    fun `validateCreateCategory rejects short hex color`() {
        // Act + Assert
        shouldThrow<ValidationException> { validateCreateCategory("Gym", "icon", "#FFF") }
    }

    @Test
    fun `validateCreateCategory accepts a sane category`() {
        // Act + Assert
        shouldNotThrow<ValidationException> {
            validateCreateCategory("Gym", "emoji:🏋️", "#009688")
        }
    }

    @Test
    fun `parseLocalDateOrThrow returns null for null input`() {
        // Act + Assert
        kotlin.test.assertNull(parseLocalDateOrThrow(null, "from"))
    }

    @Test
    fun `parseLocalDateOrThrow returns null for blank input`() {
        // Act + Assert
        kotlin.test.assertNull(parseLocalDateOrThrow("   ", "from"))
    }

    @Test
    fun `parseLocalDateOrThrow parses a valid ISO date`() {
        // Act + Assert
        kotlin.test.assertEquals(
            LocalDate(2026, 3, 21),
            parseLocalDateOrThrow("2026-03-21", "from"),
        )
    }

    @Test
    fun `parseLocalDateOrThrow rejects malformed input with param name in message`() {
        // Act + Assert
        val ex = shouldThrow<ValidationException> { parseLocalDateOrThrow("nope", "from") }
        ex.message.orEmpty() shouldContain "from"
    }
}
