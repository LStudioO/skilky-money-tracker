package com.vstorchevyi.skilky.domain.usecase

import com.vstorchevyi.skilky.domain.model.AppError
import com.vstorchevyi.skilky.domain.model.AuthSession
import com.vstorchevyi.skilky.domain.model.Either
import com.vstorchevyi.skilky.domain.repository.AuthRepository

/** Creates an account and returns the resulting signed-in session. */
class RegisterUseCase(
    private val authRepository: AuthRepository,
) {
    suspend operator fun invoke(
        email: String,
        password: String,
        displayName: String,
    ): Either<AppError, AuthSession> = authRepository.register(email, password, displayName)
}
