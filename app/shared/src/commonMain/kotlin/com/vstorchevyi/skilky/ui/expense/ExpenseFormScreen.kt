package com.vstorchevyi.skilky.ui.expense

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vstorchevyi.skilky.api.Currency
import com.vstorchevyi.skilky.domain.model.AppError
import com.vstorchevyi.skilky.domain.model.Category
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlin.time.Instant

/**
 * Stateful entry point. Both the create and edit destinations land here:
 * the [expenseId] tells the ViewModel which mode to start in. On save or
 * delete the screen pops itself off the back stack via [onClose].
 */
@Composable
fun ExpenseFormScreen(
    expenseId: Long?,
    onClose: () -> Unit,
    viewModel: ExpenseFormViewModel = koinViewModel { parametersOf(expenseId) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                ExpenseFormEvent.Saved, ExpenseFormEvent.Deleted -> {
                    onClose()
                }

                is ExpenseFormEvent.ShowError -> {
                    snackbarHostState.showSnackbar(event.error.toMessage())
                }
            }
        }
    }

    ExpenseFormScreenContent(
        state = state,
        snackbarHostState = snackbarHostState,
        onBack = onClose,
        onNameChange = viewModel::onNameChange,
        onAmountChange = viewModel::onAmountChange,
        onCurrencyChange = viewModel::onCurrencyChange,
        onCategoryChange = viewModel::onCategoryChange,
        onDateChange = viewModel::onDateChange,
        onNoteChange = viewModel::onNoteChange,
        onSave = viewModel::onSave,
        onDelete = viewModel::onDelete,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseFormScreenContent(
    state: ExpenseFormUiState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onNameChange: (String) -> Unit,
    onAmountChange: (String) -> Unit,
    onCurrencyChange: (Currency) -> Unit,
    onCategoryChange: (Long) -> Unit,
    onDateChange: (LocalDate) -> Unit,
    onNoteChange: (String) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
) {
    val title = if (state.mode == ExpenseFormUiState.Mode.New) "Add expense" else "Edit expense"
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("Back") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        when {
            state.isLoading -> {
                LoadingBox(padding)
            }

            state.notFound -> {
                NotFoundBox(padding, onBack = onBack)
            }

            else -> {
                FormBody(
                    state = state,
                    padding = padding,
                    onNameChange = onNameChange,
                    onAmountChange = onAmountChange,
                    onCurrencyChange = onCurrencyChange,
                    onCategoryChange = onCategoryChange,
                    onDateChange = onDateChange,
                    onNoteChange = onNoteChange,
                    onSave = onSave,
                    onDelete = onDelete,
                )
            }
        }
    }
}

@Composable
private fun LoadingBox(padding: PaddingValues) {
    Box(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun NotFoundBox(
    padding: PaddingValues,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Expense not found.", style = MaterialTheme.typography.bodyLarge)
        OutlinedButton(onClick = onBack) { Text("Back") }
    }
}

@Composable
private fun FormBody(
    state: ExpenseFormUiState,
    padding: PaddingValues,
    onNameChange: (String) -> Unit,
    onAmountChange: (String) -> Unit,
    onCurrencyChange: (Currency) -> Unit,
    onCategoryChange: (Long) -> Unit,
    onDateChange: (LocalDate) -> Unit,
    onNoteChange: (String) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
) {
    val draft = state.draft
    val controlsEnabled = !state.isSaving && !state.isDeleting
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        FormFields(
            draft = draft,
            categories = state.categories,
            controlsEnabled = controlsEnabled,
            onNameChange = onNameChange,
            onAmountChange = onAmountChange,
            onCurrencyChange = onCurrencyChange,
            onCategoryChange = onCategoryChange,
            onDateChange = onDateChange,
            onNoteChange = onNoteChange,
        )
        FormActions(
            mode = state.mode,
            canSave = draft.canSave,
            controlsEnabled = controlsEnabled,
            isSaving = state.isSaving,
            isDeleting = state.isDeleting,
            onSave = onSave,
            onDelete = onDelete,
        )
    }
}

@Composable
private fun FormFields(
    draft: ExpenseDraft,
    categories: List<Category>,
    controlsEnabled: Boolean,
    onNameChange: (String) -> Unit,
    onAmountChange: (String) -> Unit,
    onCurrencyChange: (Currency) -> Unit,
    onCategoryChange: (Long) -> Unit,
    onDateChange: (LocalDate) -> Unit,
    onNoteChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = draft.name,
        onValueChange = onNameChange,
        label = { Text("Name") },
        singleLine = true,
        enabled = controlsEnabled,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = draft.amountText,
        onValueChange = onAmountChange,
        label = { Text("Amount") },
        singleLine = true,
        enabled = controlsEnabled,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
    )
    CurrencyDropdown(selected = draft.currency, enabled = controlsEnabled, onSelect = onCurrencyChange)
    CategoryDropdown(
        categories = categories,
        selectedId = draft.categoryId,
        enabled = controlsEnabled,
        onSelect = onCategoryChange,
    )
    DateField(date = draft.date, enabled = controlsEnabled, onSelect = onDateChange)
    OutlinedTextField(
        value = draft.note,
        onValueChange = onNoteChange,
        label = { Text("Note (optional)") },
        enabled = controlsEnabled,
        minLines = 2,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun FormActions(
    mode: ExpenseFormUiState.Mode,
    canSave: Boolean,
    controlsEnabled: Boolean,
    isSaving: Boolean,
    isDeleting: Boolean,
    onSave: () -> Unit,
    onDelete: () -> Unit,
) {
    Button(
        onClick = onSave,
        enabled = canSave && controlsEnabled,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(if (isSaving) "Saving…" else "Save")
    }
    if (mode == ExpenseFormUiState.Mode.Edit) {
        OutlinedButton(
            onClick = onDelete,
            enabled = controlsEnabled,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (isDeleting) "Deleting…" else "Delete")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CurrencyDropdown(
    selected: Currency,
    enabled: Boolean,
    onSelect: (Currency) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = it },
    ) {
        OutlinedTextField(
            value = "${selected.code} ${selected.symbol}",
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text("Currency") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            Currency.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text("${option.code} ${option.symbol}") },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryDropdown(
    categories: List<Category>,
    selectedId: Long?,
    enabled: Boolean,
    onSelect: (Long) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = categories.firstOrNull { it.id == selectedId }
    val display =
        when {
            selected != null -> "${selected.icon}  ${selected.name}"
            categories.isEmpty() -> "Loading…"
            else -> "Pick a category"
        }
    val anchorEnabled = enabled && categories.isNotEmpty()
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (anchorEnabled) expanded = it },
    ) {
        OutlinedTextField(
            value = display,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text("Category") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, anchorEnabled),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            categories.forEach { category ->
                DropdownMenuItem(
                    text = { Text("${category.icon}  ${category.name}") },
                    onClick = {
                        onSelect(category.id)
                        expanded = false
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateField(
    date: LocalDate,
    enabled: Boolean,
    onSelect: (LocalDate) -> Unit,
) {
    var showPicker by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = date.toString(),
            onValueChange = {},
            label = { Text("Date") },
            readOnly = true,
            enabled = false,
            modifier = Modifier.fillMaxWidth().weight(1f),
        )
        OutlinedButton(onClick = { showPicker = true }, enabled = enabled) {
            Text("Pick")
        }
    }
    if (showPicker) {
        val initialMillis = date.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val millis = pickerState.selectedDateMillis
                        if (millis != null) {
                            onSelect(millisToLocalDate(millis))
                        }
                        showPicker = false
                    },
                ) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text("Cancel") }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

private fun millisToLocalDate(millis: Long): LocalDate = Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.UTC).date

private fun AppError.toMessage(): String =
    when (this) {
        AppError.Unauthorized -> "Your session expired. Sign in again."
        AppError.Validation -> "Check the values and try again."
        AppError.Conflict -> "Conflict. Try again."
        AppError.Network -> "Network problem. Check your connection."
        AppError.Unknown -> "Something went wrong. Please try again."
    }

@Preview
@Composable
private fun ExpenseFormScreenContentNewPreview() {
    MaterialTheme {
        ExpenseFormScreenContent(
            state =
                ExpenseFormUiState(
                    mode = ExpenseFormUiState.Mode.New,
                    categories =
                        listOf(
                            Category(1, "Food", "🍎", "#FF6B6B", isDefault = true, nameKey = "food"),
                            Category(2, "Coffee", "☕", "#8B4513", isDefault = false),
                        ),
                    draft =
                        ExpenseDraft(
                            name = "Milk",
                            amountText = "45",
                            categoryId = 1,
                            date = LocalDate(2026, 6, 8),
                        ),
                ),
            snackbarHostState = remember { SnackbarHostState() },
            onBack = {},
            onNameChange = {},
            onAmountChange = {},
            onCurrencyChange = {},
            onCategoryChange = {},
            onDateChange = {},
            onNoteChange = {},
            onSave = {},
            onDelete = {},
        )
    }
}

@Preview
@Composable
private fun ExpenseFormScreenContentEditPreview() {
    MaterialTheme {
        ExpenseFormScreenContent(
            state =
                ExpenseFormUiState(
                    mode = ExpenseFormUiState.Mode.Edit,
                    categories =
                        listOf(
                            Category(1, "Food", "🍎", "#FF6B6B", isDefault = true, nameKey = "food"),
                        ),
                    draft =
                        ExpenseDraft(
                            name = "Taxi home",
                            amountText = "120.50",
                            categoryId = 1,
                            note = "split with Anya",
                            date = LocalDate(2026, 6, 7),
                        ),
                ),
            snackbarHostState = remember { SnackbarHostState() },
            onBack = {},
            onNameChange = {},
            onAmountChange = {},
            onCurrencyChange = {},
            onCategoryChange = {},
            onDateChange = {},
            onNoteChange = {},
            onSave = {},
            onDelete = {},
        )
    }
}
