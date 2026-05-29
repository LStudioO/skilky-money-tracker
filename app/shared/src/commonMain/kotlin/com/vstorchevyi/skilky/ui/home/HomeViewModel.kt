package com.vstorchevyi.skilky.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vstorchevyi.skilky.domain.usecase.GetCurrentSessionUseCase
import com.vstorchevyi.skilky.domain.usecase.LogoutUseCase
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class HomeViewModel(
    private val getCurrentSession: GetCurrentSessionUseCase,
    private val logout: LogoutUseCase,
) : ViewModel() {
    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    private val _effects =
        Channel<HomeEffect>(
            capacity = Channel.BUFFERED,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )
    val effects = _effects.receiveAsFlow()

    init {
        viewModelScope.launch {
            getCurrentSession()?.user?.let { user ->
                _state.value = HomeUiState(displayName = user.displayName, email = user.email)
            }
        }
    }

    fun onSignOut() {
        viewModelScope.launch {
            logout()
            _effects.trySend(HomeEffect.NavigateToLogin)
        }
    }
}
