package com.vstorchevyi.skilky.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.vstorchevyi.skilky.data.local.SkilkyDatabase
import kotlinx.cinterop.ExperimentalForeignApi
import okio.Path.Companion.toPath
import org.koin.core.module.Module
import org.koin.dsl.module
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask

/** The single Swift-visible entry point: start Koin with the iOS module. */
fun startKoinIos() {
    initializeKoin(iosPlatformModule)
}

/**
 * The iOS side of the platform Koin module: a `DataStore<Preferences>` file
 * plus the `SkilkyDatabase`, both rooted under the app's Documents directory.
 * iOS does not expose a `filesDir` analogue, so the path is resolved via
 * `NSFileManager`.
 */
private val iosPlatformModule: Module =
    module {
        single<DataStore<Preferences>> {
            PreferenceDataStoreFactory.createWithPath(
                produceFile = { documentsPath(TOKEN_STORE_FILE) },
            )
        }
        single {
            Room.databaseBuilder<SkilkyDatabase>(
                name = documentsPath(DATABASE_FILE).toString(),
            )
                .setDriver(BundledSQLiteDriver())
                .build()
        }
        single { get<SkilkyDatabase>().categoryDao() }
        single { get<SkilkyDatabase>().expenseDao() }
    }

@OptIn(ExperimentalForeignApi::class)
private fun documentsPath(fileName: String): okio.Path {
    val documents: NSURL =
        requireNotNull(
            NSFileManager.defaultManager.URLForDirectory(
                directory = NSDocumentDirectory,
                inDomain = NSUserDomainMask,
                appropriateForURL = null,
                create = true,
                error = null,
            ),
        ) { "Documents directory was unavailable" }
    val base = requireNotNull(documents.path) { "Documents directory has no path" }
    return "$base/$fileName".toPath()
}

private const val TOKEN_STORE_FILE = "skilky_tokens.preferences_pb"
private const val DATABASE_FILE = "skilky.db"
