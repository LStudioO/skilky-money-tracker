package com.vstorchevyi.skilky.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
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
 * rooted under the app's Documents directory. iOS does not expose a `filesDir`
 * analogue, so the path is resolved via `NSFileManager`.
 */
private val iosPlatformModule: Module =
    module {
        single<DataStore<Preferences>> {
            PreferenceDataStoreFactory.createWithPath(
                produceFile = { iosTokenStorePath() },
            )
        }
    }

@OptIn(ExperimentalForeignApi::class)
private fun iosTokenStorePath(): okio.Path {
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
    return "$base/$TOKEN_STORE_FILE".toPath()
}

private const val TOKEN_STORE_FILE = "skilky_tokens.preferences_pb"
