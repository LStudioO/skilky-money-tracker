package com.vstorchevyi.skilky

import com.vstorchevyi.skilky.ai.CachedCategoryLoader
import com.vstorchevyi.skilky.ai.CategoryHint
import com.vstorchevyi.skilky.ai.OllamaClient
import com.vstorchevyi.skilky.ai.TextParsingService
import com.vstorchevyi.skilky.ai.parseServiceOverride
import com.vstorchevyi.skilky.ai.warnIfLowMemory
import com.vstorchevyi.skilky.config.AppConfig
import com.vstorchevyi.skilky.db.DatabaseFactory
import com.vstorchevyi.skilky.plugins.configureCallId
import com.vstorchevyi.skilky.plugins.configureCallLogging
import com.vstorchevyi.skilky.plugins.configureCors
import com.vstorchevyi.skilky.plugins.configureJwtAuthentication
import com.vstorchevyi.skilky.plugins.configureRateLimit
import com.vstorchevyi.skilky.plugins.configureRouting
import com.vstorchevyi.skilky.plugins.configureSerialization
import com.vstorchevyi.skilky.plugins.configureStatusPages
import com.vstorchevyi.skilky.repository.CategoryRepository
import com.vstorchevyi.skilky.repository.ExpenseRepository
import com.vstorchevyi.skilky.repository.ParseCorrectionsRepository
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
    warnIfLowMemory(appConfig.ai)

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
    val parseCorrectionsRepository = databaseFactory?.let { ParseCorrectionsRepository(it) }
    val textParsingService =
        parseServiceOverride() ?: buildTextParsingService(appConfig.ai, categoryRepository)

    configureCallId()
    configureCallLogging()
    configureSerialization()
    configureStatusPages()
    configureJwtAuthentication(tokenProvider)
    configureRateLimit()
    configureCors(appConfig)
    configureRouting(
        appConfig = appConfig,
        databaseFactory = databaseFactory,
        userRepository = userRepository,
        refreshTokenRepository = refreshTokenRepository,
        categoryRepository = categoryRepository,
        expenseRepository = expenseRepository,
        parseCorrectionsRepository = parseCorrectionsRepository,
        textParsingService = textParsingService,
        passwordHasher = passwordHasher,
        tokenProvider = tokenProvider,
    )
}

/**
 * Builds the real Ollama-backed parser when the operator has supplied
 * both an AI block and a database (the second is required because the
 * service needs the user's category list to build a useful prompt).
 * Returns null when either piece is missing; the parse route then 404s.
 */
private fun Application.buildTextParsingService(
    aiConfig: AppConfig.AiConfig?,
    categoryRepository: CategoryRepository?,
): TextParsingService? {
    if (aiConfig == null || categoryRepository == null) return null
    val client = OllamaClient(aiConfig)
    monitor.subscribe(ApplicationStopped) { client.close() }
    val cachedLoader =
        CachedCategoryLoader(
            source = { userId ->
                categoryRepository.listVisible(userId).map { CategoryHint(id = it.id, name = it.name) }
            },
        )
    return TextParsingService(
        ollamaClient = client,
        loadCategories = cachedLoader,
    )
}
