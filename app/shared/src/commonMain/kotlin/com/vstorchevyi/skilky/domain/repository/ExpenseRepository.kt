package com.vstorchevyi.skilky.domain.repository

import com.vstorchevyi.skilky.domain.model.AppError
import com.vstorchevyi.skilky.domain.model.Either
import com.vstorchevyi.skilky.domain.model.Expense
import com.vstorchevyi.skilky.domain.model.ExpenseInput
import kotlinx.coroutines.flow.Flow

/**
 * Read-and-mutate access to the user's expenses. Reads come straight off
 * the local cache (DAO `Flow`); [refresh] pulls the latest server page into
 * the cache. Mutations go to the server first and the local cache mirrors
 * the result on success.
 */
interface ExpenseRepository {
    fun getExpenses(): Flow<List<Expense>>

    /**
     * Stream a single expense by id from the local cache. Emits `null` when
     * no row matches — for instance, after a delete or before the first
     * refresh has populated the cache.
     */
    fun getExpense(id: Long): Flow<Expense?>

    suspend fun refresh(): Either<AppError, Unit>

    suspend fun create(input: ExpenseInput): Either<AppError, Expense>

    suspend fun update(
        id: Long,
        input: ExpenseInput,
    ): Either<AppError, Expense>

    suspend fun delete(id: Long): Either<AppError, Unit>
}
