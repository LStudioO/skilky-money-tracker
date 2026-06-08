package com.vstorchevyi.skilky.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.vstorchevyi.skilky.data.local.SkilkyDatabase
import okio.Path.Companion.toPath
import org.koin.core.module.Module
import org.koin.dsl.module
import java.io.File

/**
 * The desktop side of the platform Koin module: a `DataStore<Preferences>`
 * file plus the `SkilkyDatabase`, both under `~/.skilky/`. The directory is
 * created on first access. This target also covers `jvmTest`; tests that
 * want isolation supply their own builder.
 */
val desktopPlatformModule: Module =
    module {
        single<DataStore<Preferences>> {
            PreferenceDataStoreFactory.createWithPath(
                produceFile = { skilkyDir().resolve(TOKEN_STORE_FILE).absolutePath.toPath() },
            )
        }
        single {
            Room.databaseBuilder<SkilkyDatabase>(
                name = skilkyDir().resolve(DATABASE_FILE).absolutePath,
            )
                .setDriver(BundledSQLiteDriver())
                .build()
        }
        single { get<SkilkyDatabase>().categoryDao() }
        single { get<SkilkyDatabase>().expenseDao() }
    }

private fun skilkyDir(): File {
    val dir = File(System.getProperty("user.home") ?: ".", ".skilky")
    dir.mkdirs()
    return dir
}

private const val TOKEN_STORE_FILE = "skilky_tokens.preferences_pb"
private const val DATABASE_FILE = "skilky.db"
