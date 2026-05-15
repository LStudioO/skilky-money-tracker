package com.vstorchevyi.skilky.api

import kotlinx.serialization.Serializable

@Serializable
data class ApiErrorResponse(
    val error: ErrorDetail,
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
        ): ApiErrorResponse = ApiErrorResponse(ErrorDetail(code, message, details))
    }
}
