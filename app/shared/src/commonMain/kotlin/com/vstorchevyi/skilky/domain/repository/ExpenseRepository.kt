package com.vstorchevyi.skilky.domain.repository

import com.vstorchevyi.skilky.domain.model.AppError
import com.vstorchevyi.skilky.domain.model.Either
import com.vstorchevyi.skilky.domain.model.Expense
import kotlinx.coroutines.flow.Flow

/**
 * Read access to the user's expenses. Reads come straight off the local
 * cache (DAO `Flow`); [refresh] pulls the latest server page into the cache.
 * Create / update / delete arrive in a follow-up slice.
 */
interface ExpenseRepository {
    fun getExpenses(): Flow<List<Expense>>

    suspend fun refresh(): Either<AppError, Unit>
}
