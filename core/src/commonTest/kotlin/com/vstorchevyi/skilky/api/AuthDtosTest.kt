package com.vstorchevyi.skilky.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AuthDtosTest {
    @Test
    fun `register request holds its fields`() {
        val req =
            RegisterRequest(
                email = "v@example.com",
                password = "secret123",
                displayName = "Vlad",
            )
        assertEquals("v@example.com", req.email)
        assertEquals("secret123", req.password)
        assertEquals("Vlad", req.displayName)
    }

    @Test
    fun `login and refresh requests hold their fields`() {
        val login = LoginRequest(email = "v@example.com", password = "secret123")
        assertEquals("v@example.com", login.email)

        val refresh = RefreshRequest(refreshToken = "abc.def.ghi")
        assertEquals("abc.def.ghi", refresh.refreshToken)
    }

    @Test
    fun `auth response wraps user and tokens`() {
        val user =
            UserDto(
                id = 1L,
                email = "v@example.com",
                displayName = "Vlad",
                defaultCurrency = "UAH",
            )
        val auth = AuthResponse(token = "t", refreshToken = "r", user = user)
        assertEquals(1L, auth.user.id)
        assertEquals("UAH", auth.user.defaultCurrency)
        assertEquals("t", auth.token)
        assertEquals("r", auth.refreshToken)
    }

    @Test
    fun `health response defaults db to null`() {
        val health = HealthResponse(status = "ok", version = "1.0.0")
        assertEquals("ok", health.status)
        assertEquals("1.0.0", health.version)
        assertNull(health.db)
    }
}
