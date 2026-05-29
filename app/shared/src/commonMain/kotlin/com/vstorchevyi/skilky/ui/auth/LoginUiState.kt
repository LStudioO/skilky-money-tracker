package com.vstorchevyi.skilky.ui.auth

import com.vstorchevyi.skilky.domain.model.AppError

/** Form state and submission status for the login screen. */
data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val isSubmitting: Boolean = false,
    val error: AppError? = null,
) {
    val canSubmit: Boolean
        get() = !isSubmitting && email.isNotBlank() && password.isNotBlank()
}

/** What the screen can ask the [LoginViewModel] to do. */
sealed interface LoginIntent {
    data class EmailChanged(
        val value: String,
    ) : LoginIntent

    data class PasswordChanged(
        val value: String,
    ) : LoginIntent

    data object Submit : LoginIntent

    data object GoToRegister : LoginIntent
}

/** One-shot signals the screen consumes once and turns into navigation. */
sealed interface LoginEffect {
    data object NavigateToHome : LoginEffect

    data object NavigateToRegister : LoginEffect
}
