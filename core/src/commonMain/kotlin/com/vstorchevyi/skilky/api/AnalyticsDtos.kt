package com.vstorchevyi.skilky.api

import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Spending summary for one calendar month, aggregated in a single [currency].
 * `grandTotal` is the sum of [totalByCategory] amounts.
 */
@Serializable
data class MonthlySummaryResponse(
    val year: Int,
    val month: Int,
    val currency: Currency,
    val grandTotal: Double,
    val totalByCategory: List<CategoryTotal>,
)

/** One category's total spend within a summary period. */
@Serializable
data class CategoryTotal(
    val category: String,
    val amount: Double,
)

/**
 * One category's slice of a date-range breakdown. [percentage] is the share of
 * the range's total spend, rounded to one decimal; [count] is the number of
 * expenses in the category.
 */
@Serializable
data class CategoryBreakdownItem(
    val category: String,
    val amount: Double,
    val percentage: Double,
    val count: Int,
)

/** Bucket size for a spending trend. */
@Serializable
enum class TrendGranularity {
    @SerialName("weekly")
    WEEKLY,

    @SerialName("monthly")
    MONTHLY,
}

/**
 * Spending trend as an ordered list of [points], oldest first. The shape of a
 * point depends on [granularity]: monthly points carry `year`/`month`, weekly
 * points carry `weekStart` (the Monday of the week).
 */
@Serializable
data class TrendResponse(
    val granularity: TrendGranularity,
    val points: List<TrendPoint>,
)

/** One bucket of a [TrendResponse]. Only the fields for the active granularity are set. */
@Serializable
data class TrendPoint(
    val year: Int? = null,
    val month: Int? = null,
    @Serializable(with = LocalDateIsoSerializer::class)
    val weekStart: LocalDate? = null,
    val total: Double,
)
