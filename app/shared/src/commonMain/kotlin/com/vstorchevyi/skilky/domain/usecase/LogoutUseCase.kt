package com.vstorchevyi.skilky.domain.usecase

import com.vstorchevyi.skilky.domain.repository.AuthRepository

/** Forgets the stored session, returning the app to a signed-out state. */
class LogoutUseCase(
    private val authRepository: AuthRepository,
) {
    suspend operator fun invoke() = authRepository.logout()
}
