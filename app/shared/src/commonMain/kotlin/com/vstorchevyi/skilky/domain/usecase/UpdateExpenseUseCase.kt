package com.vstorchevyi.skilky.domain.usecase

import com.vstorchevyi.skilky.domain.model.AppError
import com.vstorchevyi.skilky.domain.model.Either
import com.vstorchevyi.skilky.domain.model.Expense
import com.vstorchevyi.skilky.domain.model.ExpenseInput
import com.vstorchevyi.skilky.domain.repository.ExpenseRepository

/** Replace an existing expense with the supplied input. */
class UpdateExpenseUseCase(
    private val repository: ExpenseRepository,
) {
    suspend operator fun invoke(
        id: Long,
        input: ExpenseInput,
    ): Either<AppError, Expense> = repository.update(id, input)
}
