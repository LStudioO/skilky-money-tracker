package com.vstorchevyi.skilky.data.local

import com.vstorchevyi.skilky.domain.model.AuthSession

/**
 * Persists the signed-in [AuthSession] between app launches.
 *
 * `AuthRepositoryImpl` depends on this interface, not a concrete store, so the
 * backing implementation can change without touching the repository.
 */
internal interface TokenStorage {
    suspend fun save(session: AuthSession)

    suspend fun read(): AuthSession?

    suspend fun clear()
}
