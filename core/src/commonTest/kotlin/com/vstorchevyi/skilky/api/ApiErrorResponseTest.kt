package com.vstorchevyi.skilky.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ApiErrorResponseTest {
    @Test
    fun `of builds envelope with all fields`() {
        val err =
            ApiErrorResponse.of(
                code = "VALIDATION_ERROR",
                message = "bad input",
                details = mapOf("email" to "missing", "password" to "too short"),
                requestId = "req-42",
            )
        assertEquals("VALIDATION_ERROR", err.error.code)
        assertEquals("bad input", err.error.message)
        assertEquals("missing", err.error.details["email"])
        assertEquals("too short", err.error.details["password"])
        assertEquals("req-42", err.requestId)
    }

    @Test
    fun `of defaults details to empty and requestId to null`() {
        val err = ApiErrorResponse.of(code = "X", message = "y")
        assertTrue(err.error.details.isEmpty())
        assertNull(err.requestId)
    }

    @Test
    fun `direct construction matches of() output`() {
        val viaOf = ApiErrorResponse.of(code = "A", message = "B")
        val direct = ApiErrorResponse(ApiErrorResponse.ErrorDetail("A", "B"))
        assertEquals(direct, viaOf)
    }
}
