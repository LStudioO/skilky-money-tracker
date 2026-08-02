package com.vstorchevyi.skilky.domain.usecase

import com.vstorchevyi.skilky.domain.model.AppError
import com.vstorchevyi.skilky.domain.model.Either
import com.vstorchevyi.skilky.domain.model.Expense
import com.vstorchevyi.skilky.domain.repository.ExpenseRepository
import kotlinx.coroutines.flow.Flow

/** Stream the user's expenses from the local cache. */
class GetExpensesUseCase(
    private val repository: ExpenseRepository,
) {
    operator fun invoke(): Flow<Either<AppError, List<Expense>>> = repository.getExpenses()
}
