package com.vstorchevyi.skilky

import com.vstorchevyi.skilky.config.AppConfig
import com.vstorchevyi.skilky.db.DatabaseFactory
import com.vstorchevyi.skilky.plugins.configureCallId
import com.vstorchevyi.skilky.plugins.configureCallLogging
import com.vstorchevyi.skilky.plugins.configureCors
import com.vstorchevyi.skilky.plugins.configureJwtAuthentication
import com.vstorchevyi.skilky.plugins.configureRouting
import com.vstorchevyi.skilky.plugins.configureSerialization
import com.vstorchevyi.skilky.plugins.configureStatusPages
import com.vstorchevyi.skilky.repository.CategoryRepository
import com.vstorchevyi.skilky.repository.ExpenseRepository
import com.vstorchevyi.skilky.repository.RefreshTokenRepository
import com.vstorchevyi.skilky.repository.UserRepository
import com.vstorchevyi.skilky.security.JwtTokenProvider
import com.vstorchevyi.skilky.security.PasswordHasher
import com.vstorchevyi.skilky.security.TokenHasher
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped

/**
 * Application entry point.
 *
 * Plugin install order matters: each plugin attaches to the request
 * pipeline in the order installed.
 *  1. CallId must run first so every subsequent plugin (logging, status
 *     pages, route code) can read the correlation ID.
 *  2. StatusPages must come BEFORE Routing so route exceptions bubble in.
 *
 * DB wiring is conditional on [AppConfig.database] being present. Tests
 * omit that block and the entire DB and auth surface is silently skipped,
 * which lets unit tests run without Postgres.
 */
fun Application.module() {
    val appConfig = AppConfig.from(environment.config)

    val databaseFactory =
        appConfig.database?.let { dbConfig ->
            DatabaseFactory(dbConfig).also { factory ->
                factory.init()
                // Close the pool on graceful shutdown.
                monitor.subscribe(ApplicationStopped) { factory.close() }
            }
        }

    val passwordHasher = PasswordHasher()
    val tokenProvider = JwtTokenProvider(appConfig.jwt)
    val tokenHasher = TokenHasher(appConfig.security.refreshTokenPepper)
    val userRepository = databaseFactory?.let { UserRepository(it) }
    val refreshTokenRepository = databaseFactory?.let { RefreshTokenRepository(it, tokenHasher) }
    val categoryRepository = databaseFactory?.let { CategoryRepository(it) }
    val expenseRepository = databaseFactory?.let { ExpenseRepository(it) }

    configureCallId()
    configureCallLogging()
    configureSerialization()
    configureStatusPages()
    configureJwtAuthentication(tokenProvider)
    configureCors(appConfig)
    configureRouting(
        appConfig = appConfig,
        databaseFactory = databaseFactory,
        userRepository = userRepository,
        refreshTokenRepository = refreshTokenRepository,
        categoryRepository = categoryRepository,
        expenseRepository = expenseRepository,
        passwordHasher = passwordHasher,
        tokenProvider = tokenProvider,
    )
}
