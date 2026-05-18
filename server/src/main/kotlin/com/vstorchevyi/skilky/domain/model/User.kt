package com.vstorchevyi.skilky.domain.model

/**
 * Domain user. Innermost ring of the architecture: no Ktor, no Exposed, no
 * serialization imports. Wire conversion lives in
 * [com.vstorchevyi.skilky.routes.toDto]; DB conversion lives in
 * [com.vstorchevyi.skilky.repository.UserRepository].
 */
data class User(
    val id: Long,
    val email: String,
    val passwordHash: String,
    val displayName: String,
    val defaultCurrency: String,
)
