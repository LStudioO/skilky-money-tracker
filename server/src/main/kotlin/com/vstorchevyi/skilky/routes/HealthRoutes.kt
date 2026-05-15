package com.vstorchevyi.skilky.routes

import com.vstorchevyi.skilky.api.ApiRoutes
import com.vstorchevyi.skilky.api.HealthResponse
import com.vstorchevyi.skilky.config.AppConfig
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

fun Route.healthRoutes(appConfig: AppConfig) {
    get(ApiRoutes.HEALTH) {
        call.respond(
            HealthResponse(
                status = "ok",
                version = appConfig.api.version,
            ),
        )
    }
}
