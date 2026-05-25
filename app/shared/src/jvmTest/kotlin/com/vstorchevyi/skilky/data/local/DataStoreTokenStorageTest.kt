package com.vstorchevyi.skilky.data.local

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.vstorchevyi.skilky.domain.model.AuthSession
import com.vstorchevyi.skilky.domain.model.User
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DataStoreTokenStorageTest {
    private lateinit var tempDir: File

    @BeforeTest
    fun setUp() {
        tempDir = Files.createTempDirectory("token-store").toFile()
    }

    @AfterTest
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `read returns null when no session has been saved`() =
        runTest {
            // Arrange
            val sut = createSut()

            // Act
            val read = sut.read()

            // Assert
            assertNull(read)
        }

    @Test
    fun `read returns the saved session`() =
        runTest {
            // Arrange
            val sut = createSut()
            val session = anAuthSession()
            sut.save(session)

            // Act
            val read = sut.read()

            // Assert
            assertEquals(session, read)
        }

    @Test
    fun `read returns the latest session when save is called twice`() =
        runTest {
            // Arrange
            val sut = createSut()
            sut.save(anAuthSession(accessToken = "first"))
            val updated = anAuthSession(accessToken = "second")
            sut.save(updated)

            // Act
            val read = sut.read()

            // Assert
            assertEquals(updated, read)
        }

    @Test
    fun `clear forgets the saved session`() =
        runTest {
            // Arrange
            val sut = createSut()
            sut.save(anAuthSession())

            // Act
            sut.clear()

            // Assert
            assertNull(sut.read())
        }

    private fun createSut(): DataStoreTokenStorage {
        val file = tempDir.resolve("tokens.preferences_pb")
        val dataStore =
            PreferenceDataStoreFactory.createWithPath(
                produceFile = { file.absolutePath.toPath() },
            )
        return DataStoreTokenStorage(dataStore)
    }

    private fun anAuthSession(
        accessToken: String = "access",
        refreshToken: String = "refresh",
        user: User = aUser(),
    ): AuthSession =
        AuthSession(
            accessToken = accessToken,
            refreshToken = refreshToken,
            user = user,
        )

    private fun aUser(): User =
        User(
            id = 1,
            email = "v@example.com",
            displayName = "V",
            defaultCurrency = "UAH",
        )
}
