package com.vstorchevyi.skilky.data.mapper

import com.vstorchevyi.skilky.api.AuthResponse
import com.vstorchevyi.skilky.api.UserDto
import kotlin.test.Test
import kotlin.test.assertEquals

class AuthMapperTest {
    @Test
    fun `maps AuthResponse into a domain AuthSession`() {
        // Arrange
        val response =
            AuthResponse(
                token = "access-token",
                refreshToken = "refresh-token",
                user =
                    UserDto(
                        id = 42,
                        email = "vlad@example.com",
                        displayName = "Vlad",
                        defaultCurrency = "UAH",
                    ),
            )

        // Act
        val session = response.toDomain()

        // Assert
        assertEquals("access-token", session.accessToken)
        assertEquals("refresh-token", session.refreshToken)
        assertEquals(42L, session.user.id)
        assertEquals("vlad@example.com", session.user.email)
        assertEquals("Vlad", session.user.displayName)
        assertEquals("UAH", session.user.defaultCurrency)
    }
}
