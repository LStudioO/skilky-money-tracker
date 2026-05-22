package com.vstorchevyi.skilky.routes

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/**
 * One trend bucket: an inclusive `[from, to]` date range plus its label. Monthly
 * buckets set [year]/[month]; weekly buckets set [weekStart]. The unused fields
 * stay null so the bucket maps cleanly onto the wire `TrendPoint`.
 */
internal data class TrendPeriod(
    val from: LocalDate,
    val to: LocalDate,
    val year: Int? = null,
    val month: Int? = null,
    val weekStart: LocalDate? = null,
)

/** First and last day of the given calendar month. */
internal fun monthBounds(
    year: Int,
    month: Int,
): Pair<LocalDate, LocalDate> {
    val first = LocalDate(year, month, 1)
    val last = first.plus(1, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY)
    return first to last
}

/**
 * The [periods] most recent calendar months, oldest first, ending with the
 * month containing [today].
 */
internal fun monthlyTrendPeriods(
    today: LocalDate,
    periods: Int,
): List<TrendPeriod> {
    val anchor = LocalDate(today.year, today.month, 1)
    return (periods - 1 downTo 0).map { back ->
        val monthStart = anchor.minus(back, DateTimeUnit.MONTH)
        val (from, to) = monthBounds(monthStart.year, monthStart.monthNumber)
        TrendPeriod(from = from, to = to, year = monthStart.year, month = monthStart.monthNumber)
    }
}

/** The Monday of the week containing [date]. */
internal fun weekStartOf(date: LocalDate): LocalDate = date.minus(date.dayOfWeek.ordinal, DateTimeUnit.DAY)

/**
 * The [periods] most recent Monday-anchored weeks, oldest first, ending with
 * the week containing [today].
 */
internal fun weeklyTrendPeriods(
    today: LocalDate,
    periods: Int,
): List<TrendPeriod> {
    val currentWeek = weekStartOf(today)
    return (periods - 1 downTo 0).map { back ->
        val start = currentWeek.minus(back * DAYS_PER_WEEK, DateTimeUnit.DAY)
        val end = start.plus(DAYS_PER_WEEK - 1, DateTimeUnit.DAY)
        TrendPeriod(from = start, to = end, weekStart = start)
    }
}

private const val DAYS_PER_WEEK = 7
