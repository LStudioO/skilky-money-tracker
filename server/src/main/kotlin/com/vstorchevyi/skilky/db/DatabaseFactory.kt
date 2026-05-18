package com.vstorchevyi.skilky.db

import com.vstorchevyi.skilky.config.AppConfig
import com.vstorchevyi.skilky.db.tables.RefreshTokensTable
import com.vstorchevyi.skilky.db.tables.UsersTable
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.inTopLevelSuspendTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

/**
 * Glues HikariCP (connection pool), Exposed (DSL), and the
 * suspend-to-blocking bridge needed by repositories.
 *
 * **Pool.** Hikari keeps N warm connections so individual requests do not
 * pay the ~100-200 ms Postgres handshake on every call.
 *
 * **Suspend bridge.** [dbQuery] shifts work to `Dispatchers.IO` (a pool
 * sized for blocking I/O) and opens a top-level Exposed transaction.
 * Repositories run all JDBC inside this so the Netty event loop is never
 * blocked.
 *
 * **Schema management.** [init] calls `SchemaUtils.create` on first boot.
 * Idempotent for new tables, but does NOT add columns to existing tables.
 * Migrations (Flyway / Liquibase) come later, once we have to ship schema
 * changes against existing production data.
 */
class DatabaseFactory(
    private val config: AppConfig.DatabaseConfig,
) {
    private lateinit var dataSource: HikariDataSource
    private lateinit var database: Database

    fun init() {
        dataSource = HikariDataSource(buildHikariConfig())
        database = Database.connect(dataSource)
        transaction(database) {
            SchemaUtils.create(UsersTable, RefreshTokensTable)
        }
    }

    fun close() {
        if (::dataSource.isInitialized) dataSource.close()
    }

    /** Used by `/health/db`. Returns false on any failure so the endpoint can report degraded instead of 500. */
    suspend fun ping(): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                dataSource.connection.use { conn ->
                    conn.createStatement().use { stmt ->
                        stmt.execute("SELECT 1")
                    }
                }
                true
            }.getOrDefault(false)
        }

    /**
     * Run [block] inside a top-level Exposed transaction on the IO
     * dispatcher. Top-level means a nested call joins the existing
     * transaction rather than opening a new one; usually nested
     * transactions indicate a design mistake.
     */
    suspend fun <T> dbQuery(block: suspend () -> T): T =
        withContext(Dispatchers.IO) {
            inTopLevelSuspendTransaction(db = database) { block() }
        }

    private fun buildHikariConfig() =
        HikariConfig().apply {
            jdbcUrl = config.jdbcUrl
            username = config.user
            password = config.password
            maximumPoolSize = config.maxPoolSize
            driverClassName = "org.postgresql.Driver"
            // Autocommit off so Exposed manages transaction boundaries.
            // Autocommit on would make every statement an implicit
            // single-statement transaction, defeating the wrapper.
            isAutoCommit = false
            // REPEATABLE READ: stricter than Postgres' default READ
            // COMMITTED. Inside one transaction, every SELECT sees the
            // same snapshot. Prevents non-repeatable reads at the cost of
            // occasional serialization failures under heavy write
            // contention. Acceptable for our workload.
            transactionIsolation = "TRANSACTION_REPEATABLE_READ"
            // Log a warning if a connection is held > 30 s. Almost always
            // a leak (check-out without check-in). Off by default, cheap
            // to enable.
            leakDetectionThreshold = LEAK_DETECTION_MS
            validate()
        }

    companion object {
        private const val LEAK_DETECTION_MS = 30_000L
    }
}
