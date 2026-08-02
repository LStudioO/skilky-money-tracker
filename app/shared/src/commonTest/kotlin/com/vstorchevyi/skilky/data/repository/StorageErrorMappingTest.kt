package com.vstorchevyi.skilky.data.repository

import com.vstorchevyi.skilky.data.local.asStorageResult
import com.vstorchevyi.skilky.data.local.runCatchingStorage
import com.vstorchevyi.skilky.domain.model.AppError
import com.vstorchevyi.skilky.domain.model.Either
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class StorageErrorMappingTest {
    @Test
    fun `successful storage call returns Right`() {
        assertEquals(Either.Right(42), runCatchingStorage { 42 })
    }

    @Test
    fun `storage exception maps to Storage`() {
        val result = runCatchingStorage<Int> { error("disk unavailable") }

        assertEquals(Either.Left(AppError.Storage), result)
    }

    @Test
    fun `storage cancellation propagates`() {
        assertFailsWith<CancellationException> {
            runCatchingStorage<Unit> { throw CancellationException("cancelled") }
        }
    }

    @Test
    fun `storage flow exception emits Storage`() =
        runTest {
            val result =
                flow<Int> { error("database unavailable") }
                    .asStorageResult()
                    .first()

            assertEquals(Either.Left(AppError.Storage), result)
        }
}
