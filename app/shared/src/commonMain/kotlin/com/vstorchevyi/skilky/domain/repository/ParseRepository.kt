package com.vstorchevyi.skilky.domain.repository

import com.vstorchevyi.skilky.api.Currency
import com.vstorchevyi.skilky.api.ParsedExpenseItem
import com.vstorchevyi.skilky.domain.model.AppError
import com.vstorchevyi.skilky.domain.model.Either

/** Parses free-form expense input through the server. */
interface ParseRepository {
    suspend fun parseText(
        text: String,
        currency: Currency,
    ): Either<AppError, List<ParsedExpenseItem>>

    suspend fun parseAudio(
        bytes: ByteArray,
        currency: Currency,
    ): Either<AppError, List<ParsedExpenseItem>>

    suspend fun parseReceipt(
        bytes: ByteArray,
        currency: Currency,
    ): Either<AppError, List<ParsedExpenseItem>>
}
