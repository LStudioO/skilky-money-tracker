package com.vstorchevyi.skilky.plugins

import com.vstorchevyi.skilky.config.AppConfig
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.cors.routing.CORS

fun Application.configureCors(appConfig: AppConfig) {
    install(CORS) {
        if (appConfig.cors.allowedHosts.contains("*")) {
            anyHost()
        } else {
            appConfig.cors.allowedHosts.forEach { host -> allowHost(host) }
        }

        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Patch)
        allowMethod(HttpMethod.Delete)

        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
    }
}
