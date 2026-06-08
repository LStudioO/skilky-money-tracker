package com.vstorchevyi.skilky.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel

/**
 * Stateful entry point: pulls state from the [HomeViewModel] and forwards the
 * events to the nav layer. UI lives in [HomeScreenContent], which previews
 * and unit tests can render with hand-built state.
 */
@Composable
fun HomeScreen(
    onSignedOut: () -> Unit,
    viewModel: HomeViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                HomeEvent.NavigateToLogin -> onSignedOut()
            }
        }
    }

    HomeScreenContent(
        state = state,
        onSignOut = viewModel::onSignOut,
    )
}

@Composable
fun HomeScreenContent(
    state: HomeUiState,
    onSignOut: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .safeContentPadding()
                .fillMaxSize()
                .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Welcome", style = MaterialTheme.typography.headlineMedium)
        Text(
            state.displayName.ifBlank { "Skilky user" },
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            state.email,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 4.dp),
        )

        TextButton(
            onClick = onSignOut,
            modifier = Modifier.padding(top = 24.dp),
        ) {
            Text("Sign out")
        }
    }
}

@Preview
@Composable
private fun HomeScreenContentPreview() {
    MaterialTheme {
        HomeScreenContent(
            state = HomeUiState(displayName = "Vlad", email = "v@example.com"),
            onSignOut = {},
        )
    }
}
