package com.vstorchevyi.skilky.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vstorchevyi.skilky.domain.model.Either
import com.vstorchevyi.skilky.domain.usecase.LoginUseCase
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class LoginViewModel(
    private val login: LoginUseCase,
) : ViewModel() {
    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    private val _events =
        Channel<LoginEvent>(
            capacity = Channel.BUFFERED,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )
    val events = _events.receiveAsFlow()

    fun onEmailChange(value: String) {
        _state.update { it.copy(email = value, error = null) }
    }

    fun onPasswordChange(value: String) {
        _state.update { it.copy(password = value, error = null) }
    }

    fun onGoToRegister() {
        _events.trySend(LoginEvent.NavigateToRegister)
    }

    fun onSubmit() {
        val snapshot = _state.value
        if (!snapshot.canSubmit) return
        _state.update { it.copy(isSubmitting = true, error = null) }
        viewModelScope.launch {
            when (val result = login(snapshot.email.trim(), snapshot.password)) {
                is Either.Right -> {
                    _state.update { it.copy(isSubmitting = false) }
                    _events.trySend(LoginEvent.NavigateToHome)
                }

                is Either.Left -> {
                    _state.update { it.copy(isSubmitting = false, error = result.value) }
                }
            }
        }
    }
}
