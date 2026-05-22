package com.vstorchevyi.skilky.routes

import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDate
import kotlin.test.Test

/**
 * Pure unit coverage of the trend bucket math. The route delegates date
 * grouping to these functions, so off-by-one errors at month and week
 * boundaries surface here without a database.
 */
class AnalyticsPeriodsTest {
    @Test
    fun `monthBounds returns the first and last day of the month`() {
        // Act
        val bounds = monthBounds(2026, 3)

        // Assert
        bounds shouldBe (LocalDate(2026, 3, 1) to LocalDate(2026, 3, 31))
    }

    @Test
    fun `monthBounds gives February 28 days in a non-leap year`() {
        // Act
        val bounds = monthBounds(2026, 2)

        // Assert
        bounds shouldBe (LocalDate(2026, 2, 1) to LocalDate(2026, 2, 28))
    }

    @Test
    fun `monthBounds gives February 29 days in a leap year`() {
        // Act
        val bounds = monthBounds(2028, 2)

        // Assert
        bounds shouldBe (LocalDate(2028, 2, 1) to LocalDate(2028, 2, 29))
    }

    @Test
    fun `monthlyTrendPeriods returns months oldest first ending with today's month`() {
        // Act
        val periods = monthlyTrendPeriods(today = LocalDate(2026, 5, 22), periods = 3)

        // Assert
        periods.map { it.year to it.month } shouldBe
            listOf(2026 to 3, 2026 to 4, 2026 to 5)
        periods.first().from shouldBe LocalDate(2026, 3, 1)
        periods.last().to shouldBe LocalDate(2026, 5, 31)
        periods.all { it.weekStart == null } shouldBe true
    }

    @Test
    fun `monthlyTrendPeriods crosses the year boundary`() {
        // Act
        val periods = monthlyTrendPeriods(today = LocalDate(2026, 1, 15), periods = 3)

        // Assert
        periods.map { it.year to it.month } shouldBe
            listOf(2025 to 11, 2025 to 12, 2026 to 1)
    }

    @Test
    fun `weekStartOf returns the Monday of the week`() {
        // Act — 2026-05-22 is a Friday
        val monday = weekStartOf(LocalDate(2026, 5, 22))

        // Assert
        monday shouldBe LocalDate(2026, 5, 18)
    }

    @Test
    fun `weekStartOf returns the same date when given a Monday`() {
        // Act
        val monday = weekStartOf(LocalDate(2026, 5, 18))

        // Assert
        monday shouldBe LocalDate(2026, 5, 18)
    }

    @Test
    fun `weeklyTrendPeriods returns Monday-anchored weeks oldest first`() {
        // Act
        val periods = weeklyTrendPeriods(today = LocalDate(2026, 5, 22), periods = 2)

        // Assert
        periods.map { it.weekStart } shouldBe
            listOf(LocalDate(2026, 5, 11), LocalDate(2026, 5, 18))
        periods.first().from shouldBe LocalDate(2026, 5, 11)
        periods.first().to shouldBe LocalDate(2026, 5, 17)
        periods.last().to shouldBe LocalDate(2026, 5, 24)
        periods.all { it.year == null && it.month == null } shouldBe true
    }
}
