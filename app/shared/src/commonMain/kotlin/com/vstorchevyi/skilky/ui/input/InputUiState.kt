package com.vstorchevyi.skilky.ui.input

import com.vstorchevyi.skilky.api.Currency
import com.vstorchevyi.skilky.api.InputType
import com.vstorchevyi.skilky.domain.model.AppError
import com.vstorchevyi.skilky.domain.model.Category
import kotlinx.datetime.LocalDate

data class InputUiState(
    val query: String = "",
    val isParsing: Boolean = false,
    val parseError: InputError? = null,
    val previewItems: List<ParseItemDraft>? = null,
    val previewInputType: InputType = InputType.TEXT,
    val categories: List<Category> = emptyList(),
    val isSaving: Boolean = false,
    val saveError: AppError? = null,
) {
    val canSubmit: Boolean
        get() = query.isNotBlank() && !isParsing

    val canSave: Boolean
        get() = previewItems?.let { items -> items.isNotEmpty() && items.all(ParseItemDraft::canSave) } == true
}

data class ParseItemDraft(
    val id: Long,
    val name: String = "",
    val amountText: String = "",
    val currency: Currency = Currency.UAH,
    val categoryId: Long? = null,
    val suggestedCategoryId: Long? = null,
    val suggestedCategoryName: String? = null,
    val date: LocalDate,
    val isEditing: Boolean = false,
) {
    val parsedAmount: Double?
        get() = amountText.trim().replace(',', '.').toDoubleOrNull()

    val canSave: Boolean
        get() = name.isNotBlank() && (parsedAmount ?: 0.0) > 0.0 && categoryId != null
}

sealed interface InputError {
    data object NoItems : InputError

    data class NoAudioItems(
        val transcript: String,
    ) : InputError

    data object UnsupportedAudio : InputError

    data object AudioTooLarge : InputError

    data object UnsupportedImage : InputError

    data object ImageTooLarge : InputError

    data class Request(
        val error: AppError,
    ) : InputError
}

sealed interface InputEvent {
    data class Saved(
        val count: Int,
    ) : InputEvent

    data class ShowError(
        val error: AppError,
    ) : InputEvent
}
