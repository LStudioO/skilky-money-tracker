package com.vstorchevyi.skilky.db.tables

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable

/**
 * Categories visible in the API are either **system** rows (`user_id` null, seeded once) or
 * **custom** rows owned by a user. System rows carry [nameKey] for stable localization; custom
 * rows use `name` as the single source of display text.
 */
object CategoriesTable : LongIdTable("categories") {
    val name = varchar("name", length = 100)
    val icon = varchar("icon", length = 80)
    val color = varchar("color", length = 7)
    val isDefault = bool("is_default").default(false)
    val userId = reference("user_id", UsersTable, onDelete = ReferenceOption.CASCADE).nullable()

    /**
     * Stable slug for seeded rows (see [com.vstorchevyi.skilky.api.DefaultCategoryKeys]).
     * Null for user-created categories — logic must never match on English [name] alone.
     */
    val nameKey = varchar("name_key", length = 32).nullable()
}
