package com.vstorchevyi.skilky.routes

import com.vstorchevyi.skilky.api.ApiRoutes
import com.vstorchevyi.skilky.api.AuthResponse
import com.vstorchevyi.skilky.api.LoginRequest
import com.vstorchevyi.skilky.api.RefreshRequest
import com.vstorchevyi.skilky.api.RegisterRequest
import com.vstorchevyi.skilky.config.AppConfig
import com.vstorchevyi.skilky.errors.ConflictException
import com.vstorchevyi.skilky.errors.UnauthorizedException
import com.vstorchevyi.skilky.repository.RefreshTokenRepository
import com.vstorchevyi.skilky.repository.UserRepository
import com.vstorchevyi.skilky.security.JwtTokenProvider
import com.vstorchevyi.skilky.security.PasswordHasher
import com.vstorchevyi.skilky.security.validateRegisterRequest
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post

fun Route.authRoutes(
    jwtConfig: AppConfig.JwtConfig,
    userRepository: UserRepository,
    refreshTokenRepository: RefreshTokenRepository,
    passwordHasher: PasswordHasher,
    tokenProvider: JwtTokenProvider,
) {
    post(ApiRoutes.Auth.REGISTER) {
        val req = call.receive<RegisterRequest>()
        validateRegisterRequest(req)

        if (userRepository.findByEmail(req.email) != null) {
            throw ConflictException("Email already registered")
        }

        val user =
            userRepository.create(
                email = req.email,
                passwordHash = passwordHasher.hash(req.password),
                displayName = req.displayName.trim(),
            )

        val accessToken = tokenProvider.createAccessToken(user.id, user.email)
        val refreshToken =
            refreshTokenRepository.create(
                userId = user.id,
                ttlDays = jwtConfig.refreshTokenExpirationDays,
            )

        call.respond(
            HttpStatusCode.Created,
            AuthResponse(token = accessToken, refreshToken = refreshToken, user = user.toDto()),
        )
    }

    post(ApiRoutes.Auth.LOGIN) {
        val req = call.receive<LoginRequest>()
        val user = userRepository.findByEmail(req.email) ?: throw UnauthorizedException()
        if (!passwordHasher.verify(req.password, user.passwordHash)) {
            throw UnauthorizedException()
        }

        val accessToken = tokenProvider.createAccessToken(user.id, user.email)
        val refreshToken =
            refreshTokenRepository.create(
                userId = user.id,
                ttlDays = jwtConfig.refreshTokenExpirationDays,
            )

        call.respond(
            AuthResponse(token = accessToken, refreshToken = refreshToken, user = user.toDto()),
        )
    }

    post(ApiRoutes.Auth.REFRESH) {
        val req = call.receive<RefreshRequest>()
        val record =
            refreshTokenRepository.findValid(req.refreshToken)
                ?: throw UnauthorizedException("Invalid or expired refresh token")
        val user =
            userRepository.findById(record.userId)
                ?: throw UnauthorizedException("User no longer exists")

        refreshTokenRepository.delete(record.id)

        val accessToken = tokenProvider.createAccessToken(user.id, user.email)
        val refreshToken =
            refreshTokenRepository.create(
                userId = user.id,
                ttlDays = jwtConfig.refreshTokenExpirationDays,
            )

        call.respond(
            AuthResponse(token = accessToken, refreshToken = refreshToken, user = user.toDto()),
        )
    }
}
