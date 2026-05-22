package com.vstorchevyi.skilky.support

import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.testing.ApplicationTestBuilder

/**
 * Minimal in-memory config for tests that do not need a database.
 *
 * Mirrors the keys [com.vstorchevyi.skilky.config.AppConfig.from] reads as
 * required. Adding a new required key to AppConfig means adding it here too,
 * or every test using this helper will fail at boot with
 * ApplicationConfigurationException.
 */
fun ApplicationTestBuilder.useTestConfig(extra: Map<String, String> = emptyMap()) {
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
                "skilky.security.bcryptCost" to "4",
                *extra.entries.map { it.key to it.value }.toTypedArray(),
            )
    }
}
