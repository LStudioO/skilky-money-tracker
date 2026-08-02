package com.vstorchevyi.skilky.domain.repository

import com.vstorchevyi.skilky.api.Currency
import com.vstorchevyi.skilky.api.ParsedExpenseItem
import com.vstorchevyi.skilky.domain.model.AppError
import com.vstorchevyi.skilky.domain.model.Either

class FakeParseRepository : ParseRepository {
    val calls: MutableList<Call> = mutableListOf()
    val receiptCalls: MutableList<ReceiptCall> = mutableListOf()
    var result: Either<AppError, List<ParsedExpenseItem>> = Either.Right(emptyList())
    var receiptResult: Either<AppError, List<ParsedExpenseItem>> = Either.Right(emptyList())

    override suspend fun parseText(
        text: String,
        currency: Currency,
    ): Either<AppError, List<ParsedExpenseItem>> {
        calls += Call(text, currency)
        return result
    }

    override suspend fun parseReceipt(
        bytes: ByteArray,
        currency: Currency,
    ): Either<AppError, List<ParsedExpenseItem>> {
        receiptCalls += ReceiptCall(bytes, currency)
        return receiptResult
    }

    data class Call(
        val text: String,
        val currency: Currency,
    )

    class ReceiptCall(
        val bytes: ByteArray,
        val currency: Currency,
    ) {
        override fun equals(other: Any?): Boolean = other is ReceiptCall && bytes.contentEquals(other.bytes) && currency == other.currency

        override fun hashCode(): Int = 31 * bytes.contentHashCode() + currency.hashCode()
    }
}
