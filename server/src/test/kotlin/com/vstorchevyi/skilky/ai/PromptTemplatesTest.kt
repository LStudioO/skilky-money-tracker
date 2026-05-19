package com.vstorchevyi.skilky.ai

import com.vstorchevyi.skilky.api.Currency
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test

class PromptTemplatesTest {
    @Test
    fun `text system prompt enumerates every supplied category by name`() {
        val cats =
            listOf(
                CategoryHint(id = 1, name = "Food"),
                CategoryHint(id = 2, name = "Transport"),
                CategoryHint(id = 9, name = "Gym"),
            )

        val prompt = PromptTemplates.systemPromptText(cats)

        prompt shouldContain "Food"
        prompt shouldContain "Transport"
        prompt shouldContain "Gym"
    }

    @Test
    fun `system prompts do not leak category ids to the model`() {
        // Use an id unlikely to appear coincidentally in any example
        // string baked into the prompt (e.g. "420Г" in a receipt
        // example would otherwise match "42" as a substring).
        val cats = listOf(CategoryHint(id = 999_777, name = "Food"))

        PromptTemplates.systemPromptText(cats) shouldNotContain "999777"
        PromptTemplates.systemPromptAudio(cats) shouldNotContain "999777"
        PromptTemplates.systemPromptReceipt(cats) shouldNotContain "999777"
    }

    @Test
    fun `all system prompts instruct JSON-only output without markdown fences`() {
        val cats = listOf(CategoryHint(id = 1, name = "Food"))

        for (
        prompt in
        listOf(
            PromptTemplates.systemPromptText(cats),
            PromptTemplates.systemPromptAudio(cats),
            PromptTemplates.systemPromptReceipt(cats),
        )
        ) {
            prompt shouldContain "JSON"
            prompt shouldContain "markdown"
        }
    }

    @Test
    fun `audio system prompt asks for a transcript and text prompt does not`() {
        val cats = listOf(CategoryHint(id = 1, name = "Food"))

        PromptTemplates.systemPromptAudio(cats) shouldContain "transcript"
        PromptTemplates.systemPromptText(cats) shouldNotContain "transcript"
        PromptTemplates.systemPromptReceipt(cats) shouldNotContain "transcript"
    }

    @Test
    fun `text user prompt includes both currency code and input text`() {
        val rendered = PromptTemplates.userPromptText(text = "milk 45", currency = Currency.UAH)

        rendered shouldContain "Currency: UAH"
        rendered shouldContain "milk 45"
    }

    @Test
    fun `audio and receipt user prompts include the currency code`() {
        PromptTemplates.userPromptAudio(Currency.UAH) shouldContain "UAH"
        PromptTemplates.userPromptReceipt(Currency.USD) shouldContain "USD"
    }

    @Test
    fun `text response schema requires items array on the root and name plus amount per item`() {
        val schema = PromptTemplates.responseSchemaText

        val rootRequired = schema["required"]?.jsonArray?.map { it.jsonPrimitive.content }
        rootRequired shouldBe listOf("items")

        val itemRequired =
            schema["properties"]
                ?.jsonObject
                ?.get("items")
                ?.jsonObject
                ?.get("items")
                ?.jsonObject
                ?.get("required")
                ?.jsonArray
                ?.map { it.jsonPrimitive.content }
        itemRequired shouldBe listOf("name", "amount")
    }

    @Test
    fun `audio response schema exposes a top-level transcript property`() {
        val schema = PromptTemplates.responseSchemaAudio

        val hasTranscript =
            schema["properties"]?.jsonObject?.containsKey("transcript")
        hasTranscript shouldBe true
    }
}
