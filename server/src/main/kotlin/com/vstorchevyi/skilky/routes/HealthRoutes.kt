package com.vstorchevyi.skilky.routes

import com.vstorchevyi.skilky.api.ApiRoutes
import com.vstorchevyi.skilky.api.HealthResponse
import com.vstorchevyi.skilky.config.AppConfig
import com.vstorchevyi.skilky.db.DatabaseFactory
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import org.koin.ktor.ext.getKoin
import org.koin.ktor.ext.inject

fun Route.healthRoutes() {
    val apiConfig: AppConfig.ApiConfig by inject()
    val apiVersion = apiConfig.version
    val databaseFactory = getKoin().getOrNull<DatabaseFactory>()

    get(ApiRoutes.HEALTH) {
        call.respond(
            HealthResponse(
                status = "ok",
                version = apiVersion,
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
                    version = apiVersion,
                    db = if (connected) "connected" else "disconnected",
                ),
            )
        }
    }
}
