package com.vstorchevyi.skilky.data.repository

import com.vstorchevyi.skilky.api.AuthResponse
import com.vstorchevyi.skilky.api.LoginRequest
import com.vstorchevyi.skilky.api.RegisterRequest
import com.vstorchevyi.skilky.data.local.TokenStorage
import com.vstorchevyi.skilky.data.local.runCatchingStorage
import com.vstorchevyi.skilky.data.mapper.toDomain
import com.vstorchevyi.skilky.data.remote.AuthApi
import com.vstorchevyi.skilky.domain.model.AppError
import com.vstorchevyi.skilky.domain.model.AuthSession
import com.vstorchevyi.skilky.domain.model.Either
import com.vstorchevyi.skilky.domain.model.flatMap
import com.vstorchevyi.skilky.domain.model.map
import com.vstorchevyi.skilky.domain.repository.AuthRepository

/**
 * Calls [AuthApi], persists the resulting session through [TokenStorage], and
 * collapses every failure into an [AppError] so the domain stays transport-agnostic.
 */
internal class AuthRepositoryImpl(
    private val authApi: AuthApi,
    private val tokenStorage: TokenStorage,
) : AuthRepository {
    override suspend fun register(
        email: String,
        password: String,
        displayName: String,
    ): Either<AppError, AuthSession> = authenticate { authApi.register(RegisterRequest(email, password, displayName)) }

    override suspend fun login(
        email: String,
        password: String,
    ): Either<AppError, AuthSession> = authenticate { authApi.login(LoginRequest(email, password)) }

    override suspend fun currentSession(): Either<AppError, AuthSession?> = runCatchingStorage { tokenStorage.read() }

    override suspend fun logout(): Either<AppError, Unit> = runCatchingStorage { tokenStorage.clear() }

    private suspend fun authenticate(call: suspend () -> AuthResponse): Either<AppError, AuthSession> =
        runCatchingApi { call().toDomain() }
            .flatMap { session ->
                runCatchingStorage { tokenStorage.save(session) }
                    .map { session }
            }
}
