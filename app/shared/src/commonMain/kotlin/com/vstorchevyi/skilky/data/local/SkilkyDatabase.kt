package com.vstorchevyi.skilky.data.local

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor

/**
 * Root Room database. KSP fills in the platform-specific constructors via
 * [SkilkyDatabaseConstructor]; each platform module supplies the file path
 * and the SQLite driver when it builds the [SkilkyDatabase] singleton.
 */
@Database(
    entities = [CategoryEntity::class, ExpenseEntity::class],
    version = 1,
    exportSchema = true,
)
@ConstructedBy(SkilkyDatabaseConstructor::class)
internal abstract class SkilkyDatabase : RoomDatabase() {
    abstract fun categoryDao(): CategoryDao

    abstract fun expenseDao(): ExpenseDao
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
