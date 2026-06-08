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
 * Stateful entry point: pulls state from the [LoginViewModel] and forwards
 * the events to the nav layer. UI lives in [LoginScreenContent], which
 * previews and unit tests can render with hand-built state.
 */
@Composable
fun LoginScreen(
    onSignedIn: () -> Unit,
    onGoToRegister: () -> Unit,
    viewModel: LoginViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                LoginEvent.NavigateToHome -> onSignedIn()
                LoginEvent.NavigateToRegister -> onGoToRegister()
            }
        }
    }

    LoginScreenContent(
        state = state,
        onEmailChange = viewModel::onEmailChange,
        onPasswordChange = viewModel::onPasswordChange,
        onSubmit = viewModel::onSubmit,
        onGoToRegister = viewModel::onGoToRegister,
    )
}

@Composable
fun LoginScreenContent(
    state: LoginUiState,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onGoToRegister: () -> Unit,
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
        Text("Sign in", style = MaterialTheme.typography.headlineMedium)

        OutlinedTextField(
            value = state.email,
            onValueChange = onEmailChange,
            label = { Text("Email") },
            singleLine = true,
            enabled = !state.isSubmitting,
            modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
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

        state.error?.let { LoginErrorText(it) }

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
            Text("Sign in")
        }

        TextButton(
            onClick = onGoToRegister,
            modifier = Modifier.padding(top = 8.dp),
        ) {
            Text("Create an account")
        }
    }
}

@Composable
private fun LoginErrorText(error: AppError) {
    val message =
        when (error) {
            AppError.Unauthorized -> "Email or password is incorrect."
            AppError.Validation -> "Check your email and password format."
            AppError.Conflict -> "Account state conflict. Try again."
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
private fun LoginScreenContentPreview() {
    MaterialTheme {
        LoginScreenContent(
            state = LoginUiState(email = "v@example.com", password = "secret"),
            onEmailChange = {},
            onPasswordChange = {},
            onSubmit = {},
            onGoToRegister = {},
        )
    }
}

@Preview
@Composable
private fun LoginScreenContentSubmittingPreview() {
    MaterialTheme {
        LoginScreenContent(
            state =
                LoginUiState(
                    email = "v@example.com",
                    password = "secret",
                    isSubmitting = true,
                ),
            onEmailChange = {},
            onPasswordChange = {},
            onSubmit = {},
            onGoToRegister = {},
        )
    }
}

@Preview
@Composable
private fun LoginScreenContentErrorPreview() {
    MaterialTheme {
        LoginScreenContent(
            state =
                LoginUiState(
                    email = "v@example.com",
                    password = "secret",
                    error = AppError.Unauthorized,
                ),
            onEmailChange = {},
            onPasswordChange = {},
            onSubmit = {},
            onGoToRegister = {},
        )
    }
}
