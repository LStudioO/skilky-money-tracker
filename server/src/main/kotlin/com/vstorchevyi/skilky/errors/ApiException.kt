package com.vstorchevyi.skilky.errors

import io.ktor.http.HttpStatusCode

sealed class ApiException(
    val httpStatus: HttpStatusCode,
    val errorCode: String,
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

class ValidationException(
    message: String,
) : ApiException(HttpStatusCode.UnprocessableEntity, "VALIDATION_ERROR", message)

class ConflictException(
    message: String,
) : ApiException(HttpStatusCode.Conflict, "CONFLICT", message)

class UnauthorizedException(
    message: String = "Invalid credentials",
) : ApiException(HttpStatusCode.Unauthorized, "UNAUTHORIZED", message)

class ForbiddenException(
    message: String,
) : ApiException(HttpStatusCode.Forbidden, "FORBIDDEN", message)

/**
 * Raised when an upstream AI service (Ollama, Whisper) is unreachable or
 * returns an unrecoverable error. Maps to `503 AI_UNAVAILABLE` so clients
 * can retry or fall back to manual entry without confusing the user with
 * a generic 5xx.
 */
class AiUnavailableException(
    message: String = "AI service is unavailable",
    cause: Throwable? = null,
) : ApiException(HttpStatusCode.ServiceUnavailable, "AI_UNAVAILABLE", message, cause)
