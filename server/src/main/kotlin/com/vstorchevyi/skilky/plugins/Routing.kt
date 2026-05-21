package com.vstorchevyi.skilky.plugins

import com.vstorchevyi.skilky.ai.TextParsingService
import com.vstorchevyi.skilky.config.AppConfig
import com.vstorchevyi.skilky.db.DatabaseFactory
import com.vstorchevyi.skilky.repository.CategoryRepository
import com.vstorchevyi.skilky.repository.ExpenseRepository
import com.vstorchevyi.skilky.repository.ParseCorrectionsRepository
import com.vstorchevyi.skilky.repository.RefreshTokenRepository
import com.vstorchevyi.skilky.repository.UserRepository
import com.vstorchevyi.skilky.routes.authRoutes
import com.vstorchevyi.skilky.routes.categoryRoutes
import com.vstorchevyi.skilky.routes.expenseRoutes
import com.vstorchevyi.skilky.routes.healthRoutes
import com.vstorchevyi.skilky.routes.parseCorrectionsRoutes
import com.vstorchevyi.skilky.routes.parseRoutes
import com.vstorchevyi.skilky.security.JwtTokenProvider
import com.vstorchevyi.skilky.security.PasswordHasher
import io.ktor.server.application.Application
import io.ktor.server.routing.routing

@Suppress("LongParameterList")
fun Application.configureRouting(
    appConfig: AppConfig,
    databaseFactory: DatabaseFactory?,
    userRepository: UserRepository?,
    refreshTokenRepository: RefreshTokenRepository?,
    categoryRepository: CategoryRepository?,
    expenseRepository: ExpenseRepository?,
    parseCorrectionsRepository: ParseCorrectionsRepository?,
    textParsingService: TextParsingService?,
    passwordHasher: PasswordHasher,
    tokenProvider: JwtTokenProvider,
) {
    routing {
        healthRoutes(appConfig, databaseFactory)
        if (userRepository != null && refreshTokenRepository != null) {
            authRoutes(
                jwtConfig = appConfig.jwt,
                userRepository = userRepository,
                refreshTokenRepository = refreshTokenRepository,
                passwordHasher = passwordHasher,
                tokenProvider = tokenProvider,
            )
        }
        if (categoryRepository != null) {
            categoryRoutes(categoryRepository)
        }
        if (expenseRepository != null) {
            expenseRoutes(expenseRepository)
        }
        if (textParsingService != null) {
            parseRoutes(textParsingService)
        }
        if (parseCorrectionsRepository != null) {
            parseCorrectionsRoutes(parseCorrectionsRepository)
        }
    }
}
