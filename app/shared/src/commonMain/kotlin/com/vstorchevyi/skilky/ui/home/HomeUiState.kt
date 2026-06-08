package com.vstorchevyi.skilky.ui.home

import com.vstorchevyi.skilky.domain.model.AppError
import com.vstorchevyi.skilky.domain.model.Expense
import kotlinx.datetime.LocalDate

/**
 * Snapshot of the home screen: the user header plus the latest expenses,
 * grouped by date. `groups` is what the screen renders; the per-group total
 * is computed in the ViewModel each emission to keep the screen pure.
 */
data class HomeUiState(
    val displayName: String = "",
    val email: String = "",
    val groups: List<ExpenseGroup> = emptyList(),
    val isRefreshing: Boolean = false,
)

/** A run of expenses on the same date, with the sum of their amounts. */
data class ExpenseGroup(
    val date: LocalDate,
    val items: List<Expense>,
    val total: Double,
)

sealed interface HomeEvent {
    data object NavigateToLogin : HomeEvent

    data class ShowError(
        val error: AppError,
    ) : HomeEvent
}
