package com.vstorchevyi.skilky.ai

import io.ktor.server.application.Application
import io.ktor.util.AttributeKey

/**
 * Test-side hook: an `application { attributes.put(...) }` block before
 * [com.vstorchevyi.skilky.module] runs replaces the real Ollama-backed
 * service with a stub. Production code never puts to this key, so
 * `module()` falls back to building from [com.vstorchevyi.skilky.config.AppConfig.ai].
 *
 * Kept here (not in the test source set) because [Application.attributes]
 * is a runtime-level mechanism shared by both sides; the alternative
 * would be a wider refactor of `module()`'s signature.
 */
val TextParsingServiceOverrideKey: AttributeKey<TextParsingService> =
    AttributeKey("skilky.textParsingServiceOverride")

internal fun Application.parseServiceOverride(): TextParsingService? =
    if (attributes.contains(TextParsingServiceOverrideKey)) {
        attributes[TextParsingServiceOverrideKey]
    } else {
        null
    }
