package com.vstorchevyi.skilky.plugins

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.callid.callId
import io.ktor.server.plugins.callid.callIdMdc
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import org.slf4j.event.Level

/**
 * One log line per request under `/api`, plus call-ID propagation into
 * MDC so any nested log lines (Hikari, JDBC, route code) inherit the
 * same ID.
 *
 * Health and liveness probes live outside `/api` so orchestrator polling
 * does not drown the log.
 */
fun Application.configureCallLogging() {
    install(CallLogging) {
        level = Level.INFO
        filter { call -> call.request.path().startsWith("/api") }
        callIdMdc(MDC_KEY)
        format { call ->
            val status = call.response.status()?.value ?: "-"
            val method = call.request.httpMethod.value
            val path = call.request.path()
            val id = call.callId ?: "-"
            "$status $method $path id=$id"
        }
    }
}
