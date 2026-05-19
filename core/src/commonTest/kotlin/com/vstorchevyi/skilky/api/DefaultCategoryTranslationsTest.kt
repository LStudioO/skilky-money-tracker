package com.vstorchevyi.skilky.api

import kotlin.test.Test
import kotlin.test.assertEquals

class DefaultCategoryTranslationsTest {
    // SUT — stable keys → localized labels for seeded categories.
    private val sut = DefaultCategoryTranslations

    @Test
    fun `uk returns Ukrainian label for food`() {
        // Arrange
        val nameKey = DefaultCategoryKeys.FOOD
        val storedName = "Food"
        val languageTag = "uk"

        // Act
        val result = sut.displayName(nameKey, storedName, languageTag)

        // Assert
        assertEquals("Їжа", result)
    }

    @Test
    fun `unknown language falls back to English`() {
        // Arrange
        val nameKey = DefaultCategoryKeys.FOOD
        val storedName = "Food"
        val languageTag = "de"

        // Act
        val result = sut.displayName(nameKey, storedName, languageTag)

        // Assert
        assertEquals("Food", result)
    }

    @Test
    fun `null nameKey returns stored name`() {
        // Arrange
        val storedName = "Gym"

        // Act
        val result = sut.displayName(nameKey = null, storedName = storedName, languageTag = "uk")

        // Assert
        assertEquals("Gym", result)
    }

    @Test
    fun `normalize parses primary language from Accept-Language`() {
        // Arrange
        val header = "uk-UA,en;q=0.8"

        // Act
        val result = sut.normalizeLanguageTag(header)

        // Assert
        assertEquals("uk", result)
    }

    @Test
    fun `normalize maps en-US primary tag to en`() {
        // Arrange
        val header = "en-US"

        // Act
        val result = sut.normalizeLanguageTag(header)

        // Assert
        assertEquals("en", result)
    }
}
