package com.vstorchevyi.skilky.domain.model

import com.vstorchevyi.skilky.api.Currency
import com.vstorchevyi.skilky.api.InputType
import kotlinx.datetime.LocalDate
import kotlin.time.Instant

/**
 * A single recorded expense. [category] is a snapshot of the category at the
 * time the server returned the row, so the list screen renders without a
 * second lookup. A later refresh propagates renames.
 */
data class Expense(
    val id: Long,
    val name: String,
    val amount: Double,
    val currency: Currency,
    val category: ExpenseCategorySnapshot,
    val note: String?,
    val inputType: InputType,
    val date: LocalDate,
    val createdAt: Instant,
    val syncStatus: ExpenseSyncStatus = ExpenseSyncStatus.SYNCED,
) {
    /** Unsynced rows use local negative IDs until sync replaces them. */
    val isPending: Boolean
        get() = syncStatus != ExpenseSyncStatus.SYNCED
}

enum class ExpenseSyncStatus {
    SYNCED,
    PENDING,
    PROCESSING,
    FAILED,
}

/**
 * The subset of [Category] that travels embedded inside an [Expense]. Kept
 * separate so the domain doesn't depend on the local-cache packaging choice
 * of denormalizing category fields onto the expense row.
 */
data class ExpenseCategorySnapshot(
    val id: Long,
    val name: String,
    val icon: String,
    val color: String,
)
