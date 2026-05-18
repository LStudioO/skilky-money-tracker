package com.vstorchevyi.skilky.security

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import com.vstorchevyi.skilky.config.AppConfig
import com.vstorchevyi.skilky.support.aJwtConfig
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class JwtTokenProviderTest {
    @Test
    fun `token carries userId and email claims`() {
        // Arrange
        val sut = createSut()
        val verifier = buildVerifier()

        // Act
        val token = sut.createAccessToken(userId = 42, email = "vlad@example.com")

        // Assert
        val decoded = verifier.verify(token)
        decoded.getClaim("userId").asLong() shouldBe 42L
        decoded.getClaim("email").asString() shouldBe "vlad@example.com"
    }

    @Test
    fun `token has the configured issuer and audience`() {
        // Arrange
        val config = aJwtConfig()
        val sut = createSut(config)
        val verifier = buildVerifier(config)

        // Act
        val token = sut.createAccessToken(userId = 1, email = "x@y.z")

        // Assert
        val decoded = verifier.verify(token)
        decoded.issuer shouldBe config.issuer
        decoded.audience shouldContain config.audience
    }

    @Test
    fun `expiry is the configured number of days from issuedAt`() {
        // Arrange
        val config = aJwtConfig(accessTokenExpirationDays = 3)
        val sut = createSut(config)
        val verifier = buildVerifier(config)

        // Act
        val token = sut.createAccessToken(userId = 1, email = "x@y.z")

        // Assert
        val decoded = verifier.verify(token)
        val seconds = (decoded.expiresAt.time - decoded.issuedAt.time) / 1000
        val expected = config.accessTokenExpirationDays.toLong() * SECONDS_IN_DAY
        seconds shouldBe expected
    }

    @Test
    fun `issuedAt is taken from current system time, not a fixed value`() {
        // Arrange
        val sut = createSut()
        val verifier = buildVerifier()
        val before = System.currentTimeMillis()

        // Act
        val token = sut.createAccessToken(userId = 1, email = "x@y.z")

        // Assert: JWT iat is encoded in whole seconds, so issuedMillis can
        // land up to 999 ms before `before` due to truncation. Allowing a
        // 1-second floor catches the real concern (constant or epoch-zero
        // iat) without flaking on the truncation.
        val issuedMillis = verifier.verify(token).issuedAt.time
        val drift = before - issuedMillis
        withClue("iat should be close to now; got drift=$drift ms") {
            drift shouldBeGreaterThan -1_000L
            (60_000L - drift) shouldBeGreaterThan 0L
        }
    }

    @Test
    fun `verifier rejects token signed by a different secret`() {
        // Arrange: SUT and verifier disagree on the secret.
        val sut = createSut(aJwtConfig(secret = "different-secret"))
        val verifier = buildVerifier()

        // Act
        val token = sut.createAccessToken(userId = 1, email = "x@y.z")

        // Assert
        shouldThrow<Exception> { verifier.verify(token) }
    }

    @Test
    fun `verifier rejects token with wrong issuer`() {
        // Arrange
        val sut = createSut(aJwtConfig(issuer = "wrong-issuer"))
        val verifier = buildVerifier()

        // Act
        val token = sut.createAccessToken(userId = 1, email = "x@y.z")

        // Assert
        shouldThrow<Exception> { verifier.verify(token) }
    }

    private fun createSut(config: AppConfig.JwtConfig = aJwtConfig()) = JwtTokenProvider(config)

    private fun buildVerifier(config: AppConfig.JwtConfig = aJwtConfig()): JWTVerifier =
        JWT
            .require(Algorithm.HMAC256(config.secret))
            .withIssuer(config.issuer)
            .withAudience(config.audience)
            .build()

    companion object {
        private const val SECONDS_IN_DAY = 24L * 60L * 60L
    }
}
