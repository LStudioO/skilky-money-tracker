package com.vstorchevyi.skilky.plugins

import com.vstorchevyi.skilky.config.AppConfig
import com.vstorchevyi.skilky.db.DatabaseFactory
import com.vstorchevyi.skilky.repository.RefreshTokenRepository
import com.vstorchevyi.skilky.repository.UserRepository
import com.vstorchevyi.skilky.routes.authRoutes
import com.vstorchevyi.skilky.routes.healthRoutes
import com.vstorchevyi.skilky.security.JwtTokenProvider
import com.vstorchevyi.skilky.security.PasswordHasher
import io.ktor.server.application.Application
import io.ktor.server.routing.routing

fun Application.configureRouting(
    appConfig: AppConfig,
    databaseFactory: DatabaseFactory?,
    userRepository: UserRepository?,
    refreshTokenRepository: RefreshTokenRepository?,
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
    }
}
