package com.vstorchevyi.skilky.domain.usecase

import com.vstorchevyi.skilky.domain.model.AuthSession
import com.vstorchevyi.skilky.domain.repository.AuthRepository

/**
 * Reads the persisted session at startup. A non-null result means the app can
 * skip the login screen and go straight to the signed-in flow.
 */
class GetCurrentSessionUseCase(
    private val authRepository: AuthRepository,
) {
    suspend operator fun invoke(): AuthSession? = authRepository.currentSession()
}
