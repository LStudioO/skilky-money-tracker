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
        val result = Either.catch { 42 }

        assertEquals(Either.Right(42), result)
    }

    @Test
    fun `catch wraps a thrown Exception as Left`() {
        val boom = IllegalStateException("boom")

        val result = Either.catch { throw boom }

        assertTrue(result is Either.Left)
        assertSame(boom, (result as Either.Left).value)
    }

    @Test
    fun `catch rethrows CancellationException so coroutine cancellation propagates`() {
        assertFailsWith<CancellationException> {
            Either.catch { throw CancellationException("cancelled") }
        }
    }

    @Test
    fun `catch rethrows a terminal Error so the process can crash cleanly`() {
        assertFailsWith<Error> {
            Either.catch<Unit> { throw Error("fatal") }
        }
    }

    @Test
    fun `leftOrNull returns the Left value`() {
        val left: Either<String, Int> = Either.Left("oops")

        assertEquals("oops", left.leftOrNull())
    }

    @Test
    fun `leftOrNull returns null when Right`() {
        val right: Either<String, Int> = Either.Right(1)

        assertNull(right.leftOrNull())
    }

    @Test
    fun `ifRight runs the block when Right and returns the same Either`() {
        var captured: Int? = null
        val original: Either<String, Int> = Either.Right(7)

        val returned = original.ifRight { captured = it }

        assertEquals(7, captured)
        assertSame(original, returned)
    }

    @Test
    fun `ifRight does not run the block when Left`() {
        var ran = false

        val left: Either<String, Int> = Either.Left("err")
        left.ifRight { ran = true }

        assertEquals(false, ran)
    }

    @Test
    fun `ifLeft runs the block when Left and returns the same Either`() {
        var captured: String? = null
        val original: Either<String, Int> = Either.Left("err")

        val returned = original.ifLeft { captured = it }

        assertEquals("err", captured)
        assertSame(original, returned)
    }

    @Test
    fun `ifLeft does not run the block when Right`() {
        var ran = false

        val right: Either<String, Int> = Either.Right(1)
        right.ifLeft { ran = true }

        assertEquals(false, ran)
    }
}
