package com.vstorchevyi.skilky

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.rememberNavController
import com.vstorchevyi.skilky.data.remote.SessionEvents
import com.vstorchevyi.skilky.data.sync.ExpenseSyncManager
import com.vstorchevyi.skilky.domain.model.AppError
import com.vstorchevyi.skilky.domain.model.Either
import com.vstorchevyi.skilky.domain.usecase.GetCurrentSessionUseCase
import com.vstorchevyi.skilky.ui.navigation.Route
import com.vstorchevyi.skilky.ui.navigation.SkilkyNavHost
import org.koin.compose.koinInject

/**
 * Root composable. Picks the start destination from the persisted session,
 * mounts [SkilkyNavHost], and listens for mid-session sign-out signals so the
 * nav stack can bounce back to login when the server has invalidated us.
 */
@Composable
fun App() {
    MaterialTheme {
        koinInject<ExpenseSyncManager>()
        val getCurrentSession = koinInject<GetCurrentSessionUseCase>()
        val sessionEvents = koinInject<SessionEvents>()
        val navController = rememberNavController()
        val snackbarHostState = remember { SnackbarHostState() }

        var startDestination by remember { mutableStateOf<Route?>(null) }
        var startupError by remember { mutableStateOf<AppError?>(null) }
        var retryCount by remember { mutableStateOf(0) }

        LaunchedEffect(retryCount) {
            when (val result = getCurrentSession()) {
                is Either.Left -> {
                    startupError = result.value
                }

                is Either.Right -> {
                    startupError = null
                    startDestination = if (result.value != null) Route.Home else Route.Login
                }
            }
        }

        LaunchedEffect(Unit) {
            sessionEvents.signedOut.collect {
                navController.navigate(Route.Login) {
                    popUpTo(navController.graph.id) { inclusive = true }
                    launchSingleTop = true
                }
            }
        }

        LaunchedEffect(Unit) {
            sessionEvents.storageFailures.collect {
                snackbarHostState.showSnackbar(STORAGE_ERROR_MESSAGE)
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            val resolvedStart = startDestination
            when {
                startupError != null -> {
                    StartupErrorScreen {
                        startupError = null
                        retryCount += 1
                    }
                }

                resolvedStart == null -> {
                    SplashScreen()
                }

                else -> {
                    SkilkyNavHost(startDestination = resolvedStart, navController = navController)
                }
            }
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

@Composable
private fun SplashScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun StartupErrorScreen(onRetry: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(STORAGE_ERROR_MESSAGE, style = MaterialTheme.typography.bodyLarge)
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onRetry) {
                Text("Retry")
            }
        }
    }
}

private const val STORAGE_ERROR_MESSAGE = "Local data couldn't be read. Restart the app or try again."
