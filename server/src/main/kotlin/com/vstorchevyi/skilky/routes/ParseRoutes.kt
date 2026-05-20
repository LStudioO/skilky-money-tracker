package com.vstorchevyi.skilky.routes

import com.vstorchevyi.skilky.ai.TextParsingService
import com.vstorchevyi.skilky.api.ApiRoutes
import com.vstorchevyi.skilky.api.Currency
import com.vstorchevyi.skilky.api.ParseTextRequest
import com.vstorchevyi.skilky.errors.ValidationException
import com.vstorchevyi.skilky.plugins.ParseRateLimit
import com.vstorchevyi.skilky.plugins.jwtAuthName
import com.vstorchevyi.skilky.security.validateParseAudioBytes
import com.vstorchevyi.skilky.security.validateParseReceiptBytes
import com.vstorchevyi.skilky.security.validateParseTextRequest
import io.ktor.http.content.MultiPartData
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.request.receive
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.utils.io.toByteArray

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
            route(ApiRoutes.Parse.TEXT) {
                post {
                    val user = call.requireJwtPrincipal()
                    val body = call.receive<ParseTextRequest>()
                    validateParseTextRequest(body)
                    val response = textParsingService.parseText(body, user.userId)
                    call.respond(response)
                }
            }
            route(ApiRoutes.Parse.AUDIO) {
                post {
                    val user = call.requireJwtPrincipal()
                    val multipart = call.receiveMultipart().readForm()
                    val bytes = multipart.fileBytes ?: throw ValidationException("Missing 'file' part")
                    val currency = multipart.requireCurrency()
                    validateParseAudioBytes(bytes)
                    val response = textParsingService.parseAudio(bytes, currency, user.userId)
                    call.respond(response)
                }
            }
            route(ApiRoutes.Parse.RECEIPT) {
                post {
                    val user = call.requireJwtPrincipal()
                    val multipart = call.receiveMultipart().readForm()
                    val bytes = multipart.fileBytes ?: throw ValidationException("Missing 'file' part")
                    val currency = multipart.requireCurrency()
                    validateParseReceiptBytes(bytes)
                    val response = textParsingService.parseReceipt(bytes, currency, user.userId)
                    call.respond(response)
                }
            }
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
