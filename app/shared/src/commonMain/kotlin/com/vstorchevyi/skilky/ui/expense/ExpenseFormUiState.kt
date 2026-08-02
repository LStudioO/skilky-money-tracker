package com.vstorchevyi.skilky.ui.expense

import com.vstorchevyi.skilky.api.Currency
import com.vstorchevyi.skilky.domain.model.AppError
import com.vstorchevyi.skilky.domain.model.Category
import kotlinx.datetime.LocalDate

/**
 * Snapshot of the expense form. [mode] decides whether the screen reads as
 * "Add expense" or "Edit expense" and whether the delete button is shown.
 * The form is hidden behind a spinner while [isLoading] is true (edit mode
 * waiting on the cached row), and again behind disabled controls while a
 * save or delete is in flight.
 */
data class ExpenseFormUiState(
    val mode: Mode = Mode.New,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isDeleting: Boolean = false,
    val notFound: Boolean = false,
    val categories: List<Category> = emptyList(),
    val draft: ExpenseDraft = ExpenseDraft(),
) {
    enum class Mode { New, Edit }
}

/**
 * The user-editable bits of the form. `amountText` is kept as a string so
 * partial input ("12.", "0.0") doesn't bounce through `Double.NaN` while
 * the user is typing; the ViewModel parses it on save.
 */
data class ExpenseDraft(
    val name: String = "",
    val amountText: String = "",
    val currency: Currency = Currency.UAH,
    val categoryId: Long? = null,
    val note: String = "",
    val date: LocalDate = LocalDate(EPOCH_YEAR, 1, 1),
) {
    val parsedAmount: Double?
        get() = amountText.trim().replace(',', '.').toDoubleOrNull()

    val canSave: Boolean
        get() {
            val amount = parsedAmount ?: return false
            return name.isNotBlank() && amount > 0.0 && categoryId != null
        }

    private companion object {
        const val EPOCH_YEAR = 1970
    }
}

sealed interface ExpenseFormEvent {
    /** Save succeeded; the screen should close. */
    data object Saved : ExpenseFormEvent

    /** Delete succeeded; the screen should close. */
    data object Deleted : ExpenseFormEvent

    /** Server / network failure; surface as a snackbar and stay on screen. */
    data class ShowError(
        val error: AppError,
    ) : ExpenseFormEvent
}
