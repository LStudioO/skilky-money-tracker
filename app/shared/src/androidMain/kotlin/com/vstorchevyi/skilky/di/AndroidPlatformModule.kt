package com.vstorchevyi.skilky.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.vstorchevyi.skilky.data.local.SkilkyDatabase
import okio.Path.Companion.toPath
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * The Android side of the platform Koin module: a `DataStore<Preferences>`
 * file rooted under the app's private `filesDir`, plus the `SkilkyDatabase`
 * built against the app's standard database directory. The host
 * `Application` passes its context in, which sidesteps adding `koin-android`
 * just to look it up later.
 */
fun androidPlatformModule(context: Context): Module =
    module {
        single<DataStore<Preferences>> {
            PreferenceDataStoreFactory.createWithPath(
                produceFile = {
                    context.filesDir
                        .resolve(TOKEN_STORE_FILE)
                        .absolutePath
                        .toPath()
                },
            )
        }
        single {
            Room.databaseBuilder(
                context = context,
                klass = SkilkyDatabase::class.java,
                name = context.getDatabasePath(DATABASE_FILE).absolutePath,
            )
                .setDriver(BundledSQLiteDriver())
                .build()
        }
        single { get<SkilkyDatabase>().categoryDao() }
        single { get<SkilkyDatabase>().expenseDao() }
    }

private const val TOKEN_STORE_FILE = "skilky_tokens.preferences_pb"
private const val DATABASE_FILE = "skilky.db"
