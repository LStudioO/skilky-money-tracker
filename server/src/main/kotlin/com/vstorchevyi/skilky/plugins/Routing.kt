package com.vstorchevyi.skilky.plugins

import com.vstorchevyi.skilky.ai.TextParsingService
import com.vstorchevyi.skilky.repository.UserRepository
import com.vstorchevyi.skilky.routes.authRoutes
import com.vstorchevyi.skilky.routes.categoryRoutes
import com.vstorchevyi.skilky.routes.expenseRoutes
import com.vstorchevyi.skilky.routes.healthRoutes
import com.vstorchevyi.skilky.routes.parseCorrectionsRoutes
import com.vstorchevyi.skilky.routes.parseRoutes
import io.ktor.server.application.Application
import io.ktor.server.routing.routing
import org.koin.ktor.ext.getKoin

/**
 * Registers route trees. Each conditional group checks the Koin
 * container for the relevant service before registering, so tests can
 * unlock a route by injecting a fake service via
 * [com.vstorchevyi.skilky.di.TestKoinModulesKey] even when the
 * corresponding config block (`skilky.database.*`, `skilky.ai.*`) is
 * absent.
 */
fun Application.configureRouting() {
    val koin = getKoin()
    routing {
        healthRoutes()
        if (koin.getOrNull<UserRepository>() != null) {
            authRoutes()
            categoryRoutes()
            expenseRoutes()
            parseCorrectionsRoutes()
        }
        if (koin.getOrNull<TextParsingService>() != null) {
            parseRoutes()
        }
    }
}
