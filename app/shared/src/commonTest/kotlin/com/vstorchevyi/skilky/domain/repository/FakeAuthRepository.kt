package com.vstorchevyi.skilky.domain.repository

import com.vstorchevyi.skilky.domain.model.AppError
import com.vstorchevyi.skilky.domain.model.AuthSession
import com.vstorchevyi.skilky.domain.model.Either
import com.vstorchevyi.skilky.domain.model.User

/**
 * Test double for [AuthRepository]. Each entry point returns whatever the
 * test queued up; calls are recorded on [calls] so behavior tests can assert
 * that the use case forwarded the arguments as expected.
 */
class FakeAuthRepository(
    private var registerResult: Either<AppError, AuthSession> = Either.Right(defaultSession()),
    private var loginResult: Either<AppError, AuthSession> = Either.Right(defaultSession()),
    private var session: AuthSession? = null,
) : AuthRepository {
    val calls: MutableList<Call> = mutableListOf()

    override suspend fun register(
        email: String,
        password: String,
        displayName: String,
    ): Either<AppError, AuthSession> {
        calls += Call.Register(email, password, displayName)
        return registerResult
    }

    override suspend fun login(
        email: String,
        password: String,
    ): Either<AppError, AuthSession> {
        calls += Call.Login(email, password)
        return loginResult
    }

    override suspend fun currentSession(): AuthSession? {
        calls += Call.CurrentSession
        return session
    }

    override suspend fun logout() {
        calls += Call.Logout
        session = null
    }

    fun queueLoginResult(result: Either<AppError, AuthSession>) {
        loginResult = result
    }

    fun queueRegisterResult(result: Either<AppError, AuthSession>) {
        registerResult = result
    }

    fun setSession(value: AuthSession?) {
        session = value
    }

    sealed interface Call {
        data class Register(
            val email: String,
            val password: String,
            val displayName: String,
        ) : Call

        data class Login(
            val email: String,
            val password: String,
        ) : Call

        data object CurrentSession : Call

        data object Logout : Call
    }

    companion object {
        fun defaultSession(): AuthSession =
            AuthSession(
                accessToken = "access",
                refreshToken = "refresh",
                user =
                    User(
                        id = 1,
                        email = "v@example.com",
                        displayName = "Vlad",
                        defaultCurrency = "UAH",
                    ),
            )
    }
}
