package com.vstorchevyi.skilky.eval

import com.vstorchevyi.skilky.api.ParsedExpenseItem
import kotlin.math.abs

/**
 * Per-fixture grading. Treats one expected item as matched if there is at
 * least one actual item with the same normalised name and an amount within
 * [AMOUNT_TOLERANCE] of the expected value. Greedy 1-to-1 matching means
 * an actual item only counts for one expected item; duplicates in the
 * model output show up as false positives.
 *
 * Category name is graded separately and reported but does not gate the
 * primary precision/recall (the model often invents reasonable category
 * names that don't match the user's list verbatim — that's #15's job to
 * surface, not this harness).
 */
internal object EvalGrader {
    fun grade(
        expected: List<ExpectedItem>,
        actual: List<ParsedExpenseItem>,
    ): Grade {
        val claimed = BooleanArray(actual.size)
        var truePositives = 0
        var categoryHits = 0
        for (e in expected) {
            val idx = findMatch(e, actual, claimed)
            if (idx >= 0) {
                claimed[idx] = true
                truePositives++
                if (e.expectedCategoryName != null &&
                    e.expectedCategoryName.normalise() ==
                    actual[idx].suggestedCategoryName?.normalise()
                ) {
                    categoryHits++
                }
            }
        }
        val precision = if (actual.isEmpty()) 0.0 else truePositives.toDouble() / actual.size
        val recall = if (expected.isEmpty()) 0.0 else truePositives.toDouble() / expected.size
        val f1 =
            if (precision + recall == 0.0) {
                0.0
            } else {
                2 * precision * recall / (precision + recall)
            }
        return Grade(
            precision = precision,
            recall = recall,
            f1 = f1,
            categoryAccuracy =
                if (truePositives == 0) {
                    0.0
                } else {
                    categoryHits.toDouble() / truePositives
                },
        )
    }

    private fun findMatch(
        expected: ExpectedItem,
        actual: List<ParsedExpenseItem>,
        claimed: BooleanArray,
    ): Int =
        actual
            .withIndex()
            .firstOrNull { (idx, item) ->
                !claimed[idx] &&
                    item.name.normalise() == expected.name.normalise() &&
                    abs(item.amount - expected.amount) <= AMOUNT_TOLERANCE
            }?.index ?: -1

    private fun String.normalise(): String = lowercase().trim()

    private const val AMOUNT_TOLERANCE = 0.01
}

internal data class ExpectedItem(
    val name: String,
    val amount: Double,
    val expectedCategoryName: String? = null,
)

internal data class Grade(
    val precision: Double,
    val recall: Double,
    val f1: Double,
    val categoryAccuracy: Double,
)
