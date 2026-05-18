package com.vstorchevyi.skilky.security

import com.vstorchevyi.skilky.api.RegisterRequest
import com.vstorchevyi.skilky.errors.ValidationException

private val EMAIL_REGEX = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\$")
private val PASSWORD_HAS_LETTER = Regex("[A-Za-z]")
private val PASSWORD_HAS_DIGIT = Regex("[0-9]")

private const val EMAIL_MAX_LENGTH = 254
private const val PASSWORD_MIN_LENGTH = 8
private const val DISPLAY_NAME_MAX_LENGTH = 100

fun validateRegisterRequest(req: RegisterRequest) {
    if (!EMAIL_REGEX.matches(req.email) || req.email.length > EMAIL_MAX_LENGTH) {
        throw ValidationException("Invalid email format")
    }
    if (req.password.length < PASSWORD_MIN_LENGTH ||
        !PASSWORD_HAS_LETTER.containsMatchIn(req.password) ||
        !PASSWORD_HAS_DIGIT.containsMatchIn(req.password)
    ) {
        throw ValidationException(
            "Password must be at least $PASSWORD_MIN_LENGTH characters and contain a letter and a digit",
        )
    }
    val name = req.displayName.trim()
    if (name.isEmpty() || name.length > DISPLAY_NAME_MAX_LENGTH) {
        throw ValidationException("Display name must be 1-$DISPLAY_NAME_MAX_LENGTH characters")
    }
}
