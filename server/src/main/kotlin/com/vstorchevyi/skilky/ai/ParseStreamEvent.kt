package com.vstorchevyi.skilky.ai

import com.vstorchevyi.skilky.api.ParseTextResponse

/**
 * Events emitted by [TextParsingService.streamParseText]. The route
 * layer renders each one onto the wire (today: Server-Sent Events).
 */
sealed interface ParseStreamEvent {
    /** Incremental chunk of model text. Consumers concatenate to recover the full content. */
    data class Token(
        val content: String,
    ) : ParseStreamEvent

    /** Final envelope after the stream closes. */
    data class Done(
        val response: ParseTextResponse,
    ) : ParseStreamEvent
}
