package com.vstorchevyi.skilky.ui.input

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vstorchevyi.skilky.api.Currency
import com.vstorchevyi.skilky.api.InputType
import com.vstorchevyi.skilky.api.ParsedExpenseItem
import com.vstorchevyi.skilky.domain.model.Category
import com.vstorchevyi.skilky.domain.model.Either
import com.vstorchevyi.skilky.domain.model.ExpenseInput
import com.vstorchevyi.skilky.domain.usecase.CreateExpensesUseCase
import com.vstorchevyi.skilky.domain.usecase.GetCategoriesUseCase
import com.vstorchevyi.skilky.domain.usecase.ParseAudioUseCase
import com.vstorchevyi.skilky.domain.usecase.ParseReceiptUseCase
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
    private val parseAudio: ParseAudioUseCase,
    private val parseReceipt: ParseReceiptUseCase,
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
                    showParsedItems(result.value, InputType.TEXT)
                }
            }
        }
    }

    fun onReceiptSelected(bytes: ByteArray?) {
        val snapshot = _state.value
        when {
            snapshot.isParsing -> {
                Unit
            }

            bytes == null -> {
                _state.update { it.copy(parseError = InputError.UnsupportedImage) }
            }

            bytes.size > MAX_RECEIPT_BYTES -> {
                _state.update { it.copy(parseError = InputError.ImageTooLarge) }
            }

            !bytes.isJpegOrPng() -> {
                _state.update { it.copy(parseError = InputError.UnsupportedImage) }
            }

            else -> {
                _state.update { it.copy(isParsing = true, parseError = null) }
                viewModelScope.launch {
                    when (val result = parseReceipt(bytes, Currency.UAH)) {
                        is Either.Left -> {
                            _state.update {
                                it.copy(isParsing = false, parseError = InputError.Request(result.value))
                            }
                        }

                        is Either.Right -> {
                            showParsedItems(result.value, InputType.IMAGE)
                        }
                    }
                }
            }
        }
    }

    fun onAudioRecorded(bytes: ByteArray?) {
        val snapshot = _state.value
        when {
            snapshot.isParsing -> {
                Unit
            }

            bytes == null -> {
                _state.update { it.copy(parseError = InputError.UnsupportedAudio) }
            }

            bytes.size > MAX_AUDIO_BYTES -> {
                _state.update { it.copy(parseError = InputError.AudioTooLarge) }
            }

            !bytes.isWav() -> {
                _state.update { it.copy(parseError = InputError.UnsupportedAudio) }
            }

            else -> {
                _state.update { it.copy(isParsing = true, parseError = null) }
                viewModelScope.launch {
                    when (val result = parseAudio(bytes, Currency.UAH)) {
                        is Either.Left -> {
                            _state.update {
                                it.copy(isParsing = false, parseError = InputError.Request(result.value))
                            }
                        }

                        is Either.Right -> {
                            val response = result.value
                            val emptyItemsError =
                                response.transcript
                                    ?.trim()
                                    ?.takeIf(String::isNotEmpty)
                                    ?.let(InputError::NoAudioItems)
                                    ?: InputError.NoItems
                            showParsedItems(response.items, InputType.AUDIO, emptyItemsError)
                        }
                    }
                }
            }
        }
    }

    private fun showParsedItems(
        items: List<ParsedExpenseItem>,
        inputType: InputType,
        emptyItemsError: InputError = InputError.NoItems,
    ) {
        if (items.isEmpty()) {
            _state.update { it.copy(isParsing = false, parseError = emptyItemsError) }
            return
        }
        val categories = _state.value.categories
        val date = today(clock, timeZone)
        _state.update {
            it.copy(
                isParsing = false,
                parseError = null,
                previewItems = items.map { item -> item.toDraft(categories, date) },
                previewInputType = inputType,
                saveError = null,
            )
        }
    }

    fun onDismissPreview() {
        if (_state.value.isSaving) return
        _state.update {
            it.copy(
                previewItems = null,
                previewInputType = InputType.TEXT,
                saveError = null,
            )
        }
    }

    fun onAddItem() {
        val item = ParseItemDraft(id = nextItemId++, date = today(clock, timeZone), isEditing = true)
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
        val inputs = items.map { it.toInput(snapshot.previewInputType) }
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
                            previewInputType = InputType.TEXT,
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

    private fun ParseItemDraft.toInput(inputType: InputType): ExpenseInput =
        ExpenseInput(
            name = name.trim(),
            amount = requireNotNull(parsedAmount),
            currency = currency,
            categoryId = requireNotNull(categoryId),
            note = null,
            date = date,
            inputType = inputType,
        )
}

private fun today(
    clock: Clock,
    timeZone: TimeZone,
): LocalDate = clock.now().toLocalDateTime(timeZone).date

private fun ByteArray.isJpegOrPng(): Boolean = startsWith(JPEG_MAGIC) || startsWith(PNG_MAGIC)

private fun ByteArray.isWav(): Boolean =
    size >= WAV_HEADER_MIN_BYTES &&
        startsWith(RIFF_MAGIC) &&
        copyOfRange(WAVE_OFFSET, WAVE_OFFSET + WAVE_MAGIC.size).contentEquals(WAVE_MAGIC)

private fun ByteArray.startsWith(prefix: ByteArray): Boolean =
    size >= prefix.size && prefix.indices.all { index -> this[index] == prefix[index] }

private val RIFF_MAGIC = byteArrayOf(0x52, 0x49, 0x46, 0x46)
private val WAVE_MAGIC = byteArrayOf(0x57, 0x41, 0x56, 0x45)
private val JPEG_MAGIC = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())
private val PNG_MAGIC = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
private const val WAVE_OFFSET = 8
private const val WAV_HEADER_MIN_BYTES = 12
private const val MAX_AUDIO_BYTES = 10 * 1024 * 1024
private const val MAX_RECEIPT_BYTES = 10 * 1024 * 1024

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
