package com.vstorchevyi.skilky.api

import kotlinx.serialization.Serializable

@Serializable
data class HealthResponse(
    val status: String,
    val version: String,
    val db: String? = null,
)
