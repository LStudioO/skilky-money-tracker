package com.vstorchevyi.skilky.config

import io.ktor.server.config.ApplicationConfig

/**
 * Typed projection of Ktor's HOCON-backed config. Read once at startup so
 * every consumer sees an immutable, type-checked struct.
 *
 * [from] dereferences each required key eagerly. Missing keys throw at
 * boot, not at request time: a misconfigured server should fail fast in
 * deployment, never silently 500 in production.
 *
 * @property database Nullable so tests can omit the entire `skilky.database`
 *   block and still boot. Application wiring guards every DB-dependent
 *   plugin on this being non-null.
 * @property security Secrets distinct from [jwt] on purpose: one key per
 *   cryptographic use, so a leak of the JWT signing surface does not
 *   cascade into refresh-token forgery.
 */
data class AppConfig(
    val api: ApiConfig,
    val cors: CorsConfig,
    val database: DatabaseConfig?,
    val jwt: JwtConfig,
    val security: SecurityConfig,
) {
    data class ApiConfig(
        val version: String,
    )

    data class CorsConfig(
        val allowedHosts: List<String>,
    )

    data class DatabaseConfig(
        val jdbcUrl: String,
        val user: String,
        val password: String,
        val maxPoolSize: Int,
    )

    data class JwtConfig(
        val secret: String,
        val issuer: String,
        val audience: String,
        val accessTokenExpirationDays: Int,
        val refreshTokenExpirationDays: Int,
    )

    /** @property refreshTokenPepper HMAC key for refresh-token hashing. */
    data class SecurityConfig(
        val refreshTokenPepper: String,
    )

    companion object {
        fun from(config: ApplicationConfig): AppConfig =
            AppConfig(
                api =
                    ApiConfig(
                        version = config.property("skilky.api.version").getString(),
                    ),
                cors =
                    CorsConfig(
                        allowedHosts =
                            config
                                .property("skilky.cors.allowedHosts")
                                .getString()
                                .split(",")
                                .map(String::trim)
                                .filter(String::isNotEmpty),
                    ),
                // Presence of jdbcUrl is the signal that the operator
                // intends to run with a DB. Other database.* properties are
                // then required.
                database =
                    config.propertyOrNull("skilky.database.jdbcUrl")?.let {
                        DatabaseConfig(
                            jdbcUrl = config.property("skilky.database.jdbcUrl").getString(),
                            user = config.property("skilky.database.user").getString(),
                            password = config.property("skilky.database.password").getString(),
                            maxPoolSize = config.property("skilky.database.maxPoolSize").getString().toInt(),
                        )
                    },
                jwt =
                    JwtConfig(
                        secret = config.property("skilky.jwt.secret").getString(),
                        issuer = config.property("skilky.jwt.issuer").getString(),
                        audience = config.property("skilky.jwt.audience").getString(),
                        accessTokenExpirationDays =
                            config.property("skilky.jwt.accessTokenExpirationDays").getString().toInt(),
                        refreshTokenExpirationDays =
                            config.property("skilky.jwt.refreshTokenExpirationDays").getString().toInt(),
                    ),
                security =
                    SecurityConfig(
                        refreshTokenPepper = config.property("skilky.security.refreshTokenPepper").getString(),
                    ),
            )
    }
}
