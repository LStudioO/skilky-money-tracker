package com.vstorchevyi.skilky.ui.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vstorchevyi.skilky.domain.model.Category
import com.vstorchevyi.skilky.domain.model.Either
import com.vstorchevyi.skilky.domain.usecase.CreateCategoryUseCase
import com.vstorchevyi.skilky.domain.usecase.DeleteCategoryUseCase
import com.vstorchevyi.skilky.domain.usecase.ObserveCategoriesUseCase
import com.vstorchevyi.skilky.domain.usecase.RefreshCategoriesUseCase
import com.vstorchevyi.skilky.domain.usecase.UpdateCategoryUseCase
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

class CategoriesViewModel(
    private val observeCategories: ObserveCategoriesUseCase,
    private val refreshCategories: RefreshCategoriesUseCase,
    private val createCategory: CreateCategoryUseCase,
    private val updateCategory: UpdateCategoryUseCase,
    private val deleteCategory: DeleteCategoryUseCase,
) : ViewModel() {
    private val _state = MutableStateFlow(CategoriesUiState())
    val state: StateFlow<CategoriesUiState> = _state.asStateFlow()

    private val _events =
        Channel<CategoriesEvent>(
            capacity = Channel.BUFFERED,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )
    val events = _events.receiveAsFlow()

    init {
        observeCategories()
            .onEach { list -> _state.update { it.copy(categories = list) } }
            .launchIn(viewModelScope)
        onRefresh()
    }

    fun onRefresh() {
        if (_state.value.isRefreshing) return
        _state.update { it.copy(isRefreshing = true) }
        viewModelScope.launch {
            val result = refreshCategories()
            _state.update { it.copy(isRefreshing = false) }
            if (result is Either.Left) {
                _events.trySend(CategoriesEvent.ShowError(result.value))
            }
        }
    }

    fun onAdd() {
        _state.update { it.copy(editing = EditingCategory()) }
    }

    fun onEdit(category: Category) {
        if (category.isDefault) return
        _state.update {
            it.copy(
                editing =
                    EditingCategory(
                        id = category.id,
                        name = category.name,
                        icon = category.icon,
                        color = category.color,
                        originalIsDefault = category.isDefault,
                    ),
            )
        }
    }

    fun onDraftNameChange(value: String) {
        _state.update { state -> state.copy(editing = state.editing?.copy(name = value)) }
    }

    fun onDraftIconChange(value: String) {
        _state.update { state -> state.copy(editing = state.editing?.copy(icon = value)) }
    }

    fun onDraftColorChange(value: String) {
        _state.update { state -> state.copy(editing = state.editing?.copy(color = value)) }
    }

    fun onDismissDialog() {
        _state.update { it.copy(editing = null) }
    }

    fun onSave() {
        val draft = _state.value.editing ?: return
        if (!draft.canSave) return
        viewModelScope.launch {
            val result =
                if (draft.id == null) {
                    createCategory(
                        name = draft.name.trim(),
                        icon = draft.icon.trim(),
                        color = draft.color.trim(),
                    )
                } else {
                    updateCategory(
                        id = draft.id,
                        name = draft.name.trim(),
                        icon = draft.icon.trim(),
                        color = draft.color.trim(),
                    )
                }
            when (result) {
                is Either.Right -> _state.update { it.copy(editing = null) }
                is Either.Left -> _events.trySend(CategoriesEvent.ShowError(result.value))
            }
        }
    }

    fun onDelete(category: Category) {
        if (category.isDefault) return
        viewModelScope.launch {
            val result = deleteCategory(category.id)
            if (result is Either.Left) {
                _events.trySend(CategoriesEvent.ShowError(result.value))
            }
        }
    }
}
