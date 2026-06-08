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
import kotlin.test.assertTrue

class ExpenseDaoTest {
    private lateinit var tempDir: File
    private lateinit var database: SkilkyDatabase

    @BeforeTest
    fun setUp() {
        tempDir = Files.createTempDirectory("expense-dao-test").toFile()
        database =
            Room.databaseBuilder<SkilkyDatabase>(
                name = tempDir.resolve("test.db").absolutePath,
            )
                .setDriver(BundledSQLiteDriver())
                .build()
    }

    @AfterTest
    fun tearDown() {
        database.close()
        tempDir.deleteRecursively()
    }

    @Test
    fun `observeAll emits rows sorted by date desc then createdAt desc`() =
        runTest {
            // Arrange
            val sut = createSut()
            sut.upsertAll(
                listOf(
                    anExpenseEntity(id = 1, dateIso = "2026-06-07", createdAtMillis = 100, name = "yesterday"),
                    anExpenseEntity(id = 2, dateIso = "2026-06-08", createdAtMillis = 200, name = "today-later"),
                    anExpenseEntity(id = 3, dateIso = "2026-06-08", createdAtMillis = 100, name = "today-earlier"),
                ),
            )

            // Act
            val read = sut.getAll().first()

            // Assert
            assertEquals(listOf("today-later", "today-earlier", "yesterday"), read.map { it.name })
        }

    @Test
    fun `upsertAll updates an existing row when the same id is inserted again`() =
        runTest {
            // Arrange
            val sut = createSut()
            sut.upsertAll(listOf(anExpenseEntity(id = 1, name = "Old")))

            // Act
            sut.upsertAll(listOf(anExpenseEntity(id = 1, name = "New")))

            // Assert
            val read = sut.getAll().first()
            assertEquals(1, read.size)
            assertEquals("New", read.single().name)
        }

    @Test
    fun `clear removes every row`() =
        runTest {
            // Arrange
            val sut = createSut()
            sut.upsertAll(
                listOf(
                    anExpenseEntity(id = 1, name = "A"),
                    anExpenseEntity(id = 2, name = "B"),
                ),
            )

            // Act
            sut.clear()

            // Assert
            assertTrue(sut.getAll().first().isEmpty())
        }

    private fun createSut(): ExpenseDao = database.expenseDao()

    private fun anExpenseEntity(
        id: Long,
        name: String = "Milk",
        amount: Double = 45.0,
        currency: String = "UAH",
        categoryId: Long = 1,
        categoryName: String = "Food",
        categoryIcon: String = "🍎",
        categoryColor: String = "#FF6B6B",
        note: String? = null,
        inputType: String = "TEXT",
        dateIso: String = "2026-06-08",
        createdAtMillis: Long = 0,
    ): ExpenseEntity =
        ExpenseEntity(
            id = id,
            name = name,
            amount = amount,
            currency = currency,
            categoryId = categoryId,
            categoryName = categoryName,
            categoryIcon = categoryIcon,
            categoryColor = categoryColor,
            note = note,
            inputType = inputType,
            dateIso = dateIso,
            createdAtMillis = createdAtMillis,
        )
}
