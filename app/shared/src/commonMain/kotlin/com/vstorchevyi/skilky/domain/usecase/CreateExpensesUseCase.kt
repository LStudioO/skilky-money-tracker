package com.vstorchevyi.skilky.domain.usecase

import com.vstorchevyi.skilky.domain.model.AppError
import com.vstorchevyi.skilky.domain.model.Either
import com.vstorchevyi.skilky.domain.model.Expense
import com.vstorchevyi.skilky.domain.model.ExpenseInput
import com.vstorchevyi.skilky.domain.repository.ExpenseRepository

/** Saves a reviewed parse result in one batch. */
class CreateExpensesUseCase(
    private val repository: ExpenseRepository,
) {
    suspend operator fun invoke(inputs: List<ExpenseInput>): Either<AppError, List<Expense>> {
        val result = repository.createAll(inputs)
        return result
    }
}
