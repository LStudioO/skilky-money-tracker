package com.vstorchevyi.skilky.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

internal enum class SyncQueueStatus {
    PENDING,
    PROCESSING,
    FAILED,
}

/** A confirmed expense waiting to be uploaded to the server. */
@Entity(
    tableName = "sync_queue",
    indices = [
        Index(value = ["clientId"], unique = true),
        Index(value = ["localExpenseId"], unique = true),
        Index("status", "createdAtMillis"),
    ],
)
internal data class SyncQueueEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val clientId: String,
    val localExpenseId: Long,
    val name: String,
    val amount: Double,
    val currency: String,
    val categoryId: Long,
    val note: String?,
    val inputType: String,
    val dateIso: String,
    val createdAtMillis: Long,
    val retryCount: Int = 0,
    val status: String = SyncQueueStatus.PENDING.name,
)
