package com.vstorchevyi.skilky.domain.repository

import com.vstorchevyi.skilky.domain.model.AppError
import com.vstorchevyi.skilky.domain.model.Category
import com.vstorchevyi.skilky.domain.model.Either
import kotlinx.coroutines.flow.Flow

/**
 * Read-and-mutate access to the user's categories. Reads come straight off
 * the local cache (DAO `Flow`); mutations go to the server first and the
 * local cache mirrors the result on success.
 */
interface CategoryRepository {
    fun getCategories(): Flow<List<Category>>

    suspend fun refresh(): Either<AppError, Unit>

    suspend fun create(
        name: String,
        icon: String,
        color: String,
    ): Either<AppError, Category>

    suspend fun update(
        id: Long,
        name: String,
        icon: String,
        color: String,
    ): Either<AppError, Category>

    suspend fun delete(id: Long): Either<AppError, Unit>
}
