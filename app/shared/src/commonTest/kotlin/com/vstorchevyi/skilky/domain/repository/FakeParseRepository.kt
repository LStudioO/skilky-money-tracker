package com.vstorchevyi.skilky.domain.repository

import com.vstorchevyi.skilky.api.Currency
import com.vstorchevyi.skilky.api.ParsedExpenseItem
import com.vstorchevyi.skilky.domain.model.AppError
import com.vstorchevyi.skilky.domain.model.Either

class FakeParseRepository : ParseRepository {
    val calls: MutableList<Call> = mutableListOf()
    var result: Either<AppError, List<ParsedExpenseItem>> = Either.Right(emptyList())

    override suspend fun parseText(
        text: String,
        currency: Currency,
    ): Either<AppError, List<ParsedExpenseItem>> {
        calls += Call(text, currency)
        return result
    }

    data class Call(
        val text: String,
        val currency: Currency,
    )
}
