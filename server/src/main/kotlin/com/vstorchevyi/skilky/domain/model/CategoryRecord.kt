package com.vstorchevyi.skilky.domain.model

data class CategoryRecord(
    val id: Long,
    val name: String,
    val icon: String,
    val color: String,
    val isDefault: Boolean,
    /** `null` means a system-wide default row. */
    val ownerUserId: Long?,
    /** Stable key for seeded defaults; see [com.vstorchevyi.skilky.api.DefaultCategoryKeys]. */
    val nameKey: String?,
)
