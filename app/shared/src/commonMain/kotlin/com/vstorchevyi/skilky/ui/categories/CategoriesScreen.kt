package com.vstorchevyi.skilky.ui.categories

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vstorchevyi.skilky.domain.model.AppError
import com.vstorchevyi.skilky.domain.model.Category
import org.koin.compose.viewmodel.koinViewModel

/**
 * Stateful entry point: pulls state from the [CategoriesViewModel] and pumps
 * one-shot events into a [SnackbarHostState]. UI lives in
 * [CategoriesScreenContent], which previews and unit tests can render with
 * hand-built state.
 */
@Composable
fun CategoriesScreen(
    onBack: () -> Unit,
    viewModel: CategoriesViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is CategoriesEvent.ShowError -> {
                    snackbarHostState.showSnackbar(event.error.toMessage())
                }
            }
        }
    }

    CategoriesScreenContent(
        state = state,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onAdd = viewModel::onAdd,
        onEdit = viewModel::onEdit,
        onDelete = viewModel::onDelete,
        onDraftNameChange = viewModel::onDraftNameChange,
        onDraftIconChange = viewModel::onDraftIconChange,
        onDraftColorChange = viewModel::onDraftColorChange,
        onSaveDraft = viewModel::onSave,
        onDismissDraft = viewModel::onDismissDialog,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreenContent(
    state: CategoriesUiState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (Category) -> Unit,
    onDelete: (Category) -> Unit,
    onDraftNameChange: (String) -> Unit,
    onDraftIconChange: (String) -> Unit,
    onDraftColorChange: (String) -> Unit,
    onSaveDraft: () -> Unit,
    onDismissDraft: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Categories") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("Back") }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAdd,
                text = { Text("Add") },
                icon = { Text("+") },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        CategoryList(
            state = state,
            onEdit = onEdit,
            onDelete = onDelete,
            padding = padding,
        )
    }

    state.editing?.let { draft ->
        EditCategoryDialog(
            draft = draft,
            onNameChange = onDraftNameChange,
            onIconChange = onDraftIconChange,
            onColorChange = onDraftColorChange,
            onSave = onSaveDraft,
            onDismiss = onDismissDraft,
        )
    }
}

@Composable
private fun CategoryList(
    state: CategoriesUiState,
    onEdit: (Category) -> Unit,
    onDelete: (Category) -> Unit,
    padding: PaddingValues,
) {
    if (state.categories.isEmpty() && state.isRefreshing) {
        Box(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
        items(state.categories, key = { it.id }) { category ->
            CategoryRow(category = category, onEdit = onEdit, onDelete = onDelete)
            HorizontalDivider()
        }
    }
}

@Composable
private fun CategoryRow(
    category: Category,
    onEdit: (Category) -> Unit,
    onDelete: (Category) -> Unit,
) {
    val rowModifier =
        if (category.isDefault) {
            Modifier.fillMaxWidth()
        } else {
            Modifier.fillMaxWidth().clickable { onEdit(category) }
        }
    ListItem(
        modifier = rowModifier,
        headlineContent = { Text(category.name) },
        supportingContent = {
            Text(category.color, style = MaterialTheme.typography.bodySmall)
        },
        leadingContent = { Text(category.icon) },
        trailingContent = {
            if (category.isDefault) {
                Text("default", style = MaterialTheme.typography.labelSmall)
            } else {
                IconButton(onClick = { onDelete(category) }) {
                    Text("✕")
                }
            }
        },
    )
}

@Composable
private fun EditCategoryDialog(
    draft: EditingCategory,
    onNameChange: (String) -> Unit,
    onIconChange: (String) -> Unit,
    onColorChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (draft.id == null) "Add category" else "Edit category") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = draft.name,
                    onValueChange = onNameChange,
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = draft.icon,
                        onValueChange = onIconChange,
                        label = { Text("Icon") },
                        singleLine = true,
                        modifier = Modifier.width(96.dp),
                    )
                    Spacer(Modifier.size(8.dp))
                    OutlinedTextField(
                        value = draft.color,
                        onValueChange = onColorChange,
                        label = { Text("Color (hex)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onSave, enabled = draft.canSave) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

private fun AppError.toMessage(): String =
    when (this) {
        AppError.Unauthorized -> "Your session expired. Sign in again."
        AppError.Validation -> "Check the values and try again."
        AppError.Conflict -> "That name is already in use."
        AppError.Network -> "Network problem. Check your connection."
        AppError.Unknown -> "Something went wrong. Please try again."
    }

@Preview
@Composable
private fun CategoriesScreenContentPreview() {
    MaterialTheme {
        CategoriesScreenContent(
            state =
                CategoriesUiState(
                    categories =
                        listOf(
                            Category(1, "Food", "🍎", "#FF6B6B", isDefault = true, nameKey = "food"),
                            Category(2, "Coffee", "☕", "#8B4513", isDefault = false),
                        ),
                ),
            snackbarHostState = remember { SnackbarHostState() },
            onBack = {},
            onAdd = {},
            onEdit = {},
            onDelete = {},
            onDraftNameChange = {},
            onDraftIconChange = {},
            onDraftColorChange = {},
            onSaveDraft = {},
            onDismissDraft = {},
        )
    }
}

@Preview
@Composable
private fun CategoriesScreenContentEditingPreview() {
    MaterialTheme {
        CategoriesScreenContent(
            state =
                CategoriesUiState(
                    categories = emptyList(),
                    editing = EditingCategory(name = "Coffee", icon = "☕", color = "#8B4513"),
                ),
            snackbarHostState = remember { SnackbarHostState() },
            onBack = {},
            onAdd = {},
            onEdit = {},
            onDelete = {},
            onDraftNameChange = {},
            onDraftIconChange = {},
            onDraftColorChange = {},
            onSaveDraft = {},
            onDismissDraft = {},
        )
    }
}
