package com.vstorchevyi.skilky.data.local

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SyncQueueDaoTest {
    private lateinit var tempDir: File
    private lateinit var database: SkilkyDatabase
    private lateinit var sut: SyncQueueDao

    @BeforeTest
    fun setUp() {
        tempDir = Files.createTempDirectory("sync-queue-dao-test").toFile()
        database =
            Room.databaseBuilder<SkilkyDatabase>(tempDir.resolve("queue.db").absolutePath)
                .setDriver(BundledSQLiteDriver())
                .build()
        sut = database.syncQueueDao()
    }

    @AfterTest
    fun tearDown() {
        database.close()
        tempDir.deleteRecursively()
    }

    @Test
    fun `pending items are returned FIFO`() =
        runTest {
            sut.enqueue(item(clientId = "later", createdAtMillis = 20))
            sut.enqueue(item(clientId = "first", createdAtMillis = 10))

            assertEquals(listOf("first", "later"), sut.getPending(limit = 10).map { it.clientId })
        }

    @Test
    fun `processing items are excluded until returned to pending`() =
        runTest {
            val id = sut.enqueue(item(clientId = "expense"))

            sut.markProcessing(id)
            assertEquals(emptyList(), sut.getPending(limit = 10))

            sut.markPending(id)
            assertEquals(listOf(id), sut.getPending(limit = 10).map { it.id })
        }

    @Test
    fun `failure increments retries and can mark item failed`() =
        runTest {
            val id = sut.enqueue(item(clientId = "expense"))

            sut.recordFailure(id, SyncQueueStatus.PENDING.name)
            sut.recordFailure(id, SyncQueueStatus.FAILED.name)

            val queued = sut.observeAll().first().single()
            assertEquals(2, queued.retryCount)
            assertEquals(SyncQueueStatus.FAILED.name, queued.status)
        }

    @Test
    fun `processed item can be removed`() =
        runTest {
            val id = sut.enqueue(item(clientId = "expense"))

            sut.deleteById(id)

            assertEquals(emptyList(), sut.observeAll().first())
        }

    private fun item(
        clientId: String,
        createdAtMillis: Long = 1,
    ) = SyncQueueEntity(
        clientId = clientId,
        localExpenseId = -createdAtMillis,
        name = "Milk",
        amount = 45.0,
        currency = "UAH",
        categoryId = 1,
        note = null,
        inputType = "TEXT",
        dateIso = "2026-08-14",
        createdAtMillis = createdAtMillis,
    )
}
