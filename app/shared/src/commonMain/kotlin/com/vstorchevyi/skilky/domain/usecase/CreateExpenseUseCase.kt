package com.vstorchevyi.skilky.domain.usecase

import com.vstorchevyi.skilky.domain.model.AppError
import com.vstorchevyi.skilky.domain.model.Either
import com.vstorchevyi.skilky.domain.model.Expense
import com.vstorchevyi.skilky.domain.model.ExpenseInput
import com.vstorchevyi.skilky.domain.repository.ExpenseRepository

/** Add a new expense from manual text entry. */
class CreateExpenseUseCase(
    private val repository: ExpenseRepository,
) {
    suspend operator fun invoke(input: ExpenseInput): Either<AppError, Expense> = repository.create(input)
}
