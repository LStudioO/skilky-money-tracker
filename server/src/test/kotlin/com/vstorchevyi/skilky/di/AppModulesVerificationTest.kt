package com.vstorchevyi.skilky.di

import com.vstorchevyi.skilky.ai.CachedCategoryLoader
import com.vstorchevyi.skilky.config.AppConfig
import org.koin.dsl.module
import org.koin.test.verify.verify
import kotlin.reflect.KClass
import kotlin.test.Test

/**
 * Static check of the production Koin graph: every definition's constructor
 * dependency must resolve to another definition. Catches a missing binding
 * (e.g. a new constructor param that nobody registered) in milliseconds,
 * without a database or Testcontainers.
 *
 * Scope is the full graph only. [appModules] returns a different module list
 * per [AppConfig] shape, but [verify] reads `AppConfig`'s own constructor and
 * treats its `database`/`ai` params as required even though they are nullable.
 * The DB-less and AI-less shapes would therefore need those types whitelisted,
 * which weakens the check. The fully-populated config exercises every
 * definition anyway, so that is the one shape worth verifying.
 */
class AppModulesVerificationTest {
    @Test
    fun `the production Koin graph resolves every constructor dependency`() {
        // Arrange: a config with every block present, so the conditional
        // persistence and AI modules are included.
        val graph = module { includes(*appModules(fullAppConfig()).toTypedArray()) }

        // Act + Assert: a missing binding throws MissingKoinDefinitionException.
        // Optional constructor params with non-Koin defaults (OllamaClient's
        // HttpClient, TextParsingService's Logger, CachedCategoryLoader's clock)
        // log an informational stderr warning but do not fail the check.
        graph.verify(extraTypes = listOf(dynamicallyBuiltCategorySource()))
    }

    /**
     * `CachedCategoryLoader.source` and `TextParsingService.loadCategories`
     * take a `suspend (Long) -> List<CategoryHint>` assembled inside the module
     * lambda, not pulled from the container. [verify] inspects the bound type's
     * constructor and cannot resolve a function type, so it has to be
     * whitelisted. Reading the `KClass` straight off the constructor keeps it
     * exact: verify compares against this same `type.classifier`. Both params
     * share the type, so one entry covers them.
     */
    private fun dynamicallyBuiltCategorySource(): KClass<*> =
        CachedCategoryLoader::class.constructors
            .first()
            .parameters
            .first { it.name == "source" }
            .type.classifier as KClass<*>

    private fun fullAppConfig(): AppConfig =
        AppConfig(
            api = AppConfig.ApiConfig(version = "1.0.0"),
            cors = AppConfig.CorsConfig(allowedHosts = listOf("*")),
            database =
                AppConfig.DatabaseConfig(
                    jdbcUrl = "jdbc:postgresql://localhost/skilky",
                    user = "skilky",
                    password = "skilky",
                    maxPoolSize = 4,
                ),
            jwt =
                AppConfig.JwtConfig(
                    secret = "test-secret",
                    issuer = "skilky-tracker-test",
                    audience = "skilky-users-test",
                    accessTokenExpirationDays = 7,
                    refreshTokenExpirationDays = 90,
                ),
            security =
                AppConfig.SecurityConfig(
                    refreshTokenPepper = "test-pepper",
                    bcryptCost = 4,
                ),
            ai =
                AppConfig.AiConfig(
                    baseUrl = "http://localhost:11434",
                    model = "test-model",
                    timeoutSeconds = 30,
                    keepAlive = "5m",
                ),
        )
}
