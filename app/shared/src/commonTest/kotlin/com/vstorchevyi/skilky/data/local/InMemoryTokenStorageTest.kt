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
            val sut = createSut()

            assertNull(sut.read())
        }

    @Test
    fun `read returns the saved session`() =
        runTest {
            val sut = createSut()
            val session = anAuthSession()
            sut.save(session)

            assertEquals(session, sut.read())
        }

    @Test
    fun `clear forgets the saved session`() =
        runTest {
            val sut = createSut()
            sut.save(anAuthSession())

            sut.clear()

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
