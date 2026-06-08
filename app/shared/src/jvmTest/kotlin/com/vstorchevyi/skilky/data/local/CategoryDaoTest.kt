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

class CategoryDaoTest {
    private lateinit var tempDir: File
    private lateinit var database: SkilkyDatabase

    @BeforeTest
    fun setUp() {
        tempDir = Files.createTempDirectory("category-dao-test").toFile()
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
    fun `observeAll emits the inserted categories sorted defaults-first then by name`() =
        runTest {
            // Arrange
            val sut = createSut()
            sut.upsertAll(
                listOf(
                    aCategoryEntity(id = 1, name = "Zebra", isDefault = false),
                    aCategoryEntity(id = 2, name = "alpha", isDefault = false),
                    aCategoryEntity(id = 3, name = "Bills", isDefault = true),
                ),
            )

            // Act
            val read = sut.observeAll().first()

            // Assert: defaults first (Bills), then non-defaults alphabetically.
            assertEquals(listOf("Bills", "alpha", "Zebra"), read.map { it.name })
        }

    @Test
    fun `upsertAll updates an existing row when the same id is inserted again`() =
        runTest {
            // Arrange
            val sut = createSut()
            sut.upsertAll(listOf(aCategoryEntity(id = 1, name = "Old name")))

            // Act
            sut.upsertAll(listOf(aCategoryEntity(id = 1, name = "New name")))

            // Assert
            val read = sut.observeAll().first()
            assertEquals(1, read.size)
            assertEquals("New name", read.single().name)
        }

    @Test
    fun `deleteById removes only the matching row`() =
        runTest {
            // Arrange
            val sut = createSut()
            sut.upsertAll(
                listOf(
                    aCategoryEntity(id = 1, name = "Keep"),
                    aCategoryEntity(id = 2, name = "Drop"),
                ),
            )

            // Act
            sut.deleteById(2)

            // Assert
            val remaining = sut.observeAll().first()
            assertEquals(listOf("Keep"), remaining.map { it.name })
        }

    @Test
    fun `clear removes every row`() =
        runTest {
            // Arrange
            val sut = createSut()
            sut.upsertAll(
                listOf(
                    aCategoryEntity(id = 1, name = "A"),
                    aCategoryEntity(id = 2, name = "B"),
                ),
            )

            // Act
            sut.clear()

            // Assert
            assertTrue(sut.observeAll().first().isEmpty())
        }

    private fun createSut(): CategoryDao = database.categoryDao()

    private fun aCategoryEntity(
        id: Long,
        name: String,
        icon: String = "📁",
        color: String = "#888888",
        isDefault: Boolean = false,
        nameKey: String? = null,
        updatedAt: Long = 0L,
    ): CategoryEntity =
        CategoryEntity(
            id = id,
            name = name,
            icon = icon,
            color = color,
            isDefault = isDefault,
            nameKey = nameKey,
            updatedAt = updatedAt,
        )
}
