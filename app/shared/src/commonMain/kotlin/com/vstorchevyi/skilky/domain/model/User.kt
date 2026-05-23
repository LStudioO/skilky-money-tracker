package com.vstorchevyi.skilky.domain.model

/** A signed-in account, as the client domain sees it. */
data class User(
    val id: Long,
    val email: String,
    val displayName: String,
    val defaultCurrency: String,
)
