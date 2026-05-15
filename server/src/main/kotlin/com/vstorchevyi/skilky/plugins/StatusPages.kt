package com.vstorchevyi.skilky.plugins

import com.vstorchevyi.skilky.api.ApiErrorResponse
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.NotFoundException
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond

fun Application.configureStatusPages() {
    install(StatusPages) {
        exception<BadRequestException> { call, cause ->
            call.respond(
                HttpStatusCode.BadRequest,
                ApiErrorResponse.of(
                    code = "BAD_REQUEST",
                    message = cause.message ?: "Malformed request",
                ),
            )
        }
        exception<NotFoundException> { call, cause ->
            call.respond(
                HttpStatusCode.NotFound,
                ApiErrorResponse.of(
                    code = "NOT_FOUND",
                    message = cause.message ?: "Resource not found",
                ),
            )
        }
        exception<Throwable> { call, cause ->
            call.application.log.error("Unhandled exception on ${call.request.local.uri}", cause)
            call.respond(
                HttpStatusCode.InternalServerError,
                ApiErrorResponse.of(
                    code = "INTERNAL_ERROR",
                    message = "Something went wrong on the server",
                ),
            )
        }
        status(HttpStatusCode.NotFound) { call, status ->
            call.respond(
                status,
                ApiErrorResponse.of(
                    code = "NOT_FOUND",
                    message = "Route not found: ${call.request.local.uri}",
                ),
            )
        }
    }
}
