package com.vstorchevyi.skilky.api

import kotlinx.serialization.Serializable

@Serializable
data class RegisterRequest(
    val email: String,
    val password: String,
    val displayName: String,
)

@Serializable
data class LoginRequest(
    val email: String,
    val password: String,
)

@Serializable
data class RefreshRequest(
    val refreshToken: String,
)

@Serializable
data class UserDto(
    val id: Long,
    val email: String,
    val displayName: String,
    val defaultCurrency: String,
)

@Serializable
data class AuthResponse(
    val token: String,
    val refreshToken: String,
    val user: UserDto,
)
