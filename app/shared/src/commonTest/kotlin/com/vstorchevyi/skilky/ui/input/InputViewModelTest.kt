package com.vstorchevyi.skilky.ui.input

import com.vstorchevyi.skilky.api.Currency
import com.vstorchevyi.skilky.api.InputType
import com.vstorchevyi.skilky.api.ParsedExpenseItem
import com.vstorchevyi.skilky.domain.model.AppError
import com.vstorchevyi.skilky.domain.model.Category
import com.vstorchevyi.skilky.domain.model.Either
import com.vstorchevyi.skilky.domain.model.ExpenseInput
import com.vstorchevyi.skilky.domain.repository.FakeCategoryRepository
import com.vstorchevyi.skilky.domain.repository.FakeExpenseRepository
import com.vstorchevyi.skilky.domain.repository.FakeParseRepository
import com.vstorchevyi.skilky.domain.usecase.CreateExpensesUseCase
import com.vstorchevyi.skilky.domain.usecase.GetCategoriesUseCase
import com.vstorchevyi.skilky.domain.usecase.ParseAudioUseCase
import com.vstorchevyi.skilky.domain.usecase.ParseReceiptUseCase
import com.vstorchevyi.skilky.domain.usecase.ParseTextUseCase
import com.vstorchevyi.skilky.domain.usecase.RefreshCategoriesUseCase
import com.vstorchevyi.skilky.support.runTestWithMain
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class InputViewModelTest {
    @Test
    fun `category cache read failure emits ShowError`() =
        runTestWithMain {
            val categories = FakeCategoryRepository().apply { setReadError(AppError.Storage) }

            val sut = createSut(categories = categories)
            advanceUntilIdle()

            assertEquals(InputEvent.ShowError(AppError.Storage), sut.events.first())
        }

    @Test
    fun `blank query does not start parsing`() =
        runTestWithMain {
            // Arrange
            val parser = FakeParseRepository()
            val sut = createSut(parser = parser)
            advanceUntilIdle()

            // Act
            sut.onQueryChange("   ")
            sut.onSubmit()
            advanceUntilIdle()

            // Assert
            assertTrue(parser.calls.isEmpty())
            assertFalse(sut.state.value.isParsing)
        }

    @Test
    fun `successful parse opens preview and resolves category suggestions`() =
        runTestWithMain {
            // Arrange
            val parser =
                FakeParseRepository().apply {
                    result =
                        Either.Right(
                            listOf(
                                parsed(name = "Milk", amount = 45.0, categoryId = 7, categoryName = "Food"),
                                parsed(name = "Taxi", amount = 120.5, categoryName = "transport"),
                            ),
                        )
                }
            val categories =
                FakeCategoryRepository(
                    initial = listOf(category(7, "Food"), category(8, "Transport")),
                )
            val sut = createSut(parser = parser, categories = categories)
            advanceUntilIdle()

            // Act
            sut.onQueryChange("milk 45, taxi 120.5")
            sut.onSubmit()
            advanceUntilIdle()

            // Assert
            assertEquals(listOf(FakeParseRepository.Call("milk 45, taxi 120.5", Currency.UAH)), parser.calls)
            val items = requireNotNull(sut.state.value.previewItems)
            assertEquals(2, items.size)
            assertEquals(7, items[0].categoryId)
            assertEquals(8, items[1].categoryId)
            assertEquals("120.5", items[1].amountText)
            assertEquals(LocalDate(2026, 6, 8), items[0].date)
        }

    @Test
    fun `empty parse keeps query and shows retryable error`() =
        runTestWithMain {
            // Arrange
            val sut = createSut()
            advanceUntilIdle()

            // Act
            sut.onQueryChange("something unclear")
            sut.onSubmit()
            advanceUntilIdle()

            // Assert
            assertEquals("something unclear", sut.state.value.query)
            assertEquals(InputError.NoItems, sut.state.value.parseError)
            assertNull(sut.state.value.previewItems)
        }

    @Test
    fun `category suggestions resolve when categories arrive after parsing`() =
        runTestWithMain {
            // Arrange
            val parser =
                FakeParseRepository().apply {
                    result = Either.Right(listOf(parsed("Milk", 45.0, categoryId = 7)))
                }
            val categories = FakeCategoryRepository()
            val sut = createSut(parser = parser, categories = categories)
            advanceUntilIdle()
            sut.onQueryChange("milk 45")
            sut.onSubmit()
            advanceUntilIdle()
            assertNull(requireNotNull(sut.state.value.previewItems).single().categoryId)

            // Act
            categories.setCategories(listOf(category(7, "Food")))
            advanceUntilIdle()

            // Assert
            assertEquals(7, requireNotNull(sut.state.value.previewItems).single().categoryId)
        }

    @Test
    fun `parse failure keeps query and exposes mapped error`() =
        runTestWithMain {
            // Arrange
            val parser = FakeParseRepository().apply { result = Either.Left(AppError.Network) }
            val sut = createSut(parser = parser)
            advanceUntilIdle()

            // Act
            sut.onQueryChange("milk 45")
            sut.onSubmit()
            advanceUntilIdle()

            // Assert
            assertEquals("milk 45", sut.state.value.query)
            assertEquals(InputError.Request(AppError.Network), sut.state.value.parseError)
            assertFalse(sut.state.value.isParsing)
        }

    @Test
    fun `receipt parse opens image preview`() =
        runTestWithMain {
            // Arrange
            val parser =
                FakeParseRepository().apply {
                    receiptResult = Either.Right(listOf(parsed("Milk", 45.0, categoryId = 7)))
                }
            val categories = FakeCategoryRepository(initial = listOf(category(7, "Food")))
            val sut = createSut(parser = parser, categories = categories)
            val image = jpegBytes()
            advanceUntilIdle()

            // Act
            sut.onReceiptSelected(image)
            advanceUntilIdle()

            // Assert
            assertEquals(1, parser.receiptCalls.size)
            assertTrue(parser.receiptCalls.single().bytes.contentEquals(image))
            assertEquals(Currency.UAH, parser.receiptCalls.single().currency)
            assertEquals(InputType.IMAGE, sut.state.value.previewInputType)
            assertEquals("Milk", requireNotNull(sut.state.value.previewItems).single().name)
        }

    @Test
    fun `audio parse opens voice preview`() =
        runTestWithMain {
            // Arrange
            val parser =
                FakeParseRepository().apply {
                    audioResult = Either.Right(listOf(parsed("Taxi", 120.0, categoryId = 8)))
                }
            val categories = FakeCategoryRepository(initial = listOf(category(8, "Transport")))
            val sut = createSut(parser = parser, categories = categories)
            val audio = wavBytes()
            advanceUntilIdle()

            // Act
            sut.onAudioRecorded(audio)
            advanceUntilIdle()

            // Assert
            assertEquals(1, parser.audioCalls.size)
            assertTrue(parser.audioCalls.single().bytes.contentEquals(audio))
            assertEquals(Currency.UAH, parser.audioCalls.single().currency)
            assertEquals(InputType.AUDIO, sut.state.value.previewInputType)
            assertEquals("Taxi", requireNotNull(sut.state.value.previewItems).single().name)
        }

    @Test
    fun `audio preview saves audio expenses`() =
        runTestWithMain {
            // Arrange
            val parser =
                FakeParseRepository().apply {
                    audioResult = Either.Right(listOf(parsed("Taxi", 120.0, categoryId = 8)))
                }
            val categories = FakeCategoryRepository(initial = listOf(category(8, "Transport")))
            val expenses =
                FakeExpenseRepository().apply {
                    createAllResult = Either.Right(listOf(FakeExpenseRepository.defaultExpense(id = 1)))
                }
            val sut = createSut(parser = parser, categories = categories, expenses = expenses)
            advanceUntilIdle()
            sut.onAudioRecorded(wavBytes())
            advanceUntilIdle()

            // Act
            sut.onSaveAll()
            advanceUntilIdle()

            // Assert
            val call = assertIs<FakeExpenseRepository.Call.CreateAll>(expenses.calls.last())
            assertEquals(InputType.AUDIO, call.inputs.single().inputType)
            assertEquals(InputEvent.Saved(1), sut.events.first())
        }

    @Test
    fun `unsupported audio is rejected before parsing`() =
        runTestWithMain {
            // Arrange
            val parser = FakeParseRepository()
            val sut = createSut(parser = parser)
            advanceUntilIdle()

            // Act
            sut.onAudioRecorded("not audio".encodeToByteArray())
            advanceUntilIdle()

            // Assert
            assertTrue(parser.audioCalls.isEmpty())
            assertEquals(InputError.UnsupportedAudio, sut.state.value.parseError)
        }

    @Test
    fun `oversized audio is rejected before parsing`() =
        runTestWithMain {
            // Arrange
            val parser = FakeParseRepository()
            val sut = createSut(parser = parser)
            advanceUntilIdle()

            // Act
            sut.onAudioRecorded(ByteArray(10 * 1024 * 1024 + 1))
            advanceUntilIdle()

            // Assert
            assertTrue(parser.audioCalls.isEmpty())
            assertEquals(InputError.AudioTooLarge, sut.state.value.parseError)
        }

    @Test
    fun `audio parse failure exposes mapped error`() =
        runTestWithMain {
            // Arrange
            val parser = FakeParseRepository().apply { audioResult = Either.Left(AppError.Network) }
            val sut = createSut(parser = parser)
            advanceUntilIdle()

            // Act
            sut.onAudioRecorded(wavBytes())
            advanceUntilIdle()

            // Assert
            assertEquals(InputError.Request(AppError.Network), sut.state.value.parseError)
            assertFalse(sut.state.value.isParsing)
        }

    @Test
    fun `receipt preview saves image expenses`() =
        runTestWithMain {
            // Arrange
            val parser =
                FakeParseRepository().apply {
                    receiptResult = Either.Right(listOf(parsed("Milk", 45.0, categoryId = 7)))
                }
            val categories = FakeCategoryRepository(initial = listOf(category(7, "Food")))
            val expenses =
                FakeExpenseRepository().apply {
                    createAllResult = Either.Right(listOf(FakeExpenseRepository.defaultExpense(id = 1)))
                }
            val sut = createSut(parser = parser, categories = categories, expenses = expenses)
            advanceUntilIdle()
            sut.onReceiptSelected(jpegBytes())
            advanceUntilIdle()

            // Act
            sut.onSaveAll()
            advanceUntilIdle()

            // Assert
            val call = assertIs<FakeExpenseRepository.Call.CreateAll>(expenses.calls.last())
            assertEquals(InputType.IMAGE, call.inputs.single().inputType)
            assertEquals(InputEvent.Saved(1), sut.events.first())
        }

    @Test
    fun `unsupported receipt image is rejected before parsing`() =
        runTestWithMain {
            // Arrange
            val parser = FakeParseRepository()
            val sut = createSut(parser = parser)
            advanceUntilIdle()

            // Act
            sut.onReceiptSelected("not an image".encodeToByteArray())
            advanceUntilIdle()

            // Assert
            assertTrue(parser.receiptCalls.isEmpty())
            assertEquals(InputError.UnsupportedImage, sut.state.value.parseError)
        }

    @Test
    fun `oversized receipt image is rejected before parsing`() =
        runTestWithMain {
            // Arrange
            val parser = FakeParseRepository()
            val sut = createSut(parser = parser)
            advanceUntilIdle()

            // Act
            sut.onReceiptSelected(ByteArray(10 * 1024 * 1024 + 1))
            advanceUntilIdle()

            // Assert
            assertTrue(parser.receiptCalls.isEmpty())
            assertEquals(InputError.ImageTooLarge, sut.state.value.parseError)
        }

    @Test
    fun `receipt parse failure exposes mapped error`() =
        runTestWithMain {
            // Arrange
            val parser = FakeParseRepository().apply { receiptResult = Either.Left(AppError.Network) }
            val sut = createSut(parser = parser)
            advanceUntilIdle()

            // Act
            sut.onReceiptSelected(jpegBytes())
            advanceUntilIdle()

            // Assert
            assertEquals(InputError.Request(AppError.Network), sut.state.value.parseError)
            assertFalse(sut.state.value.isParsing)
        }

    @Test
    fun `preview items can be edited added and removed`() =
        runTestWithMain {
            // Arrange
            val parser =
                FakeParseRepository().apply {
                    result = Either.Right(listOf(parsed("Milk", 45.0, categoryId = 7)))
                }
            val categories = FakeCategoryRepository(initial = listOf(category(7, "Food")))
            val sut = createSut(parser = parser, categories = categories)
            advanceUntilIdle()
            sut.onQueryChange("milk 45")
            sut.onSubmit()
            advanceUntilIdle()
            val parsedId = requireNotNull(sut.state.value.previewItems).single().id

            // Act
            sut.onEditItem(parsedId)
            sut.onNameChange(parsedId, "Oat milk")
            sut.onAmountChange(parsedId, "49,50")
            sut.onDoneEditing(parsedId)
            sut.onAddItem()
            val addedId = requireNotNull(sut.state.value.previewItems).last().id
            sut.onDeleteItem(addedId)

            // Assert
            val item = requireNotNull(sut.state.value.previewItems).single()
            assertEquals("Oat milk", item.name)
            assertEquals(49.5, item.parsedAmount)
            assertFalse(item.isEditing)
        }

    @Test
    fun `Save All creates one batch and closes preview`() =
        runTestWithMain {
            // Arrange
            val parser =
                FakeParseRepository().apply {
                    result =
                        Either.Right(
                            listOf(
                                parsed("Milk", 45.0, categoryId = 7),
                                parsed("Bread", 22.0, categoryId = 7),
                            ),
                        )
                }
            val categories = FakeCategoryRepository(initial = listOf(category(7, "Food")))
            val expenses =
                FakeExpenseRepository().apply {
                    createAllResult =
                        Either.Right(
                            listOf(
                                FakeExpenseRepository.defaultExpense(id = 1),
                                FakeExpenseRepository.defaultExpense(id = 2),
                            ),
                        )
                }
            val sut = createSut(parser = parser, categories = categories, expenses = expenses)
            advanceUntilIdle()
            sut.onQueryChange("milk 45, bread 22")
            sut.onSubmit()
            advanceUntilIdle()

            // Act
            sut.onSaveAll()
            advanceUntilIdle()

            // Assert
            val call = assertIs<FakeExpenseRepository.Call.CreateAll>(expenses.calls.last())
            assertEquals(
                listOf(
                    input("Milk", 45.0),
                    input("Bread", 22.0),
                ),
                call.inputs,
            )
            assertEquals(InputEvent.Saved(2), sut.events.first())
            assertEquals("", sut.state.value.query)
            assertNull(sut.state.value.previewItems)
        }

    @Test
    fun `save failure leaves reviewed items open for retry`() =
        runTestWithMain {
            // Arrange
            val parser =
                FakeParseRepository().apply {
                    result = Either.Right(listOf(parsed("Milk", 45.0, categoryId = 7)))
                }
            val categories = FakeCategoryRepository(initial = listOf(category(7, "Food")))
            val expenses =
                FakeExpenseRepository().apply {
                    createAllResult = Either.Left(AppError.Network)
                }
            val sut = createSut(parser = parser, categories = categories, expenses = expenses)
            advanceUntilIdle()
            sut.onQueryChange("milk 45")
            sut.onSubmit()
            advanceUntilIdle()

            // Act
            sut.onSaveAll()
            advanceUntilIdle()

            // Assert
            assertEquals(AppError.Network, sut.state.value.saveError)
            assertEquals(1, requireNotNull(sut.state.value.previewItems).size)
            assertFalse(sut.state.value.isSaving)
        }

    private fun createSut(
        parser: FakeParseRepository = FakeParseRepository(),
        categories: FakeCategoryRepository = FakeCategoryRepository(),
        expenses: FakeExpenseRepository = FakeExpenseRepository(),
    ): InputViewModel =
        InputViewModel(
            parseText = ParseTextUseCase(parser),
            parseAudio = ParseAudioUseCase(parser),
            parseReceipt = ParseReceiptUseCase(parser),
            getCategories = GetCategoriesUseCase(categories),
            refreshCategories = RefreshCategoriesUseCase(categories),
            createExpenses = CreateExpensesUseCase(expenses),
            clock = FixedClock(LocalDate(2026, 6, 8)),
            timeZone = TimeZone.UTC,
        )

    private fun jpegBytes(): ByteArray = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0, 0, 0, 0, 0)

    private fun wavBytes(): ByteArray =
        byteArrayOf(
            0x52,
            0x49,
            0x46,
            0x46,
            0,
            0,
            0,
            0,
            0x57,
            0x41,
            0x56,
            0x45,
        )

    private fun parsed(
        name: String,
        amount: Double,
        categoryId: Long? = null,
        categoryName: String? = null,
    ): ParsedExpenseItem =
        ParsedExpenseItem(
            name = name,
            amount = amount,
            currency = Currency.UAH,
            suggestedCategoryId = categoryId,
            suggestedCategoryName = categoryName,
        )

    private fun category(
        id: Long,
        name: String,
    ): Category = Category(id, name, "#", "#112233", isDefault = false)

    private fun input(
        name: String,
        amount: Double,
    ): ExpenseInput =
        ExpenseInput(
            name = name,
            amount = amount,
            currency = Currency.UAH,
            categoryId = 7,
            note = null,
            date = LocalDate(2026, 6, 8),
        )

    private class FixedClock(
        date: LocalDate,
    ) : Clock {
        private val pinned = Instant.fromEpochSeconds(date.toEpochDays() * SECONDS_PER_DAY)

        override fun now(): Instant = pinned

        private companion object {
            const val SECONDS_PER_DAY: Long = 86_400
        }
    }
}
