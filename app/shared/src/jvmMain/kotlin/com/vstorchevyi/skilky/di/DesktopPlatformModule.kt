package com.vstorchevyi.skilky.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import okio.Path.Companion.toPath
import org.koin.core.module.Module
import org.koin.dsl.module
import java.io.File

/**
 * The desktop side of the platform Koin module: a `DataStore<Preferences>`
 * file under `~/.skilky/`. The directory is created on first access. This
 * target also covers `jvmTest`, where any leftover file from a previous run is
 * left alone, since each test creates its own DataStore on a tmp path.
 */
val desktopPlatformModule: Module =
    module {
        single<DataStore<Preferences>> {
            PreferenceDataStoreFactory.createWithPath(
                produceFile = {
                    val baseDir =
                        File(
                            System.getProperty("user.home") ?: ".",
                            ".skilky",
                        )
                    baseDir.mkdirs()
                    baseDir.resolve(TOKEN_STORE_FILE).absolutePath.toPath()
                },
            )
        }
    }

private const val TOKEN_STORE_FILE = "skilky_tokens.preferences_pb"
