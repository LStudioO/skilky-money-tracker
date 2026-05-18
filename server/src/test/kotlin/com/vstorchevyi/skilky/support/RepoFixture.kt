package com.vstorchevyi.skilky.support

import com.vstorchevyi.skilky.config.AppConfig
import com.vstorchevyi.skilky.db.DatabaseFactory

/**
 * Boots a fresh [DatabaseFactory] pointing at the shared Testcontainers
 * Postgres, with the schema reset to empty. Caller is responsible for
 * closing the factory.
 */
fun newRepoFixture(): DatabaseFactory {
    PostgresContainer.reset()
    val factory =
        DatabaseFactory(
            AppConfig.DatabaseConfig(
                jdbcUrl = PostgresContainer.jdbcUrl,
                user = PostgresContainer.username,
                password = PostgresContainer.password,
                maxPoolSize = 4,
            ),
        )
    factory.init()
    return factory
}
