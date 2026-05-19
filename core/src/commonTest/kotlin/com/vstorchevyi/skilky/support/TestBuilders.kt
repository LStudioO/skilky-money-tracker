package com.vstorchevyi.skilky.support

import com.vstorchevyi.skilky.api.ApiErrorResponse

/** Same `aFoo` naming as server test [com.vstorchevyi.skilky.support.Builders]. */
fun anApiErrorResponse(
    code: String = "X",
    message: String = "y",
    details: Map<String, String> = emptyMap(),
    requestId: String? = null,
): ApiErrorResponse = ApiErrorResponse.of(code, message, details, requestId)
