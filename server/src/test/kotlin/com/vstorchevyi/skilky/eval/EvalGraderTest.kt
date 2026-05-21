package com.vstorchevyi.skilky.eval

import com.vstorchevyi.skilky.api.Currency
import com.vstorchevyi.skilky.api.ParsedExpenseItem
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class EvalGraderTest {
    @Test
    fun `exact match gives precision recall and F1 of 1`() {
        // Arrange
        val expected = listOf(anExpected("Milk", 45.0), anExpected("Bread", 22.0))
        val actual = listOf(anActual("Milk", 45.0), anActual("Bread", 22.0))

        // Act
        val grade = EvalGrader.grade(expected, actual)

        // Assert
        grade.precision shouldBe 1.0
        grade.recall shouldBe 1.0
        grade.f1 shouldBe 1.0
    }

    @Test
    fun `missing item lowers recall but keeps precision at 1`() {
        // Arrange
        val expected = listOf(anExpected("Milk", 45.0), anExpected("Bread", 22.0))
        val actual = listOf(anActual("Milk", 45.0))

        // Act
        val grade = EvalGrader.grade(expected, actual)

        // Assert
        grade.precision shouldBe 1.0
        grade.recall shouldBe 0.5
        grade.f1 shouldBe (2.0 / 3.0 plusOrMinus 0.0001)
    }

    @Test
    fun `extra hallucinated item lowers precision but keeps recall at 1`() {
        // Arrange
        val expected = listOf(anExpected("Milk", 45.0))
        val actual = listOf(anActual("Milk", 45.0), anActual("Soap", 12.0))

        // Act
        val grade = EvalGrader.grade(expected, actual)

        // Assert
        grade.precision shouldBe 0.5
        grade.recall shouldBe 1.0
    }

    @Test
    fun `amount drift inside tolerance still counts as a match`() {
        // Arrange
        val expected = listOf(anExpected("Milk", 45.00))
        val actual = listOf(anActual("Milk", 45.005))

        // Act
        val grade = EvalGrader.grade(expected, actual)

        // Assert
        grade.recall shouldBe 1.0
    }

    @Test
    fun `amount drift outside tolerance does not match`() {
        // Arrange
        val expected = listOf(anExpected("Milk", 45.00))
        val actual = listOf(anActual("Milk", 45.50))

        // Act
        val grade = EvalGrader.grade(expected, actual)

        // Assert
        grade.recall shouldBe 0.0
        grade.precision shouldBe 0.0
    }

    @Test
    fun `category accuracy counts only matched items`() {
        // Arrange
        val expected =
            listOf(
                anExpected("Milk", 45.0, expectedCategoryName = "Groceries"),
                anExpected("Bread", 22.0, expectedCategoryName = "Groceries"),
            )
        val actual =
            listOf(
                anActual("Milk", 45.0, suggestedCategoryName = "Groceries"),
                anActual("Bread", 22.0, suggestedCategoryName = "Food"),
            )

        // Act
        val grade = EvalGrader.grade(expected, actual)

        // Assert
        grade.categoryAccuracy shouldBe 0.5
    }

    @Test
    fun `duplicate names are matched greedily one-to-one`() {
        // Arrange
        val expected = listOf(anExpected("Milk", 45.0), anExpected("Milk", 30.0))
        val actual = listOf(anActual("Milk", 45.0), anActual("Milk", 30.0))

        // Act
        val grade = EvalGrader.grade(expected, actual)

        // Assert
        grade.precision shouldBe 1.0
        grade.recall shouldBe 1.0
    }

    private fun anExpected(
        name: String,
        amount: Double,
        expectedCategoryName: String? = null,
    ) = ExpectedItem(name = name, amount = amount, expectedCategoryName = expectedCategoryName)

    private fun anActual(
        name: String,
        amount: Double,
        suggestedCategoryName: String? = null,
    ) = ParsedExpenseItem(
        name = name,
        amount = amount,
        currency = Currency.UAH,
        suggestedCategoryId = null,
        suggestedCategoryName = suggestedCategoryName,
        confidence = 0.9,
    )
}
