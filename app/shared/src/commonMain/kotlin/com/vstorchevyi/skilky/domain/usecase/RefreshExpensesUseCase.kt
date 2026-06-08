package com.vstorchevyi.skilky.domain.usecase

import com.vstorchevyi.skilky.domain.model.AppError
import com.vstorchevyi.skilky.domain.model.Either
import com.vstorchevyi.skilky.domain.repository.ExpenseRepository

/** Pull the latest server page into the local cache. */
class RefreshExpensesUseCase(
    private val repository: ExpenseRepository,
) {
    suspend operator fun invoke(): Either<AppError, Unit> = repository.refresh()
}
