package com.vstorchevyi.skilky.domain.model

/**
 * Why a domain operation failed, independent of the transport. The data layer
 * turns HTTP status codes and connection failures into one of these, so the
 * domain and presentation layers never see Ktor or status codes.
 *
 * Used as the [Either.Left] type of a failed operation.
 */
enum class AppError {
    /** Credentials were rejected, or the session is no longer valid. */
    Unauthorized,

    /** The request was malformed or rejected by server-side validation. */
    Validation,

    /** A conflicting resource already exists, e.g. the email is taken. */
    Conflict,

    /** The server could not be reached, or it returned a server error. */
    Network,

    /** Anything not covered above. */
    Unknown,
}
