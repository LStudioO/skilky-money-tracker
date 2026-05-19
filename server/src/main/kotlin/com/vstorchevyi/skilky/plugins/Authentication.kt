package com.vstorchevyi.skilky.plugins

import com.vstorchevyi.skilky.security.JwtTokenProvider
import com.vstorchevyi.skilky.security.JwtUserPrincipal
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.jwt

private const val JWT_AUTH = "jwt-auth"

fun Application.configureJwtAuthentication(tokenProvider: JwtTokenProvider) {
    install(Authentication) {
        jwt(JWT_AUTH) {
            realm = "Skilky API"
            verifier(tokenProvider.verifier)
            validate { credential ->
                val userId =
                    runCatching { credential.payload.getClaim("userId").asLong() }
                        .getOrNull()
                        ?: return@validate null
                val email =
                    runCatching { credential.payload.getClaim("email").asString() }
                        .getOrNull()
                        ?: return@validate null
                JwtUserPrincipal(userId = userId, email = email)
            }
        }
    }
}

/** Name passed to [io.ktor.server.auth.authenticate]. */
fun jwtAuthName(): String = JWT_AUTH
