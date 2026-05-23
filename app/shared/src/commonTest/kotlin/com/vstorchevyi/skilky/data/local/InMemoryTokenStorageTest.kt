package com.vstorchevyi.skilky.data.local

import com.vstorchevyi.skilky.domain.model.AuthSession
import com.vstorchevyi.skilky.domain.model.User
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class InMemoryTokenStorageTest {
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

    private fun createSut() = InMemoryTokenStorage()

    private fun anAuthSession() =
        AuthSession(
            accessToken = "access",
            refreshToken = "refresh",
            user =
                User(
                    id = 1,
                    email = "v@example.com",
                    displayName = "V",
                    defaultCurrency = "UAH",
                ),
        )
}
