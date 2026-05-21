package com.vstorchevyi.skilky.eval

import com.vstorchevyi.skilky.ai.OllamaClient
import com.vstorchevyi.skilky.ai.TextParsingService
import com.vstorchevyi.skilky.api.ParseTextRequest
import com.vstorchevyi.skilky.config.AppConfig
import com.vstorchevyi.skilky.errors.AiUnavailableException
import kotlinx.coroutines.runBlocking
import kotlin.system.exitProcess

/**
 * Standalone eval runner: hits a real Ollama, grades the fixtures in
 * [EvalFixtures], prints per-case scores, and exits 1 if the mean F1
 * drops below [MIN_F1].
 *
 * Run via `./gradlew :server:evalTest`. Reads `AI_BASE_URL` and
 * `AI_MODEL` from the environment (same names as the server config),
 * with the dev defaults for the local docker-compose stack.
 */
internal fun main() {
    val baseUrl = System.getenv("AI_BASE_URL") ?: "http://localhost:11434"
    val model = System.getenv("AI_MODEL") ?: "gemma4:e4b"
    println("Eval harness against Ollama at $baseUrl, model $model")
    println()

    val client =
        OllamaClient(
            AppConfig.AiConfig(
                baseUrl = baseUrl,
                model = model,
                timeoutSeconds = EVAL_TIMEOUT_SECONDS,
                keepAlive = "30m",
            ),
        )
    val service =
        TextParsingService(
            ollamaClient = client,
            loadCategories = { _ -> EvalFixtures.CATEGORIES },
        )

    val results =
        try {
            runBlocking { gradeAll(service) }
        } finally {
            client.close()
        }

    summarise(results)
}

private suspend fun gradeAll(service: TextParsingService): List<EvalResult> {
    val out = mutableListOf<EvalResult>()
    for (case in EvalFixtures.TEXT_CASES) {
        val grade =
            try {
                val response =
                    service.parseText(
                        ParseTextRequest(text = case.input, currency = case.currency),
                        userId = 0L,
                    )
                EvalGrader.grade(case.expected, response.items)
            } catch (cause: AiUnavailableException) {
                System.err.println("[${case.name}] AI unavailable: ${cause.message}")
                exitProcess(EXIT_AI_UNAVAILABLE)
            }
        out += EvalResult(name = case.name, modality = "text", grade = grade)
        println(formatRow(case.name, grade))
    }
    return out
}

private fun summarise(results: List<EvalResult>) {
    if (results.isEmpty()) {
        System.err.println("No fixtures graded — nothing to summarise.")
        exitProcess(EXIT_NO_FIXTURES)
    }
    val meanF1 = results.map { it.grade.f1 }.average()
    val meanCat = results.map { it.grade.categoryAccuracy }.average()
    println()
    println(
        "Mean over ${results.size} cases: F1=${formatRate(meanF1)} " +
            "category-accuracy=${formatRate(meanCat)} (F1 threshold=${formatRate(MIN_F1)})",
    )
    if (meanF1 < MIN_F1) {
        System.err.println("FAIL: mean F1 below threshold.")
        exitProcess(EXIT_REGRESSION)
    }
}

private fun formatRow(
    name: String,
    grade: Grade,
): String =
    "[text] %-24s P=%s R=%s F1=%s cat=%s".format(
        name,
        formatRate(grade.precision),
        formatRate(grade.recall),
        formatRate(grade.f1),
        formatRate(grade.categoryAccuracy),
    )

private fun formatRate(value: Double): String = "%.2f".format(value)

internal data class EvalResult(
    val name: String,
    val modality: String,
    val grade: Grade,
)

private const val MIN_F1: Double = 0.70
private const val EVAL_TIMEOUT_SECONDS: Int = 120
private const val EXIT_REGRESSION: Int = 1
private const val EXIT_AI_UNAVAILABLE: Int = 2
private const val EXIT_NO_FIXTURES: Int = 3
