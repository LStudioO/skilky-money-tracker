package com.vstorchevyi.skilky.data.local

import com.vstorchevyi.skilky.domain.model.AuthSession

/**
 * Holds the session in memory only. The session is lost when the process ends,
 * so the app starts signed out every launch.
 *
 * This is the foundation-stage [TokenStorage]. A DataStore-backed store that
 * survives restarts replaces it in a follow-up; swapping it is a one-line
 * change in the Koin module.
 */
internal class InMemoryTokenStorage : TokenStorage {
    private var session: AuthSession? = null

    override suspend fun save(session: AuthSession) {
        this.session = session
    }

    override suspend fun read(): AuthSession? = session

    override suspend fun clear() {
        session = null
    }
}
