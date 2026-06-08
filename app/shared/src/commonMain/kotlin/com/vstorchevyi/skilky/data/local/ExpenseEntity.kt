package com.vstorchevyi.skilky.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * On-disk shape of an expense. Category fields are denormalized onto the row
 * because the server's `ExpenseResponse` ships the full category inline, and
 * the list screen never wants to join. Trade-off: a category rename leaves a
 * stale snapshot until the next refresh. Acceptable while categories are
 * tiny and rarely renamed; revisit if either changes.
 *
 * `date` is stored as ISO-8601 (`2026-06-08`) so a Room TypeConverter for
 * `LocalDate` is not needed yet. Mapper does the parse/format.
 */
@Entity(
    tableName = "expenses",
    indices = [Index("dateIso"), Index("categoryId")],
)
internal data class ExpenseEntity(
    @PrimaryKey val id: Long,
    val name: String,
    val amount: Double,
    val currency: String,
    val categoryId: Long,
    val categoryName: String,
    val categoryIcon: String,
    val categoryColor: String,
    val note: String?,
    val inputType: String,
    val dateIso: String,
    val createdAtMillis: Long,
)
