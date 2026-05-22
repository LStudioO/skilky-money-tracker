package com.vstorchevyi.skilky.routes

import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import java.math.BigDecimal
import kotlin.test.Test

/**
 * Pure unit coverage of [allocatePercentages]. The contract that matters for
 * the breakdown endpoint is that rounded percentages always sum to exactly
 * 100.0, which independent per-category rounding cannot guarantee.
 */
class AnalyticsBreakdownTest {
    @Test
    fun `empty input yields an empty result`() {
        // Act
        val percentages = allocatePercentages(emptyList(), BigDecimal.ZERO)

        // Assert
        percentages shouldBe emptyList()
    }

    @Test
    fun `a zero total yields zero for every entry`() {
        // Act
        val percentages = allocatePercentages(listOf(BigDecimal.ZERO, BigDecimal.ZERO), BigDecimal.ZERO)

        // Assert
        percentages shouldBe listOf(0.0, 0.0)
    }

    @Test
    fun `a single category gets the full 100 percent`() {
        // Act
        val percentages = allocatePercentages(listOf(BigDecimal(50)), BigDecimal(50))

        // Assert
        percentages shouldBe listOf(100.0)
    }

    @Test
    fun `two unequal shares round cleanly`() {
        // Act
        val percentages = allocatePercentages(listOf(BigDecimal(75), BigDecimal(25)), BigDecimal(100))

        // Assert
        percentages shouldBe listOf(75.0, 25.0)
    }

    @Test
    fun `three equal shares are reconciled to sum to 100`() {
        // Act — independent rounding would give 33.3 + 33.3 + 33.3 = 99.9
        val percentages =
            allocatePercentages(
                listOf(BigDecimal(10), BigDecimal(10), BigDecimal(10)),
                BigDecimal(30),
            )

        // Assert — the dropped tenth is handed to the first entry
        percentages shouldBe listOf(33.4, 33.3, 33.3)
        percentages.sum() shouldBe (100.0 plusOrMinus EPSILON)
    }

    @Test
    fun `seven equal shares still sum to exactly 100`() {
        // Act — 100 / 7 = 14.285..., a value with no clean one-decimal form
        val amounts = List(7) { BigDecimal(3) }
        val percentages = allocatePercentages(amounts, BigDecimal(21))

        // Assert
        percentages.sum() shouldBe (100.0 plusOrMinus EPSILON)
    }

    @Test
    fun `uneven decimal amounts sum to exactly 100`() {
        // Act
        val amounts = listOf(BigDecimal("3200.55"), BigDecimal("1850.20"), BigDecimal("1200.25"))
        val percentages = allocatePercentages(amounts, amounts.reduce(BigDecimal::add))

        // Assert
        percentages.sum() shouldBe (100.0 plusOrMinus EPSILON)
    }

    private companion object {
        const val EPSILON = 1e-6
    }
}
