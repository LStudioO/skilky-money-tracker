package com.vstorchevyi.skilky.di

import androidx.datastore.core.DataStore
import com.vstorchevyi.skilky.data.local.CategoryDao
import com.vstorchevyi.skilky.data.local.ExpenseDao
import com.vstorchevyi.skilky.data.local.SyncQueueDao
import com.vstorchevyi.skilky.data.sync.NetworkMonitor
import io.ktor.client.engine.HttpClientEngine
import kotlinx.datetime.TimeZone
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import org.koin.test.verify.verify
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.time.Clock

/**
 * Static check of the production Koin graph: `verify` walks every binding's
 * declared type and confirms each constructor parameter resolves to another
 * binding (or to a whitelisted external type). Catches a new constructor
 * param nobody registered in milliseconds, without starting Koin.
 *
 * `extraTypes` whitelists what `verify` would otherwise complain about:
 * - `DataStore` and `CategoryDao` come from each platform Koin module, not
 *   from `appModules`, so they have no binding in the slice we verify.
 * - `HttpClientEngine` is passed into `HttpClient` via the platform expect
 *   inside `createHttpClient`. `verify` only sees `HttpClient`'s primary
 *   constructor — it cannot read the builder lambda — so the engine
 *   parameter appears unresolved without a whitelist entry.
 */
class AppModulesVerificationTest {
    @OptIn(KoinExperimentalAPI::class)
    @Test
    fun `the production Koin graph resolves every constructor dependency`() {
        // Arrange
        val graph = module { includes(*appModules.toTypedArray()) }

        // Act + Assert
        graph.verify(
            extraTypes =
                listOf(
                    DataStore::class,
                    CategoryDao::class,
                    ExpenseDao::class,
                    SyncQueueDao::class,
                    NetworkMonitor::class,
                    HttpClientEngine::class,
                ),
        )
    }

    @Test
    fun `the production Koin graph provides input time dependencies`() {
        // Arrange
        val application = koinApplication { modules(appModules) }

        try {
            // Act + Assert
            assertSame(Clock.System, application.koin.get<Clock>())
            assertEquals(TimeZone.currentSystemDefault(), application.koin.get<TimeZone>())
        } finally {
            application.close()
        }
    }
}
