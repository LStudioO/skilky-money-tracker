package com.vstorchevyi.skilky.security

data class JwtUserPrincipal(
    val userId: Long,
    val email: String,
)
