package com.vstorchevyi.skilky.domain.model

/** A signed-in [user] plus the tokens that authenticate their requests. */
data class AuthSession(
    val accessToken: String,
    val refreshToken: String,
    val user: User,
)
