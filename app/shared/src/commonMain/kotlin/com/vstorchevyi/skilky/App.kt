package com.vstorchevyi.skilky

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.vstorchevyi.skilky.data.remote.SessionEvents
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
        val getCurrentSession = koinInject<GetCurrentSessionUseCase>()
        val sessionEvents = koinInject<SessionEvents>()
        val navController = rememberNavController()

        var startDestination by remember { mutableStateOf<Route?>(null) }

        LaunchedEffect(Unit) {
            startDestination =
                if (getCurrentSession() != null) Route.Home else Route.Login
        }

        LaunchedEffect(Unit) {
            sessionEvents.signedOut.collect {
                navController.navigate(Route.Login) {
                    popUpTo(navController.graph.id) { inclusive = true }
                    launchSingleTop = true
                }
            }
        }

        val resolvedStart = startDestination
        if (resolvedStart == null) {
            SplashScreen()
        } else {
            SkilkyNavHost(startDestination = resolvedStart, navController = navController)
        }
    }
}

@Composable
private fun SplashScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}
