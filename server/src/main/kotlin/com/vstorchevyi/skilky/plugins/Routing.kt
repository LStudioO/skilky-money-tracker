package com.vstorchevyi.skilky.plugins

import com.vstorchevyi.skilky.config.AppConfig
import com.vstorchevyi.skilky.routes.healthRoutes
import io.ktor.server.application.Application
import io.ktor.server.routing.routing

fun Application.configureRouting(appConfig: AppConfig) {
    routing {
        healthRoutes(appConfig)
    }
}
