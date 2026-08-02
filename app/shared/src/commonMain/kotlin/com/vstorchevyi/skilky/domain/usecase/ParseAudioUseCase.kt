package com.vstorchevyi.skilky.domain.usecase

import com.vstorchevyi.skilky.api.Currency
import com.vstorchevyi.skilky.api.ParseTextResponse
import com.vstorchevyi.skilky.domain.model.AppError
import com.vstorchevyi.skilky.domain.model.Either
import com.vstorchevyi.skilky.domain.repository.ParseRepository

/** Parses a 16 kHz mono WAV voice note through the authenticated server endpoint. */
class ParseAudioUseCase(
    private val repository: ParseRepository,
) {
    suspend operator fun invoke(
        bytes: ByteArray,
        currency: Currency,
    ): Either<AppError, ParseTextResponse> = repository.parseAudio(bytes, currency)
}
