package com.vstorchevyi.skilky.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vstorchevyi.skilky.domain.model.Either
import com.vstorchevyi.skilky.domain.usecase.RegisterUseCase
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class RegisterViewModel(
    private val register: RegisterUseCase,
) : ViewModel() {
    private val _state = MutableStateFlow(RegisterUiState())
    val state: StateFlow<RegisterUiState> = _state.asStateFlow()

    private val _effects =
        Channel<RegisterEffect>(
            capacity = Channel.BUFFERED,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )
    val effects = _effects.receiveAsFlow()

    fun onEmailChange(value: String) {
        _state.update { it.copy(email = value, error = null) }
    }

    fun onPasswordChange(value: String) {
        _state.update { it.copy(password = value, error = null) }
    }

    fun onDisplayNameChange(value: String) {
        _state.update { it.copy(displayName = value, error = null) }
    }

    fun onGoToLogin() {
        _effects.trySend(RegisterEffect.NavigateToLogin)
    }

    fun onSubmit() {
        val snapshot = _state.value
        if (!snapshot.canSubmit) return
        _state.update { it.copy(isSubmitting = true, error = null) }
        viewModelScope.launch {
            val result =
                register(
                    email = snapshot.email.trim(),
                    password = snapshot.password,
                    displayName = snapshot.displayName.trim(),
                )
            when (result) {
                is Either.Right -> {
                    _state.update { it.copy(isSubmitting = false) }
                    _effects.trySend(RegisterEffect.NavigateToHome)
                }

                is Either.Left -> {
                    _state.update { it.copy(isSubmitting = false, error = result.value) }
                }
            }
        }
    }
}
