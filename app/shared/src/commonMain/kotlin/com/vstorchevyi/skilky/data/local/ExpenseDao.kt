package com.vstorchevyi.skilky.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
internal interface ExpenseDao {
    @Query("SELECT * FROM expenses ORDER BY dateIso DESC, createdAtMillis DESC")
    fun getAll(): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses WHERE id = :id LIMIT 1")
    fun getById(id: Long): Flow<ExpenseEntity?>

    @Upsert
    suspend fun upsertAll(items: List<ExpenseEntity>)

    @Query("DELETE FROM expenses WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM expenses")
    suspend fun clear()
}
