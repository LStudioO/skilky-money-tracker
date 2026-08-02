package com.vstorchevyi.skilky.data.local

import com.vstorchevyi.skilky.domain.model.AppError
import com.vstorchevyi.skilky.domain.model.Either
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
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

/** Converts a local-data stream into values that preserve read failures. */
internal fun <T> Flow<T>.asStorageResult(): Flow<Either<AppError, T>> =
    map<T, Either<AppError, T>> { Either.Right(it) }
        .catch { error ->
            if (error is CancellationException) throw error
            emit(Either.Left(AppError.Storage))
        }
