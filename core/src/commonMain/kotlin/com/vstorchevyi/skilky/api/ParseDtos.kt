package com.vstorchevyi.skilky.api

import kotlinx.serialization.Serializable

/**
 * Free-form user text to extract expense items from. [currency] is the
 * default the server falls back to when the LLM cannot infer one; today
 * it is also the only currency on the response (we do not yet parse
 * mixed-currency input).
 */
@Serializable
data class ParseTextRequest(
    val text: String,
    val currency: Currency,
)

/**
 * Single parsed line.
 *
 * [suggestedCategoryId] is the id of a category the user can actually
 * see (system default OR one of their own) when the server matched the
 * LLM's pick against the user's category list, null otherwise — the
 * client then prompts the user to pick.
 *
 * [suggestedCategoryName] is the raw name the LLM produced before the
 * server matched it. The client can show it as a hint even when no id
 * matched ("the model thinks this is 'Pet food'"), so the user is not
 * starting from nothing.
 *
 * [confidence] is the LLM's self-reported score in `0.0..1.0`.
 */
@Serializable
data class ParsedExpenseItem(
    val name: String,
    val amount: Double,
    val currency: Currency,
    val suggestedCategoryId: Long? = null,
    val suggestedCategoryName: String? = null,
    val confidence: Double = 0.0,
)

/**
 * Common response for `/parse/text`, `/parse/audio`, and `/parse/receipt`.
 *
 * [transcript] is populated only by the audio endpoint: it is the text
 * the model heard the user say, so the client can show "I heard you say
 * X" in the confirmation sheet. Null for text and receipt inputs.
 *
 * [rawText] is populated only by the receipt endpoint: it is the OCR
 * text the model read off the photo, separate from the structured
 * [items] it extracted. Lets the client (and a human debugger) see
 * exactly what the model saw vs. what it returned, so a confident-but-
 * wrong item name can be verified against the ground truth. Null for
 * text and audio inputs.
 */
@Serializable
data class ParseTextResponse(
    val items: List<ParsedExpenseItem>,
    val transcript: String? = null,
    val rawText: String? = null,
)
