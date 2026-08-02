package com.vstorchevyi.skilky.ui.input

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vstorchevyi.skilky.api.Currency
import com.vstorchevyi.skilky.api.ParsedExpenseItem
import com.vstorchevyi.skilky.domain.model.Category
import com.vstorchevyi.skilky.domain.model.Either
import com.vstorchevyi.skilky.domain.model.ExpenseInput
import com.vstorchevyi.skilky.domain.usecase.CreateExpensesUseCase
import com.vstorchevyi.skilky.domain.usecase.GetCategoriesUseCase
import com.vstorchevyi.skilky.domain.usecase.ParseTextUseCase
import com.vstorchevyi.skilky.domain.usecase.RefreshCategoriesUseCase
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
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

class InputViewModel(
    private val parseText: ParseTextUseCase,
    private val getCategories: GetCategoriesUseCase,
    private val refreshCategories: RefreshCategoriesUseCase,
    private val createExpenses: CreateExpensesUseCase,
    private val clock: Clock = Clock.System,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) : ViewModel() {
    private val _state = MutableStateFlow(InputUiState())
    val state: StateFlow<InputUiState> = _state.asStateFlow()

    private val _events =
        Channel<InputEvent>(
            capacity = Channel.BUFFERED,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )
    val events = _events.receiveAsFlow()

    private var nextItemId = 1L

    init {
        getCategories()
            .onEach { result ->
                when (result) {
                    is Either.Left -> {
                        _events.trySend(InputEvent.ShowError(result.value))
                    }

                    is Either.Right -> {
                        val categories = result.value
                        _state.update { state ->
                            state.copy(
                                categories = categories,
                                previewItems = state.previewItems?.map { it.resolveCategory(categories) },
                            )
                        }
                    }
                }
            }
            .launchIn(viewModelScope)
        viewModelScope.launch {
            val result = refreshCategories()
            if (result is Either.Left) {
                _events.trySend(InputEvent.ShowError(result.value))
            }
        }
    }

    fun onQueryChange(value: String) {
        _state.update { it.copy(query = value, parseError = null) }
    }

    fun onSubmit() {
        val snapshot = _state.value
        val query = snapshot.query.trim()
        if (query.isEmpty() || snapshot.isParsing) return
        _state.update { it.copy(isParsing = true, parseError = null) }
        viewModelScope.launch {
            when (val result = parseText(query, Currency.UAH)) {
                is Either.Left -> {
                    _state.update {
                        it.copy(isParsing = false, parseError = InputError.Request(result.value))
                    }
                }

                is Either.Right -> {
                    showParsedItems(result.value)
                }
            }
        }
    }

    private fun showParsedItems(items: List<ParsedExpenseItem>) {
        if (items.isEmpty()) {
            _state.update { it.copy(isParsing = false, parseError = InputError.NoItems) }
            return
        }
        val categories = _state.value.categories
        val date = today()
        _state.update {
            it.copy(
                isParsing = false,
                parseError = null,
                previewItems = items.map { item -> item.toDraft(categories, date) },
                saveError = null,
            )
        }
    }

    fun onDismissPreview() {
        if (_state.value.isSaving) return
        _state.update { it.copy(previewItems = null, saveError = null) }
    }

    fun onAddItem() {
        val item = ParseItemDraft(id = nextItemId++, date = today(), isEditing = true)
        _state.update { state ->
            state.copy(
                previewItems = state.previewItems.orEmpty().map { it.copy(isEditing = false) } + item,
                saveError = null,
            )
        }
    }

    fun onEditItem(id: Long) {
        updateItems { items -> items.map { it.copy(isEditing = it.id == id) } }
    }

    fun onDoneEditing(id: Long) {
        updateItem(id) { it.copy(isEditing = false) }
    }

    fun onDeleteItem(id: Long) {
        updateItems { items -> items.filterNot { it.id == id } }
    }

    fun onNameChange(
        id: Long,
        value: String,
    ) {
        updateItem(id) { it.copy(name = value) }
    }

    fun onAmountChange(
        id: Long,
        value: String,
    ) {
        updateItem(id) { it.copy(amountText = value) }
    }

    fun onCurrencyChange(
        id: Long,
        value: Currency,
    ) {
        updateItem(id) { it.copy(currency = value) }
    }

    fun onCategoryChange(
        id: Long,
        value: Long,
    ) {
        updateItem(id) { it.copy(categoryId = value) }
    }

    fun onDateChange(
        id: Long,
        value: LocalDate,
    ) {
        updateItem(id) { it.copy(date = value) }
    }

    fun onSaveAll() {
        val snapshot = _state.value
        val items = snapshot.previewItems ?: return
        if (!snapshot.canSave || snapshot.isSaving) return
        val inputs = items.map { it.toInput() }
        _state.update { it.copy(isSaving = true, saveError = null) }
        viewModelScope.launch {
            when (val result = createExpenses(inputs)) {
                is Either.Left -> {
                    _state.update { it.copy(isSaving = false, saveError = result.value) }
                }

                is Either.Right -> {
                    _state.update {
                        it.copy(
                            query = "",
                            previewItems = null,
                            isSaving = false,
                            saveError = null,
                        )
                    }
                    _events.trySend(InputEvent.Saved(result.value.size))
                }
            }
        }
    }

    private fun updateItem(
        id: Long,
        transform: (ParseItemDraft) -> ParseItemDraft,
    ) {
        updateItems { items -> items.map { if (it.id == id) transform(it) else it } }
    }

    private fun updateItems(transform: (List<ParseItemDraft>) -> List<ParseItemDraft>) {
        _state.update { state ->
            val items = state.previewItems ?: return@update state
            state.copy(previewItems = transform(items), saveError = null)
        }
    }

    private fun ParsedExpenseItem.toDraft(
        categories: List<Category>,
        date: LocalDate,
    ): ParseItemDraft =
        ParseItemDraft(
            id = nextItemId++,
            name = name,
            amountText = amount.toEditorText(),
            currency = currency,
            suggestedCategoryId = suggestedCategoryId,
            suggestedCategoryName = suggestedCategoryName,
            date = date,
        ).resolveCategory(categories)

    private fun ParseItemDraft.toInput(): ExpenseInput =
        ExpenseInput(
            name = name.trim(),
            amount = requireNotNull(parsedAmount),
            currency = currency,
            categoryId = requireNotNull(categoryId),
            note = null,
            date = date,
        )

    private fun today(): LocalDate = clock.now().toLocalDateTime(timeZone).date
}

private fun ParseItemDraft.resolveCategory(categories: List<Category>): ParseItemDraft {
    if (categoryId != null) return this
    val category =
        categories.firstOrNull { it.id == suggestedCategoryId }
            ?: categories.firstOrNull { it.name.equals(suggestedCategoryName, ignoreCase = true) }
    return copy(categoryId = category?.id)
}

private fun Double.toEditorText(): String {
    val raw = toString()
    return if (raw.endsWith(".0")) raw.removeSuffix(".0") else raw
}
