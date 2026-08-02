package com.vstorchevyi.skilky.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vstorchevyi.skilky.api.Currency
import com.vstorchevyi.skilky.api.InputType
import com.vstorchevyi.skilky.domain.model.AppError
import com.vstorchevyi.skilky.domain.model.Expense
import com.vstorchevyi.skilky.domain.model.ExpenseCategorySnapshot
import com.vstorchevyi.skilky.ui.input.InputActions
import com.vstorchevyi.skilky.ui.input.InputEvent
import com.vstorchevyi.skilky.ui.input.InputUiState
import com.vstorchevyi.skilky.ui.input.InputViewModel
import com.vstorchevyi.skilky.ui.input.ParsePreviewSheet
import com.vstorchevyi.skilky.ui.input.QuickEntryBar
import com.vstorchevyi.skilky.ui.input.rememberReceiptCameraLauncher
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.readBytes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import org.koin.compose.viewmodel.koinViewModel
import kotlin.math.roundToLong
import kotlin.time.Instant

/**
 * Stateful entry point: pulls state from the [HomeViewModel], routes events
 * to navigation, and surfaces refresh failures via a snackbar. UI lives in
 * [HomeScreenContent], which previews and unit tests can render with
 * hand-built state.
 */
@Composable
fun HomeScreen(
    onSignedOut: () -> Unit,
    onOpenCategories: () -> Unit,
    onAddExpense: () -> Unit,
    onOpenExpense: (Long) -> Unit,
    viewModel: HomeViewModel = koinViewModel(),
    inputViewModel: InputViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val inputState by inputViewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val receiptLaunchers = rememberReceiptLaunchers(inputViewModel)

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                HomeEvent.NavigateToLogin -> onSignedOut()
                is HomeEvent.ShowError -> snackbarHostState.showSnackbar(event.error.toMessage())
            }
        }
    }

    LaunchedEffect(inputViewModel) {
        inputViewModel.events.collect { event ->
            when (event) {
                is InputEvent.Saved -> {
                    val noun = if (event.count == 1) "expense" else "expenses"
                    snackbarHostState.showSnackbar("Saved ${event.count} $noun.")
                }

                is InputEvent.ShowError -> {
                    snackbarHostState.showSnackbar(event.error.toMessage())
                }
            }
        }
    }

    HomeScreenContent(
        state = state,
        inputState = inputState,
        snackbarHostState = snackbarHostState,
        onOpenCategories = onOpenCategories,
        onSignOut = viewModel::onSignOut,
        onAddExpense = onAddExpense,
        onOpenExpense = onOpenExpense,
        inputActions =
            InputActions(
                onQueryChange = inputViewModel::onQueryChange,
                onSubmit = inputViewModel::onSubmit,
                onPickReceipt = receiptLaunchers.pick,
                onCaptureReceipt = receiptLaunchers.capture ?: {},
                canCaptureReceipt = receiptLaunchers.capture != null,
                onDismissPreview = inputViewModel::onDismissPreview,
                onAddItem = inputViewModel::onAddItem,
                onEditItem = inputViewModel::onEditItem,
                onDoneEditing = inputViewModel::onDoneEditing,
                onDeleteItem = inputViewModel::onDeleteItem,
                onNameChange = inputViewModel::onNameChange,
                onAmountChange = inputViewModel::onAmountChange,
                onCurrencyChange = inputViewModel::onCurrencyChange,
                onCategoryChange = inputViewModel::onCategoryChange,
                onDateChange = inputViewModel::onDateChange,
                onSaveAll = inputViewModel::onSaveAll,
            ),
    )
}

private data class ReceiptLaunchers(
    val pick: () -> Unit,
    val capture: (() -> Unit)?,
)

@Composable
private fun rememberReceiptLaunchers(inputViewModel: InputViewModel): ReceiptLaunchers {
    val scope = rememberCoroutineScope()
    val handleFile: (PlatformFile?) -> Unit = { file ->
        if (file != null) {
            scope.launch {
                val bytes =
                    try {
                        file.readBytes()
                    } catch (error: CancellationException) {
                        throw error
                    } catch (
                        @Suppress("TooGenericExceptionCaught", "SwallowedException") error: Exception,
                    ) {
                        null
                    }
                inputViewModel.onReceiptSelected(bytes)
            }
        }
    }
    val picker = rememberFilePickerLauncher(type = FileKitType.Image, onResult = handleFile)
    val camera = rememberReceiptCameraLauncher(onResult = handleFile)
    return ReceiptLaunchers(pick = picker::launch, capture = camera)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HomeScreenContent(
    state: HomeUiState,
    inputState: InputUiState = InputUiState(),
    snackbarHostState: SnackbarHostState,
    onOpenCategories: () -> Unit,
    onSignOut: () -> Unit,
    onAddExpense: () -> Unit,
    onOpenExpense: (Long) -> Unit,
    inputActions: InputActions = InputActions(),
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(state.displayName.ifBlank { "Skilky user" })
                        Text(
                            state.email,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                },
                actions = {
                    TextButton(onClick = onOpenCategories) { Text("Categories") }
                    TextButton(onClick = onSignOut) { Text("Sign out") }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddExpense,
                text = { Text("Add expense") },
                icon = { Text("+") },
            )
        },
        bottomBar = { QuickEntryBar(state = inputState, actions = inputActions) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        ExpenseList(state = state, padding = padding, onOpenExpense = onOpenExpense)
    }
    if (inputState.previewItems != null) {
        ParsePreviewSheet(state = inputState, actions = inputActions)
    }
}

@Composable
private fun ExpenseList(
    state: HomeUiState,
    padding: PaddingValues,
    onOpenExpense: (Long) -> Unit,
) {
    if (state.groups.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (state.isRefreshing) "Loading…" else "No expenses yet.",
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
        state.groups.forEach { group ->
            item(key = "header-${group.date}") { DateHeader(group) }
            items(group.items, key = { it.id }) { expense ->
                ExpenseRow(expense, onClick = { onOpenExpense(expense.id) })
            }
            item(key = "divider-${group.date}") { HorizontalDivider() }
        }
    }
}

@Composable
private fun DateHeader(group: ExpenseGroup) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(group.date.toString(), style = MaterialTheme.typography.titleSmall)
            Text(formatMoney(group.total), style = MaterialTheme.typography.titleSmall)
        }
    }
}

@Composable
private fun ExpenseRow(
    expense: Expense,
    onClick: () -> Unit,
) {
    ListItem(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        leadingContent = { Text(expense.category.icon) },
        headlineContent = { Text(expense.name) },
        supportingContent = { Text(expense.category.name) },
        trailingContent = {
            Text(
                "${formatMoney(expense.amount)} ${expense.currency.symbol}",
                style = MaterialTheme.typography.bodyMedium,
            )
        },
    )
}

private fun AppError.toMessage(): String =
    when (this) {
        AppError.Unauthorized -> "Your session expired. Sign in again."
        AppError.Validation -> "Check the values and try again."
        AppError.Conflict -> "Conflict. Try again."
        AppError.Network -> "Network problem. Check your connection."
        AppError.Storage -> "Local data couldn't be updated. Restart the app and try again."
        AppError.Unknown -> "Something went wrong. Please try again."
    }

/**
 * Cheap "two-decimal" money formatter that works in commonMain (no
 * `String.format`, no `java.text.NumberFormat`). Per-locale formatting and
 * grouping separators arrive with the Phase 8 `CurrencyFormatter`.
 */
internal fun formatMoney(amount: Double): String {
    val cents = (amount * 100).roundToLong()
    val whole = cents / 100
    val fraction = (cents % 100).let { if (it < 0) -it else it }
    val fractionPadded = if (fraction < 10) "0$fraction" else fraction.toString()
    return "$whole.$fractionPadded"
}

@Preview
@Composable
private fun HomeScreenContentEmptyPreview() {
    MaterialTheme {
        HomeScreenContent(
            state = HomeUiState(displayName = "Vlad", email = "v@example.com"),
            snackbarHostState = remember { SnackbarHostState() },
            onOpenCategories = {},
            onSignOut = {},
            onAddExpense = {},
            onOpenExpense = {},
        )
    }
}

@Preview
@Composable
private fun HomeScreenContentPopulatedPreview() {
    MaterialTheme {
        HomeScreenContent(
            state =
                HomeUiState(
                    displayName = "Vlad",
                    email = "v@example.com",
                    groups =
                        listOf(
                            ExpenseGroup(
                                date = LocalDate(2026, 6, 8),
                                items =
                                    listOf(
                                        anExpense(id = 1, name = "Milk", amount = 45.0, icon = "🍎"),
                                        anExpense(id = 2, name = "Bread", amount = 22.5, icon = "🍎"),
                                    ),
                                total = 67.5,
                            ),
                            ExpenseGroup(
                                date = LocalDate(2026, 6, 7),
                                items =
                                    listOf(
                                        anExpense(id = 3, name = "Taxi", amount = 120.0, icon = "🚕"),
                                    ),
                                total = 120.0,
                            ),
                        ),
                ),
            snackbarHostState = remember { SnackbarHostState() },
            onOpenCategories = {},
            onSignOut = {},
            onAddExpense = {},
            onOpenExpense = {},
        )
    }
}

private fun anExpense(
    id: Long,
    name: String,
    amount: Double,
    icon: String,
): Expense =
    Expense(
        id = id,
        name = name,
        amount = amount,
        currency = Currency.UAH,
        category = ExpenseCategorySnapshot(id = 1, name = "Food", icon = icon, color = "#FF6B6B"),
        note = null,
        inputType = InputType.TEXT,
        date = LocalDate(2026, 6, 8),
        createdAt = Instant.fromEpochMilliseconds(0),
    )
