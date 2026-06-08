package com.vstorchevyi.skilky.domain.usecase

import com.vstorchevyi.skilky.domain.model.AppError
import com.vstorchevyi.skilky.domain.model.Either
import com.vstorchevyi.skilky.domain.repository.ExpenseRepository

/** Remove an expense by id. */
class DeleteExpenseUseCase(
    private val repository: ExpenseRepository,
) {
    suspend operator fun invoke(id: Long): Either<AppError, Unit> = repository.delete(id)
}
