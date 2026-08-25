package com.vstorchevyi.skilky.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
@Suppress("TooManyFunctions")
internal interface SyncQueueDao {
    @Query("SELECT * FROM sync_queue ORDER BY createdAtMillis ASC, id ASC")
    fun observeAll(): Flow<List<SyncQueueEntity>>

    @Query(
        "SELECT * FROM sync_queue " +
            "WHERE status = 'PENDING' ORDER BY createdAtMillis ASC, id ASC LIMIT :limit",
    )
    suspend fun getPending(limit: Int): List<SyncQueueEntity>

    @Query("UPDATE sync_queue SET status = 'PENDING' WHERE status = 'PROCESSING'")
    suspend fun resetProcessing()

    @Insert
    suspend fun enqueue(item: SyncQueueEntity): Long

    @Insert
    suspend fun enqueueAll(items: List<SyncQueueEntity>)

    @Upsert
    suspend fun upsertExpenses(items: List<ExpenseEntity>)

    @Query("DELETE FROM expenses WHERE id = :id")
    suspend fun deleteExpenseById(id: Long)

    @Query("UPDATE sync_queue SET status = 'PROCESSING' WHERE id = :id")
    suspend fun markProcessing(id: Long)

    @Query("UPDATE sync_queue SET status = 'PENDING' WHERE id = :id")
    suspend fun markPending(id: Long)

    @Query("UPDATE sync_queue SET retryCount = 0, status = 'PENDING' WHERE localExpenseId = :localExpenseId")
    suspend fun retry(localExpenseId: Long)

    @Query("UPDATE sync_queue SET retryCount = retryCount + 1, status = :status WHERE id = :id")
    suspend fun recordFailure(
        id: Long,
        status: String,
    )

    @Query("DELETE FROM sync_queue WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM sync_queue WHERE localExpenseId = :localExpenseId")
    suspend fun deleteByLocalExpenseId(localExpenseId: Long)

    @Transaction
    suspend fun enqueueAndCache(
        queueItems: List<SyncQueueEntity>,
        expenses: List<ExpenseEntity>,
    ) {
        enqueueAll(queueItems)
        upsertExpenses(expenses)
    }

    @Transaction
    suspend fun complete(
        queueId: Long,
        localExpenseId: Long,
        syncedExpense: ExpenseEntity,
    ) {
        upsertExpenses(listOf(syncedExpense))
        deleteExpenseById(localExpenseId)
        deleteById(queueId)
    }

    @Transaction
    suspend fun deleteQueuedExpense(localExpenseId: Long) {
        deleteExpenseById(localExpenseId)
        deleteByLocalExpenseId(localExpenseId)
    }
}
