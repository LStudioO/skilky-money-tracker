package com.vstorchevyi.skilky.routes

import com.vstorchevyi.skilky.api.DefaultCategoryTranslations
import io.ktor.server.application.ApplicationCall

/**
 * Normalized `en` or `uk` from the HTTP Accept-Language header (first
 * weighted language range).
 */
fun ApplicationCall.requestLanguageTag(): String {
    val raw = request.headers["Accept-Language"]
    return DefaultCategoryTranslations.normalizeLanguageTag(raw)
}
