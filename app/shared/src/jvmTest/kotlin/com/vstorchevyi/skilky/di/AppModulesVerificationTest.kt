package com.vstorchevyi.skilky.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.vstorchevyi.skilky.domain.repository.AuthRepository
import com.vstorchevyi.skilky.domain.usecase.GetCurrentSessionUseCase
import com.vstorchevyi.skilky.domain.usecase.LoginUseCase
import com.vstorchevyi.skilky.domain.usecase.LogoutUseCase
import com.vstorchevyi.skilky.domain.usecase.RegisterUseCase
import com.vstorchevyi.skilky.ui.auth.LoginViewModel
import com.vstorchevyi.skilky.ui.auth.RegisterViewModel
import com.vstorchevyi.skilky.ui.home.HomeViewModel
import okio.Path.Companion.toPath
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertNotNull

/**
 * Static check of the production Koin graph: every common-module binding plus
 * a JVM-shaped platform module must resolve and construct without error. This
 * catches a missing or misconfigured binding (for example, a new constructor
 * param nobody registered) before it shows up at runtime as
 * `InstanceCreationException` from inside Compose.
 *
 * The `DataStore<Preferences>` is built against a real on-disk tmp file, not
 * a mock, so the chain that produces `TokenStorage` is exercised end to end.
 */
class AppModulesVerificationTest {
    private lateinit var tempDir: File

    @BeforeTest
    fun setUp() {
        tempDir = Files.createTempDirectory("koin-verify").toFile()
    }

    @AfterTest
    fun tearDown() {
        stopKoin()
        tempDir.deleteRecursively()
    }

    @Test
    fun `every use case and view model resolves from the production graph`() {
        // Arrange
        val testPlatformModule =
            module {
                single<DataStore<Preferences>> {
                    PreferenceDataStoreFactory.createWithPath(
                        produceFile = {
                            tempDir.resolve("tokens.preferences_pb").absolutePath.toPath()
                        },
                    )
                }
            }
        val app = startKoin { modules(appModules + testPlatformModule) }

        // Act + Assert: resolving each top-level entry point would throw
        // InstanceCreationException if any transitive binding were broken.
        with(app.koin) {
            assertNotNull(get<AuthRepository>())
            assertNotNull(get<GetCurrentSessionUseCase>())
            assertNotNull(get<LoginUseCase>())
            assertNotNull(get<RegisterUseCase>())
            assertNotNull(get<LogoutUseCase>())
            assertNotNull(get<LoginViewModel>())
            assertNotNull(get<RegisterViewModel>())
            assertNotNull(get<HomeViewModel>())
        }
    }
}
