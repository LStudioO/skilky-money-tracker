package com.vstorchevyi.skilky.domain.model

import kotlin.coroutines.cancellation.CancellationException

/**
 * A minimal Either: a value in one of two cases. By convention [Left] carries a
 * failure and [Right] carries a success.
 *
 * Hand-rolled on purpose. It gives the codebase Either-style results without
 * pulling in Arrow, which is far larger than the handful of operators below.
 */
sealed interface Either<out L, out R> {
    data class Left<out L>(
        val value: L,
    ) : Either<L, Nothing>

    data class Right<out R>(
        val value: R,
    ) : Either<Nothing, R>

    companion object {
        /**
         * Runs [block] and captures its outcome. A returned value becomes a
         * [Right]; a thrown exception becomes a [Left].
         *
         * Two kinds of throwables are deliberately not caught and propagate to
         * the caller:
         * - [CancellationException], because swallowing it would break
         *   structured concurrency cancellation in coroutines.
         * - Any [Error] subclass (OutOfMemoryError, StackOverflowError, ...),
         *   because these are terminal and trying to recover is worse than
         *   crashing. They are not subtypes of [Exception], so a `catch (Exception)`
         *   already lets them pass through.
         *
         * `block` is non-suspend at the type level, but `inline` means the call
         * site inherits the caller's suspend context, so suspend code can call
         * `Either.catch { ... }` without extra ceremony.
         */
        @Suppress("TooGenericExceptionCaught")
        inline fun <R> catch(block: () -> R): Either<Throwable, R> =
            try {
                Right(block())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Left(e)
            }
    }
}

/** Collapses both cases into a single value. */
inline fun <L, R, T> Either<L, R>.fold(
    onLeft: (L) -> T,
    onRight: (R) -> T,
): T =
    when (this) {
        is Either.Left -> onLeft(value)
        is Either.Right -> onRight(value)
    }

/** Transforms the [Either.Right] value; a [Either.Left] passes through unchanged. */
inline fun <L, R, T> Either<L, R>.map(transform: (R) -> T): Either<L, T> =
    when (this) {
        is Either.Left -> this
        is Either.Right -> Either.Right(transform(value))
    }

/** Chains another Either-returning step onto a [Either.Right]. */
inline fun <L, R, T> Either<L, R>.flatMap(transform: (R) -> Either<L, T>): Either<L, T> =
    when (this) {
        is Either.Left -> this
        is Either.Right -> transform(value)
    }

/** The [Either.Right] value, or null when this is a [Either.Left]. */
fun <R> Either<*, R>.getOrNull(): R? =
    when (this) {
        is Either.Left -> null
        is Either.Right -> value
    }

/** The [Either.Left] value, or null when this is a [Either.Right]. */
fun <L> Either<L, *>.leftOrNull(): L? =
    when (this) {
        is Either.Left -> value
        is Either.Right -> null
    }

/** Runs [block] when this is a [Either.Right]; returns the original Either unchanged. */
inline fun <L, R> Either<L, R>.ifRight(block: (R) -> Unit): Either<L, R> {
    if (this is Either.Right) block(value)
    return this
}

/** Runs [block] when this is a [Either.Left]; returns the original Either unchanged. */
inline fun <L, R> Either<L, R>.ifLeft(block: (L) -> Unit): Either<L, R> {
    if (this is Either.Left) block(value)
    return this
}
