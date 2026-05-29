package com.vstorchevyi.skilky.ui.auth

import com.vstorchevyi.skilky.domain.model.AppError

/** Form state and submission status for the registration screen. */
data class RegisterUiState(
    val email: String = "",
    val password: String = "",
    val displayName: String = "",
    val isSubmitting: Boolean = false,
    val error: AppError? = null,
) {
    val canSubmit: Boolean
        get() =
            !isSubmitting &&
                email.isNotBlank() &&
                password.isNotBlank() &&
                displayName.isNotBlank()
}

sealed interface RegisterIntent {
    data class EmailChanged(
        val value: String,
    ) : RegisterIntent

    data class PasswordChanged(
        val value: String,
    ) : RegisterIntent

    data class DisplayNameChanged(
        val value: String,
    ) : RegisterIntent

    data object Submit : RegisterIntent

    data object GoToLogin : RegisterIntent
}

sealed interface RegisterEffect {
    data object NavigateToHome : RegisterEffect

    data object NavigateToLogin : RegisterEffect
}
