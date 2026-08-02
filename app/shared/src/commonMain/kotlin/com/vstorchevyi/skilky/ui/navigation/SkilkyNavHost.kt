package com.vstorchevyi.skilky.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.vstorchevyi.skilky.ui.auth.LoginScreen
import com.vstorchevyi.skilky.ui.auth.RegisterScreen
import com.vstorchevyi.skilky.ui.categories.CategoriesScreen
import com.vstorchevyi.skilky.ui.expense.ExpenseFormScreen
import com.vstorchevyi.skilky.ui.home.HomeScreen

/**
 * Builds the root nav graph. [startDestination] is decided by the caller from
 * the persisted-session check at app start: [Route.Home] when a session is
 * present, [Route.Login] otherwise.
 *
 * Sign-in and sign-up both replace the auth stack rather than stacking on top
 * of it, so the user cannot back-stack into the login screen after signing in.
 */
@Composable
fun SkilkyNavHost(
    startDestination: Route,
    navController: NavHostController = rememberNavController(),
) {
    NavHost(navController = navController, startDestination = startDestination) {
        loginDestination(navController)
        registerDestination(navController)
        homeDestination(navController)
        categoriesDestination(navController)
        newExpenseDestination(navController)
        editExpenseDestination(navController)
    }
}

private fun NavGraphBuilder.loginDestination(navController: NavHostController) {
    composable<Route.Login> {
        LoginScreen(
            onSignedIn = {
                navController.navigate(Route.Home) {
                    popUpTo<Route.Login> { inclusive = true }
                    launchSingleTop = true
                }
            },
            onGoToRegister = {
                navController.navigate(Route.Register) {
                    launchSingleTop = true
                }
            },
        )
    }
}

private fun NavGraphBuilder.registerDestination(navController: NavHostController) {
    composable<Route.Register> {
        RegisterScreen(
            onRegistered = {
                navController.navigate(Route.Home) {
                    popUpTo<Route.Login> { inclusive = true }
                    launchSingleTop = true
                }
            },
            onGoToLogin = { navController.popBackStack() },
        )
    }
}

private fun NavGraphBuilder.homeDestination(navController: NavHostController) {
    composable<Route.Home> {
        HomeScreen(
            onSignedOut = {
                navController.navigate(Route.Login) {
                    popUpTo<Route.Home> { inclusive = true }
                    launchSingleTop = true
                }
            },
            onOpenCategories = {
                navController.navigate(Route.Categories) {
                    launchSingleTop = true
                }
            },
            onAddExpense = {
                navController.navigate(Route.NewExpense) {
                    launchSingleTop = true
                }
            },
            onOpenExpense = { id ->
                navController.navigate(Route.EditExpense(id)) {
                    launchSingleTop = true
                }
            },
        )
    }
}

private fun NavGraphBuilder.categoriesDestination(navController: NavHostController) {
    composable<Route.Categories> {
        CategoriesScreen(onBack = { navController.popBackStack() })
    }
}

private fun NavGraphBuilder.newExpenseDestination(navController: NavHostController) {
    composable<Route.NewExpense> {
        ExpenseFormScreen(
            expenseId = null,
            onClose = { navController.popBackStack() },
        )
    }
}

private fun NavGraphBuilder.editExpenseDestination(navController: NavHostController) {
    composable<Route.EditExpense> { entry ->
        val args = entry.toRoute<Route.EditExpense>()
        ExpenseFormScreen(
            expenseId = args.id,
            onClose = { navController.popBackStack() },
        )
    }
}
