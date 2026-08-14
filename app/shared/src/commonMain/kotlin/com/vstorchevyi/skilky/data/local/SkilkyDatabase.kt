package com.vstorchevyi.skilky.data.local

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection

/**
 * Root Room database. KSP fills in the platform-specific constructors via
 * [SkilkyDatabaseConstructor]; each platform module supplies the file path
 * and the SQLite driver when it builds the [SkilkyDatabase] singleton.
 */
@Database(
    entities = [CategoryEntity::class, ExpenseEntity::class, SyncQueueEntity::class],
    version = 2,
    exportSchema = true,
)
@ConstructedBy(SkilkyDatabaseConstructor::class)
internal abstract class SkilkyDatabase : RoomDatabase() {
    abstract fun categoryDao(): CategoryDao

    abstract fun expenseDao(): ExpenseDao

    abstract fun syncQueueDao(): SyncQueueDao
}

internal val MIGRATION_1_2 =
    object : Migration(1, 2) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execute(
                """
                CREATE TABLE IF NOT EXISTS sync_queue (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    clientId TEXT NOT NULL,
                    localExpenseId INTEGER NOT NULL,
                    name TEXT NOT NULL,
                    amount REAL NOT NULL,
                    currency TEXT NOT NULL,
                    categoryId INTEGER NOT NULL,
                    note TEXT,
                    inputType TEXT NOT NULL,
                    dateIso TEXT NOT NULL,
                    createdAtMillis INTEGER NOT NULL,
                    retryCount INTEGER NOT NULL,
                    status TEXT NOT NULL
                )
                """.trimIndent(),
            )
            connection.execute(
                "CREATE UNIQUE INDEX IF NOT EXISTS index_sync_queue_clientId ON sync_queue (clientId)",
            )
            connection.execute(
                "CREATE UNIQUE INDEX IF NOT EXISTS index_sync_queue_localExpenseId " +
                    "ON sync_queue (localExpenseId)",
            )
            connection.execute(
                "CREATE INDEX IF NOT EXISTS index_sync_queue_status_createdAtMillis " +
                    "ON sync_queue (status, createdAtMillis)",
            )
        }
    }

private fun SQLiteConnection.execute(sql: String) {
    prepare(sql).use { it.step() }
}

/**
 * KMP-style `expect` for the constructor that Room generates. KSP emits the
 * `actual` per platform target; we do not write any code in the platform
 * source sets for this.
 */
@Suppress("KotlinNoActualForExpect", "NO_ACTUAL_FOR_EXPECT")
internal expect object SkilkyDatabaseConstructor : RoomDatabaseConstructor<SkilkyDatabase> {
    override fun initialize(): SkilkyDatabase
}
