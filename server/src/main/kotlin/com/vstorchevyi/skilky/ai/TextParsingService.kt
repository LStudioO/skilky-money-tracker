package com.vstorchevyi.skilky.ai

import com.vstorchevyi.skilky.api.Currency
import com.vstorchevyi.skilky.api.ParseTextRequest
import com.vstorchevyi.skilky.api.ParseTextResponse
import com.vstorchevyi.skilky.api.ParsedExpenseItem
import com.vstorchevyi.skilky.errors.AiUnavailableException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Single entry point for the three parse modalities (text, audio,
 * receipt photo). Each method:
 * 1. Loads the user's visible categories (defaults plus any they made).
 * 2. Builds the right prompt and response schema from [PromptTemplates].
 * 3. Calls [OllamaClient.chatJson], attaching the modality's binary
 *    bytes to [OllamaClient.chatJson]'s `inputs` for audio and receipts.
 * 4. Maps the JSON to [ParsedExpenseItem]s, resolving
 *    [ParsedExpenseItem.suggestedCategoryName] back to a real
 *    [ParsedExpenseItem.suggestedCategoryId] via case-insensitive name
 *    match against the user's category list. Names the LLM made up land
 *    as a `null` id but the original string is still returned so the
 *    client can show it.
 *
 * Audio additionally returns the transcript the model heard.
 *
 * The category loader is passed as a function so the service stays
 * trivially constructible in tests without a database.
 */
class TextParsingService(
    private val ollamaClient: OllamaClient,
    private val loadCategories: suspend (userId: Long) -> List<CategoryHint>,
) {
    suspend fun parseText(
        request: ParseTextRequest,
        userId: Long,
    ): ParseTextResponse {
        val categories = loadCategories(userId)
        val raw =
            ollamaClient.chatJson(
                systemPrompt = PromptTemplates.systemPromptText(categories),
                userPrompt = PromptTemplates.userPromptText(request.text, request.currency),
                responseFormat = PromptTemplates.responseSchemaText,
            )
        return raw.decodeResponse(request.currency, categories)
    }

    /**
     * @param audio raw WAV bytes (16 kHz mono, RIFF header). Validation
     *   of the format happens upstream in
     *   [com.vstorchevyi.skilky.security.validateParseAudioBytes].
     */
    suspend fun parseAudio(
        audio: ByteArray,
        currency: Currency,
        userId: Long,
    ): ParseTextResponse {
        val categories = loadCategories(userId)
        val raw =
            ollamaClient.chatJson(
                systemPrompt = PromptTemplates.systemPromptAudio(categories),
                userPrompt = PromptTemplates.userPromptAudio(currency),
                responseFormat = PromptTemplates.responseSchemaAudio,
                inputs = listOf(audio),
            )
        return raw.decodeResponse(currency, categories, includeTranscript = true)
    }

    /**
     * @param image raw image bytes (JPEG or PNG). Validation of size and
     *   declared content type happens upstream in
     *   [com.vstorchevyi.skilky.security.validateParseReceiptBytes].
     */
    suspend fun parseReceipt(
        image: ByteArray,
        currency: Currency,
        userId: Long,
    ): ParseTextResponse {
        val categories = loadCategories(userId)
        val raw =
            ollamaClient.chatJson(
                systemPrompt = PromptTemplates.systemPromptReceipt(categories),
                userPrompt = PromptTemplates.userPromptReceipt(currency),
                responseFormat = PromptTemplates.responseSchemaReceipt,
                inputs = listOf(image),
            )
        return raw.decodeResponse(currency, categories, includeRawText = true)
    }

    private fun JsonObject.decodeResponse(
        currency: Currency,
        categories: List<CategoryHint>,
        includeTranscript: Boolean = false,
        includeRawText: Boolean = false,
    ): ParseTextResponse {
        val items =
            try {
                toParsedItems(currency, categories)
            } catch (cause: SerializationException) {
                throw AiUnavailableException(
                    "Ollama JSON did not match the expected schema: ${cause.message}",
                    cause,
                )
            } catch (cause: IllegalArgumentException) {
                throw AiUnavailableException(
                    "Ollama JSON did not match the expected schema: ${cause.message}",
                    cause,
                )
            }
        val transcript =
            if (includeTranscript) {
                this["transcript"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
            } else {
                null
            }
        val rawText =
            if (includeRawText) {
                this["rawText"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
            } else {
                null
            }
        return ParseTextResponse(items = items, transcript = transcript, rawText = rawText)
    }

    private fun JsonObject.toParsedItems(
        currency: Currency,
        categories: List<CategoryHint>,
    ): List<ParsedExpenseItem> {
        val byNormalisedName = categories.associateBy { it.name.normaliseForMatch() }
        val itemsArray =
            this["items"]?.jsonArray
                ?: throw IllegalArgumentException("Missing 'items' array")
        return itemsArray.map { element ->
            val obj = element.jsonObject
            val name =
                obj["name"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
                    ?: throw IllegalArgumentException("Item missing 'name'")
            val amount =
                obj["amount"]?.jsonPrimitive?.content?.toDoubleOrNull()
                    ?: throw IllegalArgumentException("Item missing or non-numeric 'amount'")
            val rawSuggested =
                obj["suggestedCategoryName"]
                    ?.jsonPrimitive
                    ?.content
                    ?.takeIf { it.isNotBlank() }
            val matched = rawSuggested?.let { byNormalisedName[it.normaliseForMatch()] }
            val confidence =
                obj["confidence"]?.jsonPrimitive?.content?.toDoubleOrNull()?.coerceIn(0.0, 1.0)
                    ?: 0.0
            ParsedExpenseItem(
                name = name,
                amount = amount,
                currency = currency,
                suggestedCategoryId = matched?.id,
                suggestedCategoryName = rawSuggested,
                confidence = confidence,
            )
        }
    }

    private fun String.normaliseForMatch(): String = lowercase().trim()
}
