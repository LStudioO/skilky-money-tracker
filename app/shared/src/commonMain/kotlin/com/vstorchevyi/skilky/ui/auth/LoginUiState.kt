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

/** One-shot events the screen consumes once and turns into navigation. */
sealed interface LoginEvent {
    data object NavigateToHome : LoginEvent

    data object NavigateToRegister : LoginEvent
}
