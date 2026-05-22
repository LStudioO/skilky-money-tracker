package com.vstorchevyi.skilky.plugins

import com.vstorchevyi.skilky.security.JwtTokenProvider
import com.vstorchevyi.skilky.security.JwtUserPrincipal
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.jwt
import org.koin.ktor.ext.get
import org.slf4j.LoggerFactory

private const val JWT_AUTH = "jwt-auth"

private val logger = LoggerFactory.getLogger("com.vstorchevyi.skilky.plugins.Authentication")

fun Application.configureJwtAuthentication() {
    val tokenProvider = get<JwtTokenProvider>()
    install(Authentication) {
        jwt(JWT_AUTH) {
            realm = "Skilky API"
            verifier(tokenProvider.verifier)
            validate { credential ->
                val userId =
                    runCatching { credential.payload.getClaim("userId").asLong() }
                        .onFailure { logger.debug("Rejecting JWT: userId claim unreadable", it) }
                        .getOrNull()
                        ?: return@validate null
                val email =
                    runCatching { credential.payload.getClaim("email").asString() }
                        .onFailure { logger.debug("Rejecting JWT: email claim unreadable", it) }
                        .getOrNull()
                        ?: return@validate null
                JwtUserPrincipal(userId = userId, email = email)
            }
        }
    }
}

/** Name passed to [io.ktor.server.auth.authenticate]. */
fun jwtAuthName(): String = JWT_AUTH
