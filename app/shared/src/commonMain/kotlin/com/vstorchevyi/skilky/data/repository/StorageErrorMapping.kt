package com.vstorchevyi.skilky.data.repository

import com.vstorchevyi.skilky.domain.model.AppError
import com.vstorchevyi.skilky.domain.model.Either
import kotlin.coroutines.cancellation.CancellationException

/** Maps failures from Room and DataStore without misreporting them as network errors. */
internal inline fun <T> runCatchingStorage(block: () -> T): Either<AppError, T> =
    try {
        Either.Right(block())
    } catch (e: CancellationException) {
        throw e
    } catch (
        @Suppress("TooGenericExceptionCaught", "SwallowedException") e: Exception,
    ) {
        Either.Left(AppError.Storage)
    }
