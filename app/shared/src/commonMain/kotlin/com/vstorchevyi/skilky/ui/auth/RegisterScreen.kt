package com.vstorchevyi.skilky.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vstorchevyi.skilky.domain.model.AppError
import org.koin.compose.viewmodel.koinViewModel

/**
 * Stateful entry point: pulls state from the [RegisterViewModel] and forwards
 * the events to the nav layer. UI lives in [RegisterScreenContent], which
 * previews and unit tests can render with hand-built state.
 */
@Composable
fun RegisterScreen(
    onRegistered: () -> Unit,
    onGoToLogin: () -> Unit,
    viewModel: RegisterViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                RegisterEvent.NavigateToHome -> onRegistered()
                RegisterEvent.NavigateToLogin -> onGoToLogin()
            }
        }
    }

    RegisterScreenContent(
        state = state,
        onDisplayNameChange = viewModel::onDisplayNameChange,
        onEmailChange = viewModel::onEmailChange,
        onPasswordChange = viewModel::onPasswordChange,
        onSubmit = viewModel::onSubmit,
        onGoToLogin = viewModel::onGoToLogin,
    )
}

@Composable
fun RegisterScreenContent(
    state: RegisterUiState,
    onDisplayNameChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onGoToLogin: () -> Unit,
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
        Text("Create account", style = MaterialTheme.typography.headlineMedium)

        OutlinedTextField(
            value = state.displayName,
            onValueChange = onDisplayNameChange,
            label = { Text("Display name") },
            singleLine = true,
            enabled = !state.isSubmitting,
            modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
        )

        OutlinedTextField(
            value = state.email,
            onValueChange = onEmailChange,
            label = { Text("Email") },
            singleLine = true,
            enabled = !state.isSubmitting,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        )

        OutlinedTextField(
            value = state.password,
            onValueChange = onPasswordChange,
            label = { Text("Password") },
            singleLine = true,
            enabled = !state.isSubmitting,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        )

        state.error?.let { RegisterErrorText(it) }

        Button(
            onClick = onSubmit,
            enabled = state.canSubmit,
            modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
        ) {
            if (state.isSubmitting) {
                CircularProgressIndicator(
                    modifier = Modifier.padding(end = 12.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
            Text("Create account")
        }

        TextButton(
            onClick = onGoToLogin,
            modifier = Modifier.padding(top = 8.dp),
        ) {
            Text("Already have an account? Sign in")
        }
    }
}

@Composable
private fun RegisterErrorText(error: AppError) {
    val message =
        when (error) {
            AppError.Conflict -> "An account with this email already exists."
            AppError.Validation -> "Email, password, or name didn't pass server checks."
            AppError.Unauthorized -> "Account creation was refused. Try again."
            AppError.Network -> "Network problem. Check your connection."
            AppError.Unknown -> "Something went wrong. Please try again."
        }
    Text(
        message,
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(top = 12.dp),
    )
}

@Preview
@Composable
private fun RegisterScreenContentPreview() {
    MaterialTheme {
        RegisterScreenContent(
            state =
                RegisterUiState(
                    displayName = "Vlad",
                    email = "v@example.com",
                    password = "secret",
                ),
            onDisplayNameChange = {},
            onEmailChange = {},
            onPasswordChange = {},
            onSubmit = {},
            onGoToLogin = {},
        )
    }
}

@Preview
@Composable
private fun RegisterScreenContentErrorPreview() {
    MaterialTheme {
        RegisterScreenContent(
            state =
                RegisterUiState(
                    displayName = "Vlad",
                    email = "v@example.com",
                    password = "secret",
                    error = AppError.Conflict,
                ),
            onDisplayNameChange = {},
            onEmailChange = {},
            onPasswordChange = {},
            onSubmit = {},
            onGoToLogin = {},
        )
    }
}
