package com.vstorchevyi.skilky.api

import com.vstorchevyi.skilky.support.anApiErrorResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ApiErrorResponseTest {
    @Test
    fun `of builds envelope with all fields`() {
        // Arrange
        val details = mapOf("email" to "missing", "password" to "too short")

        // Act
        val result =
            anApiErrorResponse(
                code = "VALIDATION_ERROR",
                message = "bad input",
                details = details,
                requestId = "req-42",
            )

        // Assert
        assertEquals("VALIDATION_ERROR", result.error.code)
        assertEquals("bad input", result.error.message)
        assertEquals("missing", result.error.details["email"])
        assertEquals("too short", result.error.details["password"])
        assertEquals("req-42", result.requestId)
    }

    @Test
    fun `of defaults details to empty and requestId to null`() {
        // Arrange — only required code/message; optional fields use type defaults.

        // Act
        val result = anApiErrorResponse(code = "X", message = "y")

        // Assert
        assertTrue(result.error.details.isEmpty())
        assertNull(result.requestId)
    }

    @Test
    fun `direct construction matches of helper output`() {
        // Arrange
        val code = "A"
        val message = "B"

        // Act
        val fromFactory = anApiErrorResponse(code = code, message = message)
        val fromConstructor = ApiErrorResponse(ApiErrorResponse.ErrorDetail(code, message))

        // Assert
        assertEquals(fromFactory, fromConstructor)
    }
}
