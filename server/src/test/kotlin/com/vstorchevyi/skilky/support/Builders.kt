package com.vstorchevyi.skilky.support

import com.vstorchevyi.skilky.api.LoginRequest
import com.vstorchevyi.skilky.api.RefreshRequest
import com.vstorchevyi.skilky.api.RegisterRequest
import com.vstorchevyi.skilky.config.AppConfig
import com.vstorchevyi.skilky.domain.model.User

// Test data builders. Each function returns a valid default that
// individual tests override piecewise. Keeps test arrange-sections short
// and readable: only the parameter that matters for the test is named.
//
// Convention: a builder is `aFoo` (or `anFoo`) — reads as
// "a user", "a login request" in test prose.

fun aRegisterRequest(
    email: String = "vlad@example.com",
    password: String = "secret123",
    displayName: String = "Vlad",
) = RegisterRequest(email = email, password = password, displayName = displayName)

fun aLoginRequest(
    email: String = "vlad@example.com",
    password: String = "secret123",
) = LoginRequest(email = email, password = password)

fun aRefreshRequest(refreshToken: String = "any-token") = RefreshRequest(refreshToken = refreshToken)

fun aUser(
    id: Long = 1,
    email: String = "vlad@example.com",
    passwordHash: String = "hash",
    displayName: String = "Vlad",
    defaultCurrency: String = "UAH",
) = User(
    id = id,
    email = email,
    passwordHash = passwordHash,
    displayName = displayName,
    defaultCurrency = defaultCurrency,
)

fun aJwtConfig(
    secret: String = "test-secret",
    issuer: String = "skilky-tracker-test",
    audience: String = "skilky-users-test",
    accessTokenExpirationDays: Int = 7,
    refreshTokenExpirationDays: Int = 90,
) = AppConfig.JwtConfig(
    secret = secret,
    issuer = issuer,
    audience = audience,
    accessTokenExpirationDays = accessTokenExpirationDays,
    refreshTokenExpirationDays = refreshTokenExpirationDays,
)
