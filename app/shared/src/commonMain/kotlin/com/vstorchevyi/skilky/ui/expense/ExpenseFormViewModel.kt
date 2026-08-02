package com.vstorchevyi.skilky.ui.expense

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vstorchevyi.skilky.api.Currency
import com.vstorchevyi.skilky.domain.model.Category
import com.vstorchevyi.skilky.domain.model.Either
import com.vstorchevyi.skilky.domain.model.Expense
import com.vstorchevyi.skilky.domain.model.ExpenseInput
import com.vstorchevyi.skilky.domain.usecase.CreateExpenseUseCase
import com.vstorchevyi.skilky.domain.usecase.DeleteExpenseUseCase
import com.vstorchevyi.skilky.domain.usecase.GetCategoriesUseCase
import com.vstorchevyi.skilky.domain.usecase.GetExpenseUseCase
import com.vstorchevyi.skilky.domain.usecase.RefreshCategoriesUseCase
import com.vstorchevyi.skilky.domain.usecase.UpdateExpenseUseCase
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/**
 * Drives the add/edit expense form. The [expenseId] argument is `null` for
 * a fresh create; for an edit it is the row id, and the VM seeds the draft
 * from the local cache (no extra network round-trip). The category list
 * comes from the local cache too, with a single server refresh on init so
 * a fresh-install user is not stuck with an empty picker.
 */
class ExpenseFormViewModel(
    private val expenseId: Long?,
    private val getExpense: GetExpenseUseCase,
    private val getCategories: GetCategoriesUseCase,
    private val refreshCategories: RefreshCategoriesUseCase,
    private val createExpense: CreateExpenseUseCase,
    private val updateExpense: UpdateExpenseUseCase,
    private val deleteExpense: DeleteExpenseUseCase,
    private val clock: Clock = Clock.System,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) : ViewModel() {
    private val _state =
        MutableStateFlow(
            ExpenseFormUiState(
                mode = if (expenseId == null) ExpenseFormUiState.Mode.New else ExpenseFormUiState.Mode.Edit,
                isLoading = expenseId != null,
                draft = ExpenseDraft(date = today()),
            ),
        )
    val state: StateFlow<ExpenseFormUiState> = _state.asStateFlow()

    private val _events =
        Channel<ExpenseFormEvent>(
            capacity = Channel.BUFFERED,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )
    val events = _events.receiveAsFlow()

    init {
        observeCategories()
        viewModelScope.launch { refreshCategories() }
        expenseId?.let { loadExisting(it) }
    }

    private fun observeCategories() {
        getCategories()
            .onEach { categories ->
                _state.update { current ->
                    current.copy(
                        categories = categories,
                        draft = current.draft.maybeSelectDefaultCategory(categories),
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    private fun loadExisting(id: Long) {
        viewModelScope.launch {
            val existing = getExpense(id).first()
            if (existing == null) {
                _state.update { it.copy(isLoading = false, notFound = true) }
                return@launch
            }
            _state.update { current ->
                current.copy(
                    isLoading = false,
                    draft = existing.toDraft(),
                )
            }
        }
    }

    fun onNameChange(value: String) {
        _state.update { it.copy(draft = it.draft.copy(name = value)) }
    }

    fun onAmountChange(value: String) {
        _state.update { it.copy(draft = it.draft.copy(amountText = value)) }
    }

    fun onCurrencyChange(value: Currency) {
        _state.update { it.copy(draft = it.draft.copy(currency = value)) }
    }

    fun onCategoryChange(id: Long) {
        _state.update { it.copy(draft = it.draft.copy(categoryId = id)) }
    }

    fun onNoteChange(value: String) {
        _state.update { it.copy(draft = it.draft.copy(note = value)) }
    }

    fun onDateChange(value: LocalDate) {
        _state.update { it.copy(draft = it.draft.copy(date = value)) }
    }

    fun onSave() {
        val snapshot = _state.value
        val draft = snapshot.draft
        if (!draft.canSave || snapshot.isSaving || snapshot.isDeleting) return
        val input = draft.toInput() ?: return
        _state.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val result =
                if (expenseId == null) {
                    createExpense(input)
                } else {
                    updateExpense(expenseId, input)
                }
            _state.update { it.copy(isSaving = false) }
            when (result) {
                is Either.Right -> _events.trySend(ExpenseFormEvent.Saved)
                is Either.Left -> _events.trySend(ExpenseFormEvent.ShowError(result.value))
            }
        }
    }

    fun onDelete() {
        val id = expenseId ?: return
        val snapshot = _state.value
        if (snapshot.isSaving || snapshot.isDeleting) return
        _state.update { it.copy(isDeleting = true) }
        viewModelScope.launch {
            val result = deleteExpense(id)
            _state.update { it.copy(isDeleting = false) }
            when (result) {
                is Either.Right -> _events.trySend(ExpenseFormEvent.Deleted)
                is Either.Left -> _events.trySend(ExpenseFormEvent.ShowError(result.value))
            }
        }
    }

    private fun today(): LocalDate = clock.now().toLocalDateTime(timeZone).date

    private fun ExpenseDraft.toInput(): ExpenseInput? {
        val amount = parsedAmount ?: return null
        val category = categoryId ?: return null
        return ExpenseInput(
            name = name.trim(),
            amount = amount,
            currency = currency,
            categoryId = category,
            note = note.trim().takeIf { it.isNotEmpty() },
            date = date,
        )
    }
}

private fun Expense.toDraft(): ExpenseDraft =
    ExpenseDraft(
        name = name,
        amountText = formatAmountForEditor(amount),
        currency = currency,
        categoryId = category.id,
        note = note.orEmpty(),
        date = date,
    )

/**
 * Trims a trailing `.0` from whole-number amounts so the edit field reads
 * "45" instead of "45.0". Anything with a real fractional part keeps the
 * `Double.toString()` rendering.
 */
private fun formatAmountForEditor(amount: Double): String {
    val raw = amount.toString()
    return if (raw.endsWith(".0")) raw.removeSuffix(".0") else raw
}

private fun ExpenseDraft.maybeSelectDefaultCategory(categories: List<Category>): ExpenseDraft {
    if (categoryId != null) return this
    val first = categories.firstOrNull() ?: return this
    return copy(categoryId = first.id)
}
