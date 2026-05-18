package com.vstorchevyi.skilky.routes

import com.vstorchevyi.skilky.api.UserDto
import com.vstorchevyi.skilky.domain.model.User

/**
 * Domain to wire mapping for the auth feature. Lives in the routes ring so
 * the domain model has no compile-time dependency on the wire format.
 */
fun User.toDto(): UserDto =
    UserDto(
        id = id,
        email = email,
        displayName = displayName,
        defaultCurrency = defaultCurrency,
    )
