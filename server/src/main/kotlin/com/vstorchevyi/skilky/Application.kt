package com.vstorchevyi.skilky

import com.vstorchevyi.skilky.ai.warnIfLowMemory
import com.vstorchevyi.skilky.config.AppConfig
import com.vstorchevyi.skilky.di.appModules
import com.vstorchevyi.skilky.di.testKoinModules
import com.vstorchevyi.skilky.plugins.configureCallId
import com.vstorchevyi.skilky.plugins.configureCallLogging
import com.vstorchevyi.skilky.plugins.configureCors
import com.vstorchevyi.skilky.plugins.configureJwtAuthentication
import com.vstorchevyi.skilky.plugins.configureRateLimit
import com.vstorchevyi.skilky.plugins.configureRouting
import com.vstorchevyi.skilky.plugins.configureSerialization
import com.vstorchevyi.skilky.plugins.configureStatusPages
import io.ktor.server.application.Application
import io.ktor.server.application.install
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger

/**
 * Application entry point.
 *
 * Plugin install order matters: each plugin attaches to the request
 * pipeline in the order installed.
 *  1. Koin first, so subsequent plugins and routes can inject services.
 *  2. CallId before CallLogging, so log lines carry the correlation ID.
 *  3. StatusPages before Routing, so route exceptions bubble in.
 *
 * Service construction lives in Koin modules under [com.vstorchevyi.skilky.di].
 * Singletons that own native resources (DB pool, Ollama HTTP client) declare
 * `onClose { it?.close() }` on their bindings, so Koin's container shutdown
 * (tied to Ktor's `ApplicationStopped` event by the koin-ktor plugin) closes
 * them automatically — no separate shutdown subscribers needed here.
 *
 * Tests may layer overrides on top by putting a list of modules under
 * [com.vstorchevyi.skilky.di.TestKoinModulesKey] in `application { attributes }`
 * before this module runs. Overrides allowed via `allowOverride(true)` on
 * the koin instance, so test definitions win for the same type.
 *
 * Conditional services. `appModules` omits the persistence and AI modules
 * when the corresponding config block is absent, so DI-aware routes can
 * inspect whether the type is registered and 404 when it isn't.
 */
fun Application.module() {
    val appConfig = AppConfig.from(environment.config)
    warnIfLowMemory(appConfig.ai)

    install(Koin) {
        slf4jLogger()
        allowOverride(true)
        modules(appModules(appConfig) + testKoinModules())
    }

    configureCallId()
    configureCallLogging()
    configureSerialization()
    configureStatusPages()
    configureJwtAuthentication()
    configureRateLimit()
    configureCors()
    configureRouting()
}
