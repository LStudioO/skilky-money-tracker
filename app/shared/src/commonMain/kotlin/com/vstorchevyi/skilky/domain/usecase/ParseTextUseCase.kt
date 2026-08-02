package com.vstorchevyi.skilky.domain.usecase

import com.vstorchevyi.skilky.api.Currency
import com.vstorchevyi.skilky.api.ParsedExpenseItem
import com.vstorchevyi.skilky.domain.model.AppError
import com.vstorchevyi.skilky.domain.model.Either
import com.vstorchevyi.skilky.domain.repository.ParseRepository

class ParseTextUseCase(
    private val repository: ParseRepository,
) {
    suspend operator fun invoke(
        text: String,
        currency: Currency,
    ): Either<AppError, List<ParsedExpenseItem>> = repository.parseText(text, currency)
}
