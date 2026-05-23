package com.vstorchevyi.skilky.data.mapper

import com.vstorchevyi.skilky.api.AuthResponse
import com.vstorchevyi.skilky.api.UserDto
import com.vstorchevyi.skilky.domain.model.AuthSession
import com.vstorchevyi.skilky.domain.model.User

/**
 * Turns the `:core` wire DTOs into domain models. Keeping the mapping here is
 * what stops `@Serializable` types from leaking past the data layer.
 */
internal fun AuthResponse.toDomain(): AuthSession =
    AuthSession(
        accessToken = token,
        refreshToken = refreshToken,
        user = user.toDomain(),
    )

internal fun UserDto.toDomain(): User =
    User(
        id = id,
        email = email,
        displayName = displayName,
        defaultCurrency = defaultCurrency,
    )
