package com.vstorchevyi.skilky.security

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.vstorchevyi.skilky.config.AppConfig
import java.util.Date

class JwtTokenProvider(
    private val config: AppConfig.JwtConfig,
) {
    private val algorithm = Algorithm.HMAC256(config.secret)

    fun createAccessToken(
        userId: Long,
        email: String,
    ): String {
        val now = System.currentTimeMillis()
        val expiresAt = now + config.accessTokenExpirationDays * MILLIS_IN_DAY
        return JWT
            .create()
            .withIssuer(config.issuer)
            .withAudience(config.audience)
            .withClaim("userId", userId)
            .withClaim("email", email)
            .withIssuedAt(Date(now))
            .withExpiresAt(Date(expiresAt))
            .sign(algorithm)
    }

    companion object {
        private const val MILLIS_IN_DAY = 24L * 60L * 60L * 1000L
    }
}
