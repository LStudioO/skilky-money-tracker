package com.vstorchevyi.skilky.repository

import com.vstorchevyi.skilky.api.DefaultCategories
import com.vstorchevyi.skilky.api.DefaultCategoryKeys
import com.vstorchevyi.skilky.db.DatabaseFactory
import com.vstorchevyi.skilky.db.tables.CategoriesTable
import com.vstorchevyi.skilky.db.tables.ExpensesTable
import com.vstorchevyi.skilky.domain.model.CategoryRecord
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update

/**
 * Persistence and rules for expense categories.
 *
 * **Visibility:** users see system rows (`user_id` null) plus their own. **Sort order:** defaults
 * follow [DefaultCategories.ALL] so list order is stable across devices and releases.
 *
 * **Delete safety:** removing a custom category reassigns expenses to the system **other** bucket
 * resolved by [DefaultCategoryKeys.OTHER] on the `name_key` column, never by the stored English
 * display string alone, so changing UI language cannot break the fallback.
 */
class CategoryRepository(
    private val databaseFactory: DatabaseFactory,
) {
    suspend fun listVisible(userId: Long): List<CategoryRecord> =
        databaseFactory.dbQuery {
            CategoriesTable
                .selectAll()
                .where {
                    (CategoriesTable.userId eq null) or (CategoriesTable.userId eq userId)
                }.map { it.toCategoryRecord() }
                .sortedWith(
                    compareBy(
                        { it.ownerUserId != null },
                        { r ->
                            val idx =
                                r.nameKey?.let { key ->
                                    DefaultCategories.ALL.indexOfFirst { it.key == key }
                                } ?: 1_000
                            if (idx < 0) 999 else idx
                        },
                        { it.name.lowercase() },
                    ),
                )
        }

    suspend fun findAccessible(
        categoryId: Long,
        userId: Long,
    ): CategoryRecord? =
        databaseFactory.dbQuery {
            CategoriesTable
                .selectAll()
                .where {
                    (CategoriesTable.id eq categoryId) and
                        ((CategoriesTable.userId eq null) or (CategoriesTable.userId eq userId))
                }.map { it.toCategoryRecord() }
                .singleOrNull()
        }

    suspend fun findById(categoryId: Long): CategoryRecord? =
        databaseFactory.dbQuery {
            CategoriesTable
                .selectAll()
                .where { CategoriesTable.id eq categoryId }
                .map { it.toCategoryRecord() }
                .singleOrNull()
        }

    suspend fun create(
        userId: Long,
        name: String,
        icon: String,
        color: String,
    ): CategoryRecord =
        databaseFactory.dbQuery {
            val id =
                CategoriesTable.insert {
                    it[CategoriesTable.name] = name
                    it[CategoriesTable.icon] = icon
                    it[CategoriesTable.color] = color
                    it[CategoriesTable.isDefault] = false
                    it[CategoriesTable.userId] = userId
                }[CategoriesTable.id]
                    .value
            CategoryRecord(
                id = id,
                name = name,
                icon = icon,
                color = color,
                isDefault = false,
                ownerUserId = userId,
                nameKey = null,
            )
        }

    suspend fun updateCustom(
        userId: Long,
        categoryId: Long,
        name: String,
        icon: String,
        color: String,
    ): CategoryRecord? =
        databaseFactory.dbQuery {
            val existing =
                CategoriesTable
                    .selectAll()
                    .where { CategoriesTable.id eq categoryId }
                    .map { it.toCategoryRecord() }
                    .singleOrNull()
                    ?: return@dbQuery null
            if (existing.ownerUserId == null) {
                return@dbQuery null
            }
            if (existing.ownerUserId != userId) {
                return@dbQuery null
            }
            CategoriesTable.update({ CategoriesTable.id eq categoryId }) {
                it[CategoriesTable.name] = name
                it[CategoriesTable.icon] = icon
                it[CategoriesTable.color] = color
            }
            existing.copy(name = name, icon = icon, color = color, nameKey = existing.nameKey)
        }

    /**
     * Deletes a user-owned category and moves expenses to the system
     * **Other** bucket.
     *
     * @return `false` if the row does not exist, is not owned by [userId],
     *   or is a system default.
     */
    suspend fun deleteCustomIfOwned(
        userId: Long,
        categoryId: Long,
    ): Boolean =
        databaseFactory.dbQuery {
            val existing =
                CategoriesTable
                    .selectAll()
                    .where { CategoriesTable.id eq categoryId }
                    .map { it.toCategoryRecord() }
                    .singleOrNull()
                    ?: return@dbQuery false
            if (existing.ownerUserId == null || existing.ownerUserId != userId) {
                return@dbQuery false
            }
            val otherId =
                CategoriesTable
                    .selectAll()
                    .where {
                        (CategoriesTable.userId eq null) and (CategoriesTable.nameKey eq DefaultCategoryKeys.OTHER)
                    }.map { it[CategoriesTable.id].value }
                    .singleOrNull()
                    ?: error("system category '${DefaultCategoryKeys.OTHER}' is missing — seed failed?")
            ExpensesTable.update({
                (ExpensesTable.userId eq userId) and (ExpensesTable.categoryId eq categoryId)
            }) {
                it[ExpensesTable.categoryId] = otherId
            }
            CategoriesTable.deleteWhere { CategoriesTable.id eq categoryId } > 0
        }

    private fun ResultRow.toCategoryRecord(): CategoryRecord =
        CategoryRecord(
            id = this[CategoriesTable.id].value,
            name = this[CategoriesTable.name],
            icon = this[CategoriesTable.icon],
            color = this[CategoriesTable.color],
            isDefault = this[CategoriesTable.isDefault],
            ownerUserId = this[CategoriesTable.userId]?.value,
            nameKey = this[CategoriesTable.nameKey],
        )
}
