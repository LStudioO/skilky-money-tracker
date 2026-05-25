package com.vstorchevyi.skilky.data.local

import com.vstorchevyi.skilky.domain.model.AuthSession
import com.vstorchevyi.skilky.domain.model.User
import kotlinx.serialization.Serializable

/**
 * The on-disk shape of an [AuthSession]. Kept separate from the wire DTOs in
 * `:core` and from the domain model so the storage format can change without
 * dragging either of them along.
 */
@Serializable
internal data class StoredAuthSession(
    val accessToken: String,
    val refreshToken: String,
    val user: StoredUser,
)

@Serializable
internal data class StoredUser(
    val id: Long,
    val email: String,
    val displayName: String,
    val defaultCurrency: String,
)

internal fun AuthSession.toStored(): StoredAuthSession =
    StoredAuthSession(
        accessToken = accessToken,
        refreshToken = refreshToken,
        user =
            StoredUser(
                id = user.id,
                email = user.email,
                displayName = user.displayName,
                defaultCurrency = user.defaultCurrency,
            ),
    )

internal fun StoredAuthSession.toDomain(): AuthSession =
    AuthSession(
        accessToken = accessToken,
        refreshToken = refreshToken,
        user =
            User(
                id = user.id,
                email = user.email,
                displayName = user.displayName,
                defaultCurrency = user.defaultCurrency,
            ),
    )
