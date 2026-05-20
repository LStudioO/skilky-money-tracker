package com.vstorchevyi.skilky.routes

import com.vstorchevyi.skilky.ai.ParseStreamEvent
import com.vstorchevyi.skilky.ai.TextParsingService
import com.vstorchevyi.skilky.api.ApiRoutes
import com.vstorchevyi.skilky.api.Currency
import com.vstorchevyi.skilky.api.ParseTextRequest
import com.vstorchevyi.skilky.api.ParseTextResponse
import com.vstorchevyi.skilky.errors.AiUnavailableException
import com.vstorchevyi.skilky.errors.ValidationException
import com.vstorchevyi.skilky.plugins.ParseRateLimit
import com.vstorchevyi.skilky.plugins.jwtAuthName
import com.vstorchevyi.skilky.security.validateParseAudioBytes
import com.vstorchevyi.skilky.security.validateParseReceiptBytes
import com.vstorchevyi.skilky.security.validateParseTextRequest
import io.ktor.http.ContentType
import io.ktor.http.content.MultiPartData
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.request.receive
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.respond
import io.ktor.server.response.respondTextWriter
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.utils.io.toByteArray
import kotlinx.coroutines.flow.collect
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * JWT-protected parse endpoints at
 * [com.vstorchevyi.skilky.api.ApiRoutes.Parse].
 *
 * Errors from [textParsingService] bubble through `StatusPages`:
 *  - [com.vstorchevyi.skilky.errors.ValidationException] from the input
 *    validators → 422.
 *  - [com.vstorchevyi.skilky.errors.AiUnavailableException] from the
 *    underlying Ollama call → 503.
 *
 * `/parse/audio` and `/parse/receipt` are `multipart/form-data`. The
 * `file` part carries the binary; `currency` is a form field.
 */
fun Route.parseRoutes(textParsingService: TextParsingService) {
    authenticate(jwtAuthName()) {
        rateLimit(ParseRateLimit) {
            parseTextRoute(textParsingService)
            parseTextStreamRoute(textParsingService)
            parseAudioRoute(textParsingService)
            parseReceiptRoute(textParsingService)
        }
    }
}

private fun Route.parseTextRoute(textParsingService: TextParsingService) {
    route(ApiRoutes.Parse.TEXT) {
        post {
            val user = call.requireJwtPrincipal()
            val body = call.receive<ParseTextRequest>()
            validateParseTextRequest(body)
            call.respond(textParsingService.parseText(body, user.userId))
        }
    }
}

private fun Route.parseTextStreamRoute(textParsingService: TextParsingService) {
    route(ApiRoutes.Parse.TEXT_STREAM) {
        post {
            val user = call.requireJwtPrincipal()
            val body = call.receive<ParseTextRequest>()
            validateParseTextRequest(body)
            call.respondTextWriter(contentType = SSE_CONTENT_TYPE) {
                try {
                    textParsingService.streamParseText(body, user.userId).collect { event ->
                        writeStreamEvent(event)
                    }
                } catch (cause: AiUnavailableException) {
                    // SSE response has already started, so we can't switch status codes;
                    // emit an error event so the client surfaces the failure to the user.
                    writeSseEvent(
                        "error",
                        SSE_JSON.encodeToString(String.serializer(), cause.message ?: "AI unavailable"),
                    )
                }
            }
        }
    }
}

private fun java.io.Writer.writeStreamEvent(event: ParseStreamEvent) {
    when (event) {
        is ParseStreamEvent.Token -> {
            writeSseEvent(
                "token",
                SSE_JSON.encodeToString(String.serializer(), event.content),
            )
        }

        is ParseStreamEvent.Done -> {
            writeSseEvent(
                "done",
                SSE_JSON.encodeToString(ParseTextResponse.serializer(), event.response),
            )
        }
    }
}

private fun Route.parseAudioRoute(textParsingService: TextParsingService) {
    route(ApiRoutes.Parse.AUDIO) {
        post {
            val user = call.requireJwtPrincipal()
            val multipart = call.receiveMultipart().readForm()
            val bytes = multipart.fileBytes ?: throw ValidationException("Missing 'file' part")
            val currency = multipart.requireCurrency()
            validateParseAudioBytes(bytes)
            call.respond(textParsingService.parseAudio(bytes, currency, user.userId))
        }
    }
}

private fun Route.parseReceiptRoute(textParsingService: TextParsingService) {
    route(ApiRoutes.Parse.RECEIPT) {
        post {
            val user = call.requireJwtPrincipal()
            val multipart = call.receiveMultipart().readForm()
            val bytes = multipart.fileBytes ?: throw ValidationException("Missing 'file' part")
            val currency = multipart.requireCurrency()
            validateParseReceiptBytes(bytes)
            call.respond(textParsingService.parseReceipt(bytes, currency, user.userId))
        }
    }
}

private data class ParseFormParts(
    val fileBytes: ByteArray?,
    val currencyCode: String?,
) {
    fun requireCurrency(): Currency =
        Currency.fromCode(
            currencyCode ?: throw ValidationException("Missing 'currency' part"),
        ) ?: throw ValidationException("Unknown currency code: $currencyCode")
}

private val SSE_CONTENT_TYPE: ContentType = ContentType.parse("text/event-stream")

private val SSE_JSON: Json =
    Json {
        encodeDefaults = true
        explicitNulls = false
    }

private fun java.io.Writer.writeSseEvent(
    name: String,
    data: String,
) {
    write("event: $name\n")
    write("data: $data\n\n")
    flush()
}

private suspend fun MultiPartData.readForm(): ParseFormParts {
    var fileBytes: ByteArray? = null
    var currencyCode: String? = null
    forEachPart { part ->
        when (part) {
            is PartData.FileItem -> fileBytes = part.provider().toByteArray()
            is PartData.FormItem -> if (part.name == "currency") currencyCode = part.value
            is PartData.BinaryItem, is PartData.BinaryChannelItem -> Unit
        }
        part.dispose()
    }
    return ParseFormParts(fileBytes = fileBytes, currencyCode = currencyCode)
}
