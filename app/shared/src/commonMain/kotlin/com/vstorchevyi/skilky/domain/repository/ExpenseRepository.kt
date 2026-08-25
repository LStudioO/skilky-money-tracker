package com.vstorchevyi.skilky.domain.repository

import com.vstorchevyi.skilky.domain.model.AppError
import com.vstorchevyi.skilky.domain.model.Either
import com.vstorchevyi.skilky.domain.model.Expense
import com.vstorchevyi.skilky.domain.model.ExpenseInput
import kotlinx.coroutines.flow.Flow

/**
 * Read-and-mutate access to the user's expenses. Reads come straight off the
 * local cache and preserve storage failures in the stream. [refresh] uploads
 * queued creates before pulling the latest server page into the cache.
 */
interface ExpenseRepository {
    fun getExpenses(): Flow<Either<AppError, List<Expense>>>

    /**
     * Stream a single expense by id from the local cache. Emits `null` when
     * no row matches — for instance, after a delete or before the first
     * refresh has populated the cache.
     */
    fun getExpense(id: Long): Flow<Either<AppError, Expense?>>

    suspend fun refresh(): Either<AppError, Unit>

    suspend fun syncPending(): Either<AppError, Unit>

    suspend fun retryPending(id: Long): Either<AppError, Unit>

    suspend fun deletePending(id: Long): Either<AppError, Unit>

    suspend fun create(input: ExpenseInput): Either<AppError, Expense>

    suspend fun createAll(inputs: List<ExpenseInput>): Either<AppError, List<Expense>>

    suspend fun update(
        id: Long,
        input: ExpenseInput,
    ): Either<AppError, Expense>

    suspend fun delete(id: Long): Either<AppError, Unit>
}
