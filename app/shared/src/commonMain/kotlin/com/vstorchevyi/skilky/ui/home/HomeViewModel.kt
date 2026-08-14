package com.vstorchevyi.skilky.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vstorchevyi.skilky.domain.model.Either
import com.vstorchevyi.skilky.domain.model.Expense
import com.vstorchevyi.skilky.domain.usecase.DeletePendingExpenseUseCase
import com.vstorchevyi.skilky.domain.usecase.GetCurrentSessionUseCase
import com.vstorchevyi.skilky.domain.usecase.GetExpensesUseCase
import com.vstorchevyi.skilky.domain.usecase.LogoutUseCase
import com.vstorchevyi.skilky.domain.usecase.RefreshExpensesUseCase
import com.vstorchevyi.skilky.domain.usecase.RetryPendingExpenseUseCase
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class HomeViewModel(
    private val getCurrentSession: GetCurrentSessionUseCase,
    private val logout: LogoutUseCase,
    private val getExpenses: GetExpensesUseCase,
    private val refreshExpenses: RefreshExpensesUseCase,
    private val retryPendingExpense: RetryPendingExpenseUseCase,
    private val deletePendingExpense: DeletePendingExpenseUseCase,
) : ViewModel() {
    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    private val _events =
        Channel<HomeEvent>(
            capacity = Channel.BUFFERED,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )
    val events = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            when (val result = getCurrentSession()) {
                is Either.Left -> {
                    _events.trySend(HomeEvent.ShowError(result.value))
                }

                is Either.Right -> {
                    result.value?.user?.let { user ->
                        _state.update { it.copy(displayName = user.displayName, email = user.email) }
                    }
                }
            }
        }
        getExpenses()
            .onEach { result ->
                when (result) {
                    is Either.Left -> _events.trySend(HomeEvent.ShowError(result.value))
                    is Either.Right -> _state.update { it.copy(groups = result.value.groupByDate()) }
                }
            }
            .launchIn(viewModelScope)
        onRefresh()
    }

    fun onRefresh() {
        if (_state.value.isRefreshing) return
        _state.update { it.copy(isRefreshing = true) }
        viewModelScope.launch {
            val result = refreshExpenses()
            _state.update { it.copy(isRefreshing = false) }
            if (result is Either.Left) {
                _events.trySend(HomeEvent.ShowError(result.value))
            }
        }
    }

    fun onSignOut() {
        viewModelScope.launch {
            when (val result = logout()) {
                is Either.Left -> _events.trySend(HomeEvent.ShowError(result.value))
                is Either.Right -> _events.trySend(HomeEvent.NavigateToLogin)
            }
        }
    }

    fun onRetryPending(id: Long) {
        viewModelScope.launch {
            val result = retryPendingExpense(id)
            if (result is Either.Left) _events.trySend(HomeEvent.ShowError(result.value))
        }
    }

    fun onDeletePending(id: Long) {
        viewModelScope.launch {
            val result = deletePendingExpense(id)
            if (result is Either.Left) _events.trySend(HomeEvent.ShowError(result.value))
        }
    }
}

/**
 * Groups a list of expenses by date and computes a per-group total. The list
 * arrives already sorted (DAO: date desc, createdAt desc), so a grouped
 * `LinkedHashMap` preserves the right order without re-sorting.
 */
private fun List<Expense>.groupByDate(): List<ExpenseGroup> =
    groupByTo(LinkedHashMap()) { it.date }
        .map { (date, items) ->
            ExpenseGroup(
                date = date,
                items = items,
                total = items.sumOf { it.amount },
            )
        }
