package com.vstorchevyi.skilky.ui.navigation

import kotlinx.serialization.Serializable

/**
 * Type-safe destinations for the nav graph. Each entry is a `@Serializable`
 * data object so the Compose navigation library can encode it into a route
 * key without us writing string paths by hand.
 */
@Serializable
sealed interface Route {
    @Serializable
    data object Login : Route

    @Serializable
    data object Register : Route

    @Serializable
    data object Home : Route

    @Serializable
    data object Categories : Route

    @Serializable
    data object NewExpense : Route

    @Serializable
    data class EditExpense(
        val id: Long,
    ) : Route
}
