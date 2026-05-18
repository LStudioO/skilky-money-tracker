package com.vstorchevyi.skilky.routes

import com.vstorchevyi.skilky.api.ApiRoutes
import com.vstorchevyi.skilky.api.HealthResponse
import com.vstorchevyi.skilky.config.AppConfig
import com.vstorchevyi.skilky.db.DatabaseFactory
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

fun Route.healthRoutes(
    appConfig: AppConfig,
    databaseFactory: DatabaseFactory?,
) {
    get(ApiRoutes.HEALTH) {
        call.respond(
            HealthResponse(
                status = "ok",
                version = appConfig.api.version,
            ),
        )
    }

    if (databaseFactory != null) {
        get(ApiRoutes.HEALTH_DB) {
            val connected = databaseFactory.ping()
            val httpStatus = if (connected) HttpStatusCode.OK else HttpStatusCode.ServiceUnavailable
            call.respond(
                httpStatus,
                HealthResponse(
                    status = if (connected) "ok" else "degraded",
                    version = appConfig.api.version,
                    db = if (connected) "connected" else "disconnected",
                ),
            )
        }
    }
}
