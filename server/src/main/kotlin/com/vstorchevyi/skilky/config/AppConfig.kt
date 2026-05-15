package com.vstorchevyi.skilky.config

import io.ktor.server.config.ApplicationConfig

data class AppConfig(
    val api: ApiConfig,
    val cors: CorsConfig,
) {
    data class ApiConfig(val version: String)

    data class CorsConfig(val allowedHosts: List<String>)

    companion object {
        fun from(config: ApplicationConfig): AppConfig =
            AppConfig(
                api = ApiConfig(
                    version = config.property("skilky.api.version").getString(),
                ),
                cors = CorsConfig(
                    allowedHosts = config
                        .property("skilky.cors.allowedHosts")
                        .getString()
                        .split(",")
                        .map(String::trim)
                        .filter(String::isNotEmpty),
                ),
            )
    }
}
