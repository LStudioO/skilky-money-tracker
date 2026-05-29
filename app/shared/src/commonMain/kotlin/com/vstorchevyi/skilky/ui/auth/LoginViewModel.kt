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

    private val _effects =
        Channel<LoginEffect>(
            capacity = Channel.BUFFERED,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )
    val effects = _effects.receiveAsFlow()

    fun onIntent(intent: LoginIntent) {
        when (intent) {
            is LoginIntent.EmailChanged -> {
                _state.update { it.copy(email = intent.value, error = null) }
            }

            is LoginIntent.PasswordChanged -> {
                _state.update { it.copy(password = intent.value, error = null) }
            }

            LoginIntent.Submit -> {
                submit()
            }

            LoginIntent.GoToRegister -> {
                _effects.trySend(LoginEffect.NavigateToRegister)
            }
        }
    }

    private fun submit() {
        val snapshot = _state.value
        if (!snapshot.canSubmit) return
        _state.update { it.copy(isSubmitting = true, error = null) }
        viewModelScope.launch {
            when (val result = login(snapshot.email.trim(), snapshot.password)) {
                is Either.Right -> {
                    _state.update { it.copy(isSubmitting = false) }
                    _effects.trySend(LoginEffect.NavigateToHome)
                }

                is Either.Left -> {
                    _state.update { it.copy(isSubmitting = false, error = result.value) }
                }
            }
        }
    }
}
