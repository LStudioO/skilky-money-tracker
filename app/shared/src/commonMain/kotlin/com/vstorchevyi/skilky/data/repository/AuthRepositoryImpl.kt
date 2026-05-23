package com.vstorchevyi.skilky.data.repository

import com.vstorchevyi.skilky.api.AuthResponse
import com.vstorchevyi.skilky.api.LoginRequest
import com.vstorchevyi.skilky.api.RegisterRequest
import com.vstorchevyi.skilky.data.local.TokenStorage
import com.vstorchevyi.skilky.data.mapper.toDomain
import com.vstorchevyi.skilky.data.remote.AuthApi
import com.vstorchevyi.skilky.domain.model.AppError
import com.vstorchevyi.skilky.domain.model.AuthSession
import com.vstorchevyi.skilky.domain.model.Either
import com.vstorchevyi.skilky.domain.repository.AuthRepository
import io.ktor.client.plugins.ResponseException
import io.ktor.http.HttpStatusCode
import kotlin.coroutines.cancellation.CancellationException

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

    override suspend fun currentSession(): AuthSession? = tokenStorage.read()

    override suspend fun logout() {
        tokenStorage.clear()
    }

    private suspend fun authenticate(call: suspend () -> AuthResponse): Either<AppError, AuthSession> =
        try {
            val session = call().toDomain()
            tokenStorage.save(session)
            Either.Right(session)
        } catch (e: ResponseException) {
            Either.Left(e.response.status.toAppError())
        } catch (e: CancellationException) {
            throw e
        } catch (
            @Suppress("TooGenericExceptionCaught", "SwallowedException") e: Exception,
        ) {
            // Transport failures (no connection, DNS, timeout) have no shared
            // supertype across platforms, so the catch-all maps them to Network.
            Either.Left(AppError.Network)
        }
}

private fun HttpStatusCode.toAppError(): AppError =
    when {
        this == HttpStatusCode.Unauthorized -> AppError.Unauthorized
        this == HttpStatusCode.Conflict -> AppError.Conflict
        this == HttpStatusCode.UnprocessableEntity -> AppError.Validation
        value >= HTTP_SERVER_ERROR -> AppError.Network
        else -> AppError.Unknown
    }

private const val HTTP_SERVER_ERROR = 500
