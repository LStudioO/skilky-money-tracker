package com.vstorchevyi.skilky.domain.model

import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class EitherTest {
    @Test
    fun `catch returns Right for a successful block`() {
        // Act
        val result = Either.catch { 42 }

        // Assert
        assertEquals(Either.Right(42), result)
    }

    @Test
    fun `catch wraps a thrown Exception as Left`() {
        // Arrange
        val boom = IllegalStateException("boom")

        // Act
        val result = Either.catch<Unit> { throw boom }

        // Assert
        assertTrue(result is Either.Left)
        assertSame(boom, (result as Either.Left).value)
    }

    @Test
    fun `catch rethrows CancellationException so coroutine cancellation propagates`() {
        // Act + Assert
        assertFailsWith<CancellationException> {
            Either.catch { throw CancellationException("cancelled") }
        }
    }

    @Test
    fun `catch rethrows a terminal Error so the process can crash cleanly`() {
        // Act + Assert
        assertFailsWith<Error> {
            Either.catch<Unit> { throw Error("fatal") }
        }
    }

    @Test
    fun `leftOrNull returns the Left value`() {
        // Arrange
        val left: Either<String, Int> = Either.Left("oops")

        // Act + Assert
        assertEquals("oops", left.leftOrNull())
    }

    @Test
    fun `leftOrNull returns null when Right`() {
        // Arrange
        val right: Either<String, Int> = Either.Right(1)

        // Act + Assert
        assertNull(right.leftOrNull())
    }

    @Test
    fun `ifRight runs the block when Right and returns the same Either`() {
        // Arrange
        var captured: Int? = null
        val original: Either<String, Int> = Either.Right(7)

        // Act
        val returned = original.ifRight { captured = it }

        // Assert
        assertEquals(7, captured)
        assertSame(original, returned)
    }

    @Test
    fun `ifRight does not run the block when Left`() {
        // Arrange
        var ran = false
        val left: Either<String, Int> = Either.Left("err")

        // Act
        left.ifRight { ran = true }

        // Assert
        assertEquals(false, ran)
    }

    @Test
    fun `ifLeft runs the block when Left and returns the same Either`() {
        // Arrange
        var captured: String? = null
        val original: Either<String, Int> = Either.Left("err")

        // Act
        val returned = original.ifLeft { captured = it }

        // Assert
        assertEquals("err", captured)
        assertSame(original, returned)
    }

    @Test
    fun `ifLeft does not run the block when Right`() {
        // Arrange
        var ran = false
        val right: Either<String, Int> = Either.Right(1)

        // Act
        right.ifLeft { ran = true }

        // Assert
        assertEquals(false, ran)
    }
}
