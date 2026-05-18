package com.vstorchevyi.skilky.errors

import io.ktor.http.HttpStatusCode

sealed class ApiException(
    val httpStatus: HttpStatusCode,
    val errorCode: String,
    message: String,
) : RuntimeException(message)

class ValidationException(
    message: String,
) : ApiException(HttpStatusCode.UnprocessableEntity, "VALIDATION_ERROR", message)

class ConflictException(
    message: String,
) : ApiException(HttpStatusCode.Conflict, "CONFLICT", message)

class UnauthorizedException(
    message: String = "Invalid credentials",
) : ApiException(HttpStatusCode.Unauthorized, "UNAUTHORIZED", message)
