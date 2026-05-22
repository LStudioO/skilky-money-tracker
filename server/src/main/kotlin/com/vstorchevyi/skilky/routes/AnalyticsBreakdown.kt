package com.vstorchevyi.skilky.routes

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Each amount's share of [total], as a percentage rounded to one decimal place.
 *
 * Rounding every share on its own lets the results drift off 100: three equal
 * shares each round to 33.3 and sum to 99.9. This uses the largest remainder
 * method instead. Shares are floored to tenths of a percent, then the leftover
 * tenths are handed to the shares with the largest dropped fraction, so the
 * result always sums to exactly 100.0.
 *
 * Returns 0.0 for every entry when [total] is zero. Output order matches
 * [amounts]. [total] is expected to be the sum of [amounts].
 */
internal fun allocatePercentages(
    amounts: List<BigDecimal>,
    total: BigDecimal,
): List<Double> {
    if (amounts.isEmpty()) return emptyList()
    if (total.signum() == 0) return List(amounts.size) { 0.0 }

    // Exact share of each amount, in tenths of a percent (0..1000).
    val exactTenths =
        amounts.map { amount ->
            amount
                .multiply(TOTAL_TENTHS)
                .divide(total, EXACT_SHARE_SCALE, RoundingMode.HALF_UP)
        }
    val flooredTenths = exactTenths.map { it.setScale(0, RoundingMode.FLOOR).toInt() }
    val leftoverTenths = TOTAL_TENTHS.toInt() - flooredTenths.sum()

    // Hand each leftover tenth to the entry with the next largest dropped
    // fraction; ties keep input order so the result is deterministic.
    val rankedByDroppedFraction =
        exactTenths.indices.sortedByDescending { index ->
            exactTenths[index].subtract(BigDecimal(flooredTenths[index]))
        }
    val bumped = rankedByDroppedFraction.take(leftoverTenths.coerceAtLeast(0)).toHashSet()

    return amounts.indices.map { index ->
        val tenths = flooredTenths[index] + if (index in bumped) 1 else 0
        tenths / TENTHS_PER_PERCENT
    }
}

private val TOTAL_TENTHS = BigDecimal(1000)
private const val EXACT_SHARE_SCALE = 10
private const val TENTHS_PER_PERCENT = 10.0
