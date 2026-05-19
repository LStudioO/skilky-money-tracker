package com.vstorchevyi.skilky.api

import kotlinx.serialization.Serializable

@Serializable
data class CategoryDto(
    val id: Long,
    val name: String,
    val icon: String,
    val color: String,
    val isDefault: Boolean,
    /** Present for server-seeded defaults; stable across locales (see [DefaultCategoryKeys]). */
    val nameKey: String? = null,
)

@Serializable
data class CreateCategoryRequest(
    val name: String,
    val icon: String,
    val color: String,
)

@Serializable
data class UpdateCategoryRequest(
    val name: String,
    val icon: String,
    val color: String,
)
