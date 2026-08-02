package com.vstorchevyi.skilky.ui.input

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.vstorchevyi.skilky.api.Currency
import com.vstorchevyi.skilky.domain.model.AppError
import com.vstorchevyi.skilky.ui.expense.CategoryDropdown
import com.vstorchevyi.skilky.ui.expense.CurrencyDropdown
import com.vstorchevyi.skilky.ui.expense.DateField
import kotlinx.datetime.LocalDate

internal data class InputActions(
    val onQueryChange: (String) -> Unit = {},
    val onSubmit: () -> Unit = {},
    val onDismissPreview: () -> Unit = {},
    val onAddItem: () -> Unit = {},
    val onEditItem: (Long) -> Unit = {},
    val onDoneEditing: (Long) -> Unit = {},
    val onDeleteItem: (Long) -> Unit = {},
    val onNameChange: (Long, String) -> Unit = { _, _ -> },
    val onAmountChange: (Long, String) -> Unit = { _, _ -> },
    val onCurrencyChange: (Long, Currency) -> Unit = { _, _ -> },
    val onCategoryChange: (Long, Long) -> Unit = { _, _ -> },
    val onDateChange: (Long, LocalDate) -> Unit = { _, _ -> },
    val onSaveAll: () -> Unit = {},
)

@Composable
internal fun QuickEntryBar(
    state: InputUiState,
    actions: InputActions,
) {
    Surface(tonalElevation = 3.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = state.query,
                onValueChange = actions.onQueryChange,
                label = { Text("Add expenses") },
                placeholder = { Text("Milk 45, bread 22") },
                singleLine = true,
                enabled = !state.isParsing,
                isError = state.parseError != null,
                supportingText = state.parseError?.let { error -> { Text(error.toMessage()) } },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { actions.onSubmit() }),
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = actions.onSubmit,
                enabled = state.canSubmit,
            ) {
                if (state.isParsing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text("Send")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ParsePreviewSheet(
    state: InputUiState,
    actions: InputActions,
) {
    val items = state.previewItems ?: return
    ModalBottomSheet(onDismissRequest = actions.onDismissPreview) {
        Column(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.9f),
        ) {
            PreviewHeader(itemCount = items.size, onClose = actions.onDismissPreview)
            HorizontalDivider()
            LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                items(items, key = ParseItemDraft::id) { item ->
                    if (item.isEditing) {
                        EditablePreviewItem(
                            item = item,
                            state = state,
                            actions = actions,
                        )
                    } else {
                        PreviewItem(
                            item = item,
                            state = state,
                            actions = actions,
                        )
                    }
                    HorizontalDivider()
                }
                item(key = "add-item") {
                    TextButton(
                        onClick = actions.onAddItem,
                        enabled = !state.isSaving,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    ) {
                        Text("Add item")
                    }
                }
            }
            PreviewActions(state = state, actions = actions)
        }
    }
}

@Composable
private fun PreviewHeader(
    itemCount: Int,
    onClose: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Parsed $itemCount ${if (itemCount == 1) "item" else "items"}",
            style = MaterialTheme.typography.titleLarge,
        )
        TextButton(onClick = onClose) { Text("Close") }
    }
}

@Composable
private fun PreviewItem(
    item: ParseItemDraft,
    state: InputUiState,
    actions: InputActions,
) {
    val category = state.categories.firstOrNull { it.id == item.categoryId }
    Column(modifier = Modifier.fillMaxWidth()) {
        ListItem(
            headlineContent = { Text(item.name) },
            supportingContent = {
                Text(
                    category?.let { "${it.icon}  ${it.name}" }
                        ?: item.suggestedCategoryName?.let { "Pick a category (suggested: $it)" }
                        ?: "Pick a category",
                )
            },
            trailingContent = {
                Text("${item.amountText} ${item.currency.code}")
            },
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = { actions.onEditItem(item.id) }, enabled = !state.isSaving) {
                Text("Edit")
            }
            TextButton(onClick = { actions.onDeleteItem(item.id) }, enabled = !state.isSaving) {
                Text("Delete")
            }
        }
    }
}

@Composable
private fun EditablePreviewItem(
    item: ParseItemDraft,
    state: InputUiState,
    actions: InputActions,
) {
    val controlsEnabled = !state.isSaving
    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(
            value = item.name,
            onValueChange = { actions.onNameChange(item.id, it) },
            label = { Text("Name") },
            singleLine = true,
            enabled = controlsEnabled,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = item.amountText,
            onValueChange = { actions.onAmountChange(item.id, it) },
            label = { Text("Amount") },
            singleLine = true,
            enabled = controlsEnabled,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
        CurrencyDropdown(
            selected = item.currency,
            enabled = controlsEnabled,
            onSelect = { actions.onCurrencyChange(item.id, it) },
        )
        CategoryDropdown(
            categories = state.categories,
            selectedId = item.categoryId,
            enabled = controlsEnabled,
            onSelect = { actions.onCategoryChange(item.id, it) },
        )
        DateField(
            date = item.date,
            enabled = controlsEnabled,
            onSelect = { actions.onDateChange(item.id, it) },
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = { actions.onDeleteItem(item.id) }, enabled = controlsEnabled) {
                Text("Delete")
            }
            OutlinedButton(onClick = { actions.onDoneEditing(item.id) }, enabled = controlsEnabled) {
                Text("Done")
            }
        }
    }
}

@Composable
private fun PreviewActions(
    state: InputUiState,
    actions: InputActions,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        state.saveError?.let { error ->
            Text(
                text = error.toMessage(),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Button(
            onClick = actions.onSaveAll,
            enabled = state.canSave && !state.isSaving,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (state.isSaving) "Saving..." else "Save all")
        }
    }
}

private fun InputError.toMessage(): String =
    when (this) {
        InputError.NoItems -> "No expenses found. Try rephrasing."
        is InputError.Request -> error.toMessage()
    }

private fun AppError.toMessage(): String =
    when (this) {
        AppError.Unauthorized -> "Your session expired. Sign in again."
        AppError.Validation -> "Couldn't parse that. Try rephrasing."
        AppError.Conflict -> "Conflict. Try again."
        AppError.Network -> "Network problem. Check your connection."
        AppError.Storage -> "The server may have saved these items. Restart the app before retrying."
        AppError.Unknown -> "Couldn't parse that. Try again."
    }
