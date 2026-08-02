package com.vstorchevyi.skilky.domain.usecase

import com.vstorchevyi.skilky.domain.model.AppError
import com.vstorchevyi.skilky.domain.model.Either
import com.vstorchevyi.skilky.domain.model.Expense
import com.vstorchevyi.skilky.domain.repository.ExpenseRepository
import kotlinx.coroutines.flow.Flow

/** Stream a single expense by id from the local cache. */
class GetExpenseUseCase(
    private val repository: ExpenseRepository,
) {
    operator fun invoke(id: Long): Flow<Either<AppError, Expense?>> = repository.getExpense(id)
}
