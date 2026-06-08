package com.vstorchevyi.skilky.data.repository

import com.vstorchevyi.skilky.domain.model.AppError
import com.vstorchevyi.skilky.domain.model.Either
import io.ktor.client.plugins.ResponseException
import io.ktor.http.HttpStatusCode
import kotlin.coroutines.cancellation.CancellationException

/**
 * Runs an API-touching [block] and folds the outcome into an [Either]:
 * - Success → [Either.Right] with the value.
 * - HTTP non-2xx → [Either.Left] with the mapped [AppError].
 * - Anything else (no connection, DNS, timeout, JSON parse failure, ...) →
 *   [Either.Left] with [AppError.Network]. These transport failures have no
 *   shared supertype across platforms, so the catch-all is intentional.
 * - [CancellationException] is rethrown so structured concurrency works.
 *
 * `inline` so suspend bodies at the call site keep their suspend context;
 * the helper itself stays non-suspend.
 */
internal inline fun <T> runCatchingApi(block: () -> T): Either<AppError, T> =
    try {
        Either.Right(block())
    } catch (e: ResponseException) {
        Either.Left(e.response.status.toAppError())
    } catch (e: CancellationException) {
        throw e
    } catch (
        @Suppress("TooGenericExceptionCaught", "SwallowedException") e: Exception,
    ) {
        Either.Left(AppError.Network)
    }

/**
 * Maps an HTTP status into the domain's [AppError]. The handful of explicit
 * codes match how the server signals validation, conflict, and auth
 * failures; 5xx falls into [AppError.Network] (the user's recourse is the
 * same — retry later); anything else becomes [AppError.Unknown].
 */
internal fun HttpStatusCode.toAppError(): AppError =
    when {
        this == HttpStatusCode.Unauthorized -> AppError.Unauthorized
        this == HttpStatusCode.Conflict -> AppError.Conflict
        this == HttpStatusCode.UnprocessableEntity -> AppError.Validation
        value >= HTTP_SERVER_ERROR -> AppError.Network
        else -> AppError.Unknown
    }

private const val HTTP_SERVER_ERROR = 500
