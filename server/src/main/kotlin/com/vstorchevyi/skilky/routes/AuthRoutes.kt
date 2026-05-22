package com.vstorchevyi.skilky.routes

import com.vstorchevyi.skilky.api.ApiRoutes
import com.vstorchevyi.skilky.api.AuthResponse
import com.vstorchevyi.skilky.api.LoginRequest
import com.vstorchevyi.skilky.api.RefreshRequest
import com.vstorchevyi.skilky.api.RegisterRequest
import com.vstorchevyi.skilky.config.AppConfig
import com.vstorchevyi.skilky.errors.ConflictException
import com.vstorchevyi.skilky.errors.UnauthorizedException
import com.vstorchevyi.skilky.plugins.AuthRateLimit
import com.vstorchevyi.skilky.repository.RefreshTokenRepository
import com.vstorchevyi.skilky.repository.UserRepository
import com.vstorchevyi.skilky.security.JwtTokenProvider
import com.vstorchevyi.skilky.security.PasswordHasher
import com.vstorchevyi.skilky.security.validateRegisterRequest
import io.ktor.http.HttpStatusCode
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import org.koin.ktor.ext.inject

fun Route.authRoutes() {
    val jwtConfig: AppConfig.JwtConfig by inject()
    val userRepository: UserRepository by inject()
    val refreshTokenRepository: RefreshTokenRepository by inject()
    val passwordHasher: PasswordHasher by inject()
    val tokenProvider: JwtTokenProvider by inject()
    val deps = AuthDeps(jwtConfig, userRepository, refreshTokenRepository, passwordHasher, tokenProvider)
    // Unauthenticated endpoints, so the limiter keys on client IP; caps
    // password brute-force and signup spam. See AuthRateLimit.
    rateLimit(AuthRateLimit) {
        authRegisterRoute(deps)
        authLoginRoute(deps)
        authRefreshRoute(deps)
    }
}

private fun Route.authRegisterRoute(deps: AuthDeps) {
    post(ApiRoutes.Auth.REGISTER) {
        val req = call.receive<RegisterRequest>()
        validateRegisterRequest(req)

        if (deps.userRepository.findByEmail(req.email) != null) {
            throw ConflictException("Email already registered")
        }

        val user =
            deps.userRepository.create(
                email = req.email,
                passwordHash = deps.passwordHasher.hash(req.password),
                displayName = req.displayName.trim(),
            )

        val accessToken = deps.tokenProvider.createAccessToken(user.id, user.email)
        val refreshToken =
            deps.refreshTokenRepository.create(
                userId = user.id,
                ttlDays = deps.jwtConfig.refreshTokenExpirationDays,
            )

        call.respond(
            HttpStatusCode.Created,
            AuthResponse(token = accessToken, refreshToken = refreshToken, user = user.toDto()),
        )
    }
}

private fun Route.authLoginRoute(deps: AuthDeps) {
    post(ApiRoutes.Auth.LOGIN) {
        val req = call.receive<LoginRequest>()
        val user = deps.userRepository.findByEmail(req.email) ?: throw UnauthorizedException()
        if (!deps.passwordHasher.verify(req.password, user.passwordHash)) {
            throw UnauthorizedException()
        }

        val accessToken = deps.tokenProvider.createAccessToken(user.id, user.email)
        val refreshToken =
            deps.refreshTokenRepository.create(
                userId = user.id,
                ttlDays = deps.jwtConfig.refreshTokenExpirationDays,
            )

        call.respond(
            AuthResponse(token = accessToken, refreshToken = refreshToken, user = user.toDto()),
        )
    }
}

private fun Route.authRefreshRoute(deps: AuthDeps) {
    post(ApiRoutes.Auth.REFRESH) {
        val req = call.receive<RefreshRequest>()
        val record =
            deps.refreshTokenRepository.findValid(req.refreshToken)
                ?: throw UnauthorizedException("Invalid or expired refresh token")
        val user =
            deps.userRepository.findById(record.userId)
                ?: throw UnauthorizedException("User no longer exists")

        deps.refreshTokenRepository.delete(record.id)

        val accessToken = deps.tokenProvider.createAccessToken(user.id, user.email)
        val refreshToken =
            deps.refreshTokenRepository.create(
                userId = user.id,
                ttlDays = deps.jwtConfig.refreshTokenExpirationDays,
            )

        call.respond(
            AuthResponse(token = accessToken, refreshToken = refreshToken, user = user.toDto()),
        )
    }
}

private data class AuthDeps(
    val jwtConfig: AppConfig.JwtConfig,
    val userRepository: UserRepository,
    val refreshTokenRepository: RefreshTokenRepository,
    val passwordHasher: PasswordHasher,
    val tokenProvider: JwtTokenProvider,
)
