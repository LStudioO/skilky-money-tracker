package com.vstorchevyi.skilky.domain.usecase

import com.vstorchevyi.skilky.api.Currency
import com.vstorchevyi.skilky.domain.repository.ParseRepository

/** Parses a JPEG or PNG receipt through the authenticated server endpoint. */
class ParseReceiptUseCase(
    private val repository: ParseRepository,
) {
    suspend operator fun invoke(
        bytes: ByteArray,
        currency: Currency,
    ) = repository.parseReceipt(bytes = bytes, currency = currency)
}
