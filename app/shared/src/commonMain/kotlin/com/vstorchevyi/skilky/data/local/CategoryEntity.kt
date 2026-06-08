package com.vstorchevyi.skilky.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * On-disk shape of a category. `id` mirrors the server-assigned identifier;
 * for user-created categories this is the value the create endpoint returned.
 * `nameKey` is non-null only for the 9 server-seeded defaults and is the
 * stable lookup key used by future localization (Phase 8).
 */
@Entity(tableName = "categories")
internal data class CategoryEntity(
    @PrimaryKey val id: Long,
    val name: String,
    val icon: String,
    val color: String,
    val isDefault: Boolean,
    val nameKey: String?,
    val updatedAt: Long,
)
