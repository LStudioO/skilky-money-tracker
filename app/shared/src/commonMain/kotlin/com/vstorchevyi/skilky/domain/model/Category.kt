package com.vstorchevyi.skilky.domain.model

/**
 * A spending category. `isDefault` distinguishes the nine server-seeded
 * categories (immutable, undeletable) from user-created ones. `nameKey` is
 * non-null only for defaults and is the stable lookup key for localization.
 */
data class Category(
    val id: Long,
    val name: String,
    val icon: String,
    val color: String,
    val isDefault: Boolean,
    val nameKey: String? = null,
)
