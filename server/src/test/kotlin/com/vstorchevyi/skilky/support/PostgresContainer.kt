package com.vstorchevyi.skilky.support

import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.testing.ApplicationTestBuilder
import org.testcontainers.containers.PostgreSQLContainer

/**
 * Shared Postgres container for integration tests.
 *
 * One container is started lazily on first access and reused across the
 * whole test JVM. Container startup is the expensive part (~3 s); the
 * `truncateAllTables` helper resets state between tests for isolation.
 *
 * `withReuse(true)` lets the container survive across separate test runs
 * if the developer's `~/.testcontainers.properties` has `testcontainers.reuse.enable=true`.
 * CI starts fresh every time and ignores the flag.
 */
object PostgresContainer {
    private val container: PostgreSQLContainer<*> by lazy {
        PostgreSQLContainer("postgres:17-alpine")
            .withDatabaseName("skilky_test")
            .withUsername("skilky")
            .withPassword("skilky")
            .withReuse(true)
            .also { it.start() }
    }

    val jdbcUrl: String get() = container.jdbcUrl
    val username: String get() = container.username
    val password: String get() = container.password

    /**
     * Drops every table in the public schema so the next test starts
     * against an empty database. The application reapplies schema on
     * module boot via SchemaUtils.create.
     */
    fun reset() {
        container.createConnection("").use { conn ->
            conn.createStatement().use { stmt ->
                stmt.execute("DROP SCHEMA public CASCADE")
                stmt.execute("CREATE SCHEMA public")
            }
        }
    }
}

/** [useTestConfig] plus a database block pointing at the shared container. */
fun ApplicationTestBuilder.useTestConfigWithDb(extra: Map<String, String> = emptyMap()) {
    environment {
        config =
            MapApplicationConfig(
                "skilky.api.version" to "1.0.0",
                "skilky.cors.allowedHosts" to "*",
                "skilky.jwt.secret" to "test-secret",
                "skilky.jwt.issuer" to "skilky-tracker-test",
                "skilky.jwt.audience" to "skilky-users-test",
                "skilky.jwt.accessTokenExpirationDays" to "7",
                "skilky.jwt.refreshTokenExpirationDays" to "90",
                "skilky.security.refreshTokenPepper" to "test-pepper",
                "skilky.database.jdbcUrl" to PostgresContainer.jdbcUrl,
                "skilky.database.user" to PostgresContainer.username,
                "skilky.database.password" to PostgresContainer.password,
                "skilky.database.maxPoolSize" to "4",
                *extra.entries.map { it.key to it.value }.toTypedArray(),
            )
    }
}
