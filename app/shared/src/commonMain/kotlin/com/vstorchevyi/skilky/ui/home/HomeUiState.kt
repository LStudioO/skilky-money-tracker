package com.vstorchevyi.skilky.ui.home

/** Read-only summary of the signed-in user, shown on the home placeholder. */
data class HomeUiState(
    val displayName: String = "",
    val email: String = "",
)

sealed interface HomeEffect {
    data object NavigateToLogin : HomeEffect
}
