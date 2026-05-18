package com.vstorchevyi.skilky.plugins

import com.vstorchevyi.skilky.api.ApiErrorResponse
import com.vstorchevyi.skilky.errors.ApiException
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.NotFoundException
import io.ktor.server.plugins.callid.callId
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond

/**
 * Global error handler. Route handlers throw, this renders the envelope.
 *
 * Handlers are matched most-specific first:
 *  - [ApiException]: our sealed family; each subclass carries its own
 *    status + machine-readable code.
 *  - [BadRequestException]: Ktor throws this when `call.receive<T>()`
 *    cannot deserialize the body.
 *  - [NotFoundException]: raised from route code on missing entities.
 *  - [Throwable]: last-resort catch. Generic message to the client; the
 *    real cause goes only to the server log. Leaking [Throwable.message]
 *    to the client can expose schema, file paths, or internal hostnames.
 *
 * The `status(NotFound)` block at the bottom is distinct from the
 * `exception<NotFoundException>` block: the former handles the case where
 * NO ROUTE MATCHED the request URI, which would otherwise produce Ktor's
 * default plain-text 404 and break the envelope contract.
 */
fun Application.configureStatusPages() {
    install(StatusPages) {
        exception<ApiException> { call, cause ->
            call.respondError(cause.httpStatus, cause.errorCode, cause.message ?: "")
        }
        exception<BadRequestException> { call, cause ->
            call.respondError(HttpStatusCode.BadRequest, "BAD_REQUEST", cause.message ?: "Malformed request")
        }
        exception<NotFoundException> { call, cause ->
            call.respondError(HttpStatusCode.NotFound, "NOT_FOUND", cause.message ?: "Resource not found")
        }
        exception<Throwable> { call, cause ->
            // Full detail to the log; generic message to the client. The
            // call ID bridges the two so support can find the real stack
            // trace from a user's error toast.
            call.application.log.error("Unhandled exception on ${call.request.local.uri}", cause)
            call.respondError(
                HttpStatusCode.InternalServerError,
                "INTERNAL_ERROR",
                "Something went wrong on the server",
            )
        }
        status(HttpStatusCode.NotFound) { call, status ->
            call.respondError(status, "NOT_FOUND", "Route not found: ${call.request.local.uri}")
        }
    }
}

private suspend fun ApplicationCall.respondError(
    status: HttpStatusCode,
    code: String,
    message: String,
) {
    respond(
        status,
        ApiErrorResponse.of(
            code = code,
            message = message,
            requestId = callId,
        ),
    )
}
