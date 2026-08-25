package com.vstorchevyi.skilky.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
internal interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY isDefault DESC, name COLLATE NOCASE ASC")
    fun getAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): CategoryEntity?

    @Upsert
    suspend fun upsertAll(categories: List<CategoryEntity>)

    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM categories")
    suspend fun clear()

    @Transaction
    suspend fun replaceAll(categories: List<CategoryEntity>) {
        clear()
        upsertAll(categories)
    }
}
