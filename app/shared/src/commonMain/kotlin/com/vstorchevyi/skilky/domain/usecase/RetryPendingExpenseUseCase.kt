package com.vstorchevyi.skilky.domain.usecase

import com.vstorchevyi.skilky.domain.repository.ExpenseRepository

class RetryPendingExpenseUseCase(
    private val repository: ExpenseRepository,
) {
    suspend operator fun invoke(id: Long) = repository.retryPending(id)
}
