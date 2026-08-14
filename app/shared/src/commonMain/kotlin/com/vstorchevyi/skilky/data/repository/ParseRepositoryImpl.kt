package com.vstorchevyi.skilky.data.repository

import com.vstorchevyi.skilky.api.Currency
import com.vstorchevyi.skilky.api.ParseTextRequest
import com.vstorchevyi.skilky.api.ParsedExpenseItem
import com.vstorchevyi.skilky.data.remote.ParseApi
import com.vstorchevyi.skilky.domain.model.AppError
import com.vstorchevyi.skilky.domain.model.Either
import com.vstorchevyi.skilky.domain.repository.ParseRepository

internal class ParseRepositoryImpl(
    private val api: ParseApi,
) : ParseRepository {
    override suspend fun parseText(
        text: String,
        currency: Currency,
    ): Either<AppError, List<ParsedExpenseItem>> =
        runCatchingApi {
            api.parseText(ParseTextRequest(text = text, currency = currency)).items
        }

    override suspend fun parseAudio(
        bytes: ByteArray,
        currency: Currency,
    ) = runCatchingApi {
        api.parseAudio(bytes = bytes, currency = currency)
    }

    override suspend fun parseReceipt(
        bytes: ByteArray,
        currency: Currency,
    ): Either<AppError, List<ParsedExpenseItem>> =
        runCatchingApi {
            api.parseReceipt(bytes = bytes, currency = currency).items
        }
}
