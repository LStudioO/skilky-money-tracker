package com.vstorchevyi.skilky.ai

import com.vstorchevyi.skilky.api.Currency
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/** Display name the LLM picks from. Server resolves the name back to a category id. */
data class CategoryHint(
    val id: Long,
    val name: String,
)

/**
 * System prompts and JSON schemas for the three parse endpoints. All
 * three target the same Gemma-class model, so the response shape stays
 * common — only the leading sentence (what the input is) differs per
 * modality, plus the audio prompt asks for an extra `transcript` field.
 *
 * The schemas are sent to Ollama via `/api/chat`'s `format` field so
 * the model returns structured JSON we can deserialize without regex
 * hacks. Each prompt enumerates the user's visible categories (defaults
 * plus any they created); the server resolves the chosen name back to a
 * real category id.
 */
object PromptTemplates {
    fun systemPromptText(categories: List<CategoryHint>): String =
        buildPrompt(
            opening = "You extract expense line items from informal user text.",
            categories = categories,
            includeTranscript = false,
        )

    fun systemPromptAudio(categories: List<CategoryHint>): String =
        buildPrompt(
            opening =
                "You extract expense line items from a short voice note. " +
                    "The user is logging expenses by speaking.",
            categories = categories,
            includeTranscript = true,
        )

    fun systemPromptReceipt(categories: List<CategoryHint>): String {
        val list = categories.joinToString(", ") { it.name }
        return """
            You extract expense line items from a photographed receipt.

            Output strictly JSON matching the schema you were given.

            Rules:
            - name: copy the printed line item label VERBATIM in its original language. Do not translate, summarise, abbreviate, or guess what the product is. If the label is "ШАМПІН МАРИН ЦІЛІ 420Г RIO", use that, not "Mushrooms" and not "Marinated mushrooms".
            - amount: the line total in the rightmost column, the user's currency. Do not round.
            - For weighted or multi-quantity rows that look like "1.312 X 74.90" followed by a label and a bigger number at the end of the row, the small number on the X line is the unit price and is NOT its own item; the number at the end of the row is the line total — use that.
            - Treat each printed line as its own item even if the label describes an accessory to another product (e.g. "КЕТЧУП ДО ШАШЛИКУ" is a separate ketchup line, not a kebab item).
            - IGNORE these lines entirely: subtotals, СУМА, ПДВ / VAT / tax lines, ГОТІВКА / CASH, РЕШТА / CHANGE, До сплати / TOTAL, Заокруглення / rounding, article count, receipt header, store address, fiscal numbers, QR codes.
            - suggestedCategoryName: pick the closest fit from this list, copied exactly: [$list]. If nothing on the list fits, leave the field empty.
            - confidence: your self-rating from 0.0 to 1.0.
            - rawText: REQUIRED. The full OCR text you read off the receipt, one line per row, preserving the original language and spacing as faithfully as you can. This is separate from `items` — do not summarise.

            If you cannot identify any expense, return {"items": [], "rawText": "<what you read>"}.
            Do not add commentary. Do not include markdown fences. Return only the JSON object.
            """.trimIndent()
    }

    fun userPromptText(
        text: String,
        currency: Currency,
    ): String =
        """
        Currency: ${currency.code}
        Text: $text
        """.trimIndent()

    fun userPromptAudio(currency: Currency): String =
        """
        Currency: ${currency.code}
        Extract expense items from the attached voice note.
        """.trimIndent()

    fun userPromptReceipt(currency: Currency): String =
        """
        Currency: ${currency.code}
        Extract expense items from the attached receipt photo.
        """.trimIndent()

    val responseSchemaText: JsonObject = responseSchema(includeTranscript = false, includeRawText = false)
    val responseSchemaAudio: JsonObject = responseSchema(includeTranscript = true, includeRawText = false)
    val responseSchemaReceipt: JsonObject = responseSchema(includeTranscript = false, includeRawText = true)

    private fun buildPrompt(
        opening: String,
        categories: List<CategoryHint>,
        includeTranscript: Boolean,
    ): String {
        val list = categories.joinToString(", ") { it.name }
        val transcriptRule =
            if (includeTranscript) {
                "\n- transcript: REQUIRED. A verbatim plain-text transcript of what the user said, " +
                    "in the SAME language they spoke. Do not translate. Do not summarise."
            } else {
                ""
            }
        return """
            $opening
            Output strictly JSON matching the schema you were given.

            Rules:
            - name: a short clean noun phrase in the SAME language as the input. NEVER translate to English; if the input is Ukrainian the name must be Ukrainian, if Polish then Polish, and so on. Capitalise the first letter.
            - amount: the exact number the user gave, including decimals (e.g. 45.99, not 46). Do not round. Do not invent amounts. If the user did not give a number for an item, skip it.
            - suggestedCategoryName: pick the closest fit from this list, copied exactly: [$list].
              If nothing on the list fits, leave the field empty.
            - confidence: your self-rating from 0.0 to 1.0.$transcriptRule

            If you cannot identify any expense, return {"items": []}.
            Do not add commentary. Do not include markdown fences. Return only the JSON object.
            """.trimIndent()
    }

    private fun responseSchema(
        includeTranscript: Boolean,
        includeRawText: Boolean,
    ): JsonObject =
        buildJsonObject {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("items") {
                    put("type", "array")
                    putJsonObject("items") {
                        put("type", "object")
                        putJsonObject("properties") {
                            putJsonObject("name") { put("type", "string") }
                            putJsonObject("amount") { put("type", "number") }
                            putJsonObject("suggestedCategoryName") { put("type", "string") }
                            putJsonObject("confidence") {
                                put("type", "number")
                                put("minimum", 0)
                                put("maximum", 1)
                            }
                        }
                        putJsonArray("required") {
                            add("name")
                            add("amount")
                        }
                    }
                }
                if (includeTranscript) {
                    putJsonObject("transcript") { put("type", "string") }
                }
                if (includeRawText) {
                    putJsonObject("rawText") { put("type", "string") }
                }
            }
            putJsonArray("required") {
                add("items")
                if (includeTranscript) add("transcript")
                if (includeRawText) add("rawText")
            }
        }
}
