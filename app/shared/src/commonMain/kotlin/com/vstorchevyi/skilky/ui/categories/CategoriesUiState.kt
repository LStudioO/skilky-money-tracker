package com.vstorchevyi.skilky.ui.categories

import com.vstorchevyi.skilky.domain.model.AppError
import com.vstorchevyi.skilky.domain.model.Category

/**
 * Snapshot of the categories screen. `editing` is non-null while the add/edit
 * dialog is open; when the user is creating a new category the draft holds
 * an empty id (placeholder) and the dialog renders as "Add category".
 */
data class CategoriesUiState(
    val categories: List<Category> = emptyList(),
    val isRefreshing: Boolean = false,
    val editing: EditingCategory? = null,
    val error: AppError? = null,
)

/**
 * Draft state for the add/edit dialog. `id` is null for a new category and
 * the server-assigned id for an edit. `originalIsDefault` lets the dialog
 * decide whether to disable destructive controls.
 */
data class EditingCategory(
    val id: Long? = null,
    val name: String = "",
    val icon: String = "📁",
    val color: String = "#888888",
    val originalIsDefault: Boolean = false,
) {
    val canSave: Boolean
        get() = name.isNotBlank() && icon.isNotBlank() && color.isNotBlank()
}

sealed interface CategoriesEvent {
    /** Surfaces a mutation failure as a snackbar / toast. */
    data class ShowError(
        val error: AppError,
    ) : CategoriesEvent
}
