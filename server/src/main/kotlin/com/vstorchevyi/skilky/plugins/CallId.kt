package com.vstorchevyi.skilky.plugins

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.callid.CallId
import java.util.UUID

/**
 * Per-request correlation ID.
 *
 * Lets a single client error report (the ID is in the error envelope and
 * the `X-Request-Id` response header) trace to the exact server log lines
 * that fired during that request. Without an ID, correlating "I got an
 * error 5 minutes ago" with the log means guessing by timestamp and route.
 *
 * **Header propagation.** Inbound `X-Request-Id` (from a CDN, gateway, or
 * upstream service) is kept; only absent IDs are generated. This keeps the
 * ID stable across hops in a distributed trace.
 *
 * **Verifier.** Bounded and non-blank as a defence against newline /
 * control-character injection into log lines that include the ID.
 */
fun Application.configureCallId() {
    install(CallId) {
        header(REQUEST_ID_HEADER)
        generate { UUID.randomUUID().toString() }
        replyToHeader(REQUEST_ID_HEADER)
        verify { it.isNotBlank() && it.length <= MAX_ID_LENGTH }
    }
}

const val REQUEST_ID_HEADER = "X-Request-Id"
const val MDC_KEY = "requestId"
private const val MAX_ID_LENGTH = 128
