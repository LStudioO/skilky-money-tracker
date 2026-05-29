package com.vstorchevyi.skilky.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.vstorchevyi.skilky.domain.model.AuthSession
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

/**
 * Backs [TokenStorage] with a `DataStore<Preferences>` so the session survives
 * an app restart. The whole session is serialized as a single JSON string under
 * one preference key, which keeps writes atomic and means a schema bump is a
 * key-name bump rather than a five-key migration.
 *
 * The platform Koin modules supply the [DataStore] instance; the file path
 * lives there too because it is the one platform-specific bit of the design.
 *
 * Note: DataStore Preferences is not encrypted at rest. Encrypting the refresh
 * token via the Android Keystore / iOS Keychain is tracked as future hardening.
 */
internal class DataStoreTokenStorage(
    private val dataStore: DataStore<Preferences>,
) : TokenStorage {
    override suspend fun save(session: AuthSession) {
        val payload = JSON.encodeToString(session.toStored())
        dataStore.edit { prefs ->
            prefs[SESSION_KEY] = payload
        }
    }

    override suspend fun read(): AuthSession? =
        dataStore
            .data
            .map { prefs -> prefs[SESSION_KEY] }
            .first()
            ?.let { JSON.decodeFromString<StoredAuthSession>(it).toDomain() }

    override suspend fun clear() {
        dataStore.edit { it.clear() }
    }

    private companion object {
        // Versioned so a future format change can land without overlapping the
        // old key. Old keys are simply ignored on read.
        val SESSION_KEY = stringPreferencesKey("auth_session_v1")
        val JSON: Json =
            Json {
                ignoreUnknownKeys = true
                explicitNulls = false
            }
    }
}
