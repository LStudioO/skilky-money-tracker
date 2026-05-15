package com.vstorchevyi.skilky

import com.vstorchevyi.skilky.config.AppConfig
import com.vstorchevyi.skilky.plugins.configureCallLogging
import com.vstorchevyi.skilky.plugins.configureCors
import com.vstorchevyi.skilky.plugins.configureRouting
import com.vstorchevyi.skilky.plugins.configureSerialization
import com.vstorchevyi.skilky.plugins.configureStatusPages
import io.ktor.server.application.Application

fun Application.module() {
    val appConfig = AppConfig.from(environment.config)

    configureCallLogging()
    configureSerialization()
    configureStatusPages()
    configureCors(appConfig)
    configureRouting(appConfig)
}
