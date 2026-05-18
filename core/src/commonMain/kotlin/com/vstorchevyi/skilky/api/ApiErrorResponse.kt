package com.vstorchevyi.skilky.api

import kotlinx.serialization.Serializable

/**
 * Universal error envelope. Every non-2xx server response has this exact
 * shape so clients have a single contract to parse.
 *
 * The split between `code` and `message` is intentional:
 *  - `code` is machine-readable, never localized, and what clients should
 *    switch on. Adding new codes is non-breaking; renaming is breaking.
 *  - `message` is human-readable, may be localized later. Surfaced in dev
 *    tools and logs.
 *  - `details` is a free-form map for structured context (per-field
 *    validation errors, conflict context, etc.).
 *
 * @property requestId Per-request correlation ID set by the server's
 *   CallId plugin. Optional because not every error path is inside a
 *   request scope. Clients should display it in error UI so support
 *   reports carry the exact server log line.
 */
@Serializable
data class ApiErrorResponse(
    val error: ErrorDetail,
    val requestId: String? = null,
) {
    @Serializable
    data class ErrorDetail(
        val code: String,
        val message: String,
        val details: Map<String, String> = emptyMap(),
    )

    companion object {
        fun of(
            code: String,
            message: String,
            details: Map<String, String> = emptyMap(),
            requestId: String? = null,
        ): ApiErrorResponse = ApiErrorResponse(ErrorDetail(code, message, details), requestId)
    }
}
