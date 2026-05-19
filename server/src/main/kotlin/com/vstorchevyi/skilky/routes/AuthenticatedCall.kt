package com.vstorchevyi.skilky.routes

import com.vstorchevyi.skilky.security.JwtUserPrincipal
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.principal

/**
 * Logged-in user for handlers registered under [io.ktor.server.auth.authenticate].
 * If this is null, the auth pipeline is miswired (not a normal missing-token case).
 */
fun ApplicationCall.requireJwtPrincipal(): JwtUserPrincipal =
    principal<JwtUserPrincipal>()
        ?: error("Authenticated route without JwtUserPrincipal — check JWT validate callback")
