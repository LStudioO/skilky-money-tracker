package com.vstorchevyi.skilky.eval

import com.vstorchevyi.skilky.ai.CategoryHint
import com.vstorchevyi.skilky.api.Currency

/**
 * Hand-graded fixtures for the eval harness. Add cases here, then run
 * `./gradlew :server:evalTest` to measure the model's precision/recall
 * before and after prompt or model changes.
 *
 * Naming convention: `kebab-case` short, descriptive — they appear in
 * the runner output so you can find a regression quickly. Inputs and
 * expectations should be small and self-explanatory; for longer
 * fixtures, prefer adding a few short ones over one giant one.
 *
 * Audio and receipt fixtures will go in [AUDIO_CASES] and
 * [RECEIPT_CASES] once we have a place to keep the binaries (see
 * `requests/voice-uk.wav` for the existing example). Until then, the
 * runner grades text only.
 */
internal object EvalFixtures {
    val CATEGORIES: List<CategoryHint> =
        listOf(
            CategoryHint(id = 1L, name = "Food"),
            CategoryHint(id = 2L, name = "Transport"),
            CategoryHint(id = 3L, name = "Groceries"),
            CategoryHint(id = 4L, name = "Gym"),
            CategoryHint(id = 5L, name = "Entertainment"),
            CategoryHint(id = 6L, name = "Health"),
            CategoryHint(id = 7L, name = "Bills"),
            CategoryHint(id = 8L, name = "Other"),
        )

    val TEXT_CASES: List<TextEvalCase> =
        listOf(
            TextEvalCase(
                name = "two-items-en",
                input = "milk 45, bread 22",
                currency = Currency.UAH,
                expected =
                    listOf(
                        ExpectedItem(name = "Milk", amount = 45.0, expectedCategoryName = "Groceries"),
                        ExpectedItem(name = "Bread", amount = 22.0, expectedCategoryName = "Groceries"),
                    ),
            ),
            TextEvalCase(
                name = "taxi-en",
                input = "taxi home 120",
                currency = Currency.UAH,
                expected =
                    listOf(
                        ExpectedItem(name = "Taxi home", amount = 120.0, expectedCategoryName = "Transport"),
                    ),
            ),
            TextEvalCase(
                name = "gym-en",
                input = "gym membership 500",
                currency = Currency.UAH,
                expected =
                    listOf(
                        ExpectedItem(name = "Gym membership", amount = 500.0, expectedCategoryName = "Gym"),
                    ),
            ),
            TextEvalCase(
                name = "two-items-uk",
                input = "хліб 22, молоко 45",
                currency = Currency.UAH,
                expected =
                    listOf(
                        ExpectedItem(name = "Хліб", amount = 22.0, expectedCategoryName = "Groceries"),
                        ExpectedItem(name = "Молоко", amount = 45.0, expectedCategoryName = "Groceries"),
                    ),
            ),
            TextEvalCase(
                name = "mixed-currency-symbol",
                input = "lunch 12.50 USD",
                currency = Currency.USD,
                expected =
                    listOf(
                        ExpectedItem(name = "Lunch", amount = 12.50, expectedCategoryName = "Food"),
                    ),
            ),
        )
}

internal data class TextEvalCase(
    val name: String,
    val input: String,
    val currency: Currency,
    val expected: List<ExpectedItem>,
)
