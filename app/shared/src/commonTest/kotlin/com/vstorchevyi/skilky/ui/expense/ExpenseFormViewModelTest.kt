package com.vstorchevyi.skilky.ui.expense

import com.vstorchevyi.skilky.api.Currency
import com.vstorchevyi.skilky.domain.model.AppError
import com.vstorchevyi.skilky.domain.model.Category
import com.vstorchevyi.skilky.domain.model.Either
import com.vstorchevyi.skilky.domain.repository.FakeCategoryRepository
import com.vstorchevyi.skilky.domain.repository.FakeExpenseRepository
import com.vstorchevyi.skilky.domain.usecase.CreateExpenseUseCase
import com.vstorchevyi.skilky.domain.usecase.DeleteExpenseUseCase
import com.vstorchevyi.skilky.domain.usecase.GetCategoriesUseCase
import com.vstorchevyi.skilky.domain.usecase.GetExpenseUseCase
import com.vstorchevyi.skilky.domain.usecase.RefreshCategoriesUseCase
import com.vstorchevyi.skilky.domain.usecase.UpdateExpenseUseCase
import com.vstorchevyi.skilky.support.runTestWithMain
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class ExpenseFormViewModelTest {
    @Test
    fun `new mode defaults the draft to today and the first category`() =
        runTestWithMain {
            // Arrange
            val categories = FakeCategoryRepository(initial = listOf(aCategory(id = 1)))

            // Act
            val sut = createSut(categoriesRepo = categories, expenseId = null)
            advanceUntilIdle()

            // Assert
            val state = sut.state.value
            assertEquals(ExpenseFormUiState.Mode.New, state.mode)
            assertEquals(false, state.isLoading)
            assertEquals(LocalDate(2026, 6, 8), state.draft.date)
            assertEquals(1L, state.draft.categoryId)
        }

    @Test
    fun `edit mode loads the draft from the cached expense`() =
        runTestWithMain {
            // Arrange
            val existing =
                FakeExpenseRepository.defaultExpense(
                    id = 42,
                    name = "Milk",
                    amount = 45.0,
                    categoryId = 1,
                    date = LocalDate(2026, 6, 7),
                )
            val expenses = FakeExpenseRepository(initial = listOf(existing))
            val categories = FakeCategoryRepository(initial = listOf(aCategory(id = 1)))

            // Act
            val sut = createSut(expensesRepo = expenses, categoriesRepo = categories, expenseId = 42)
            advanceUntilIdle()

            // Assert
            val draft = sut.state.value.draft
            assertEquals("Milk", draft.name)
            assertEquals("45", draft.amountText)
            assertEquals(1L, draft.categoryId)
            assertEquals(LocalDate(2026, 6, 7), draft.date)
            assertEquals(false, sut.state.value.isLoading)
        }

    @Test
    fun `edit mode flags notFound when the id is not in the cache`() =
        runTestWithMain {
            // Arrange
            val sut = createSut(expenseId = 999)

            // Act
            advanceUntilIdle()

            // Assert
            assertEquals(true, sut.state.value.notFound)
            assertEquals(false, sut.state.value.isLoading)
        }

    @Test
    fun `onSave in new mode calls create with parsed input`() =
        runTestWithMain {
            // Arrange
            val expenses =
                FakeExpenseRepository().apply {
                    createResult = Either.Right(FakeExpenseRepository.defaultExpense(id = 99))
                }
            val categories = FakeCategoryRepository(initial = listOf(aCategory(id = 1)))
            val sut = createSut(expensesRepo = expenses, categoriesRepo = categories, expenseId = null)
            advanceUntilIdle()
            sut.onNameChange("  Coffee  ")
            sut.onAmountChange("3,50")
            sut.onCurrencyChange(Currency.USD)
            sut.onNoteChange("  morning ")

            // Act
            sut.onSave()
            advanceUntilIdle()

            // Assert
            val call = expenses.calls.last() as FakeExpenseRepository.Call.Create
            assertEquals("Coffee", call.input.name)
            assertEquals(3.5, call.input.amount)
            assertEquals(Currency.USD, call.input.currency)
            assertEquals("morning", call.input.note)
            assertEquals(ExpenseFormEvent.Saved, sut.events.first())
        }

    @Test
    fun `onSave with a blank name does not call the repository`() =
        runTestWithMain {
            // Arrange
            val expenses = FakeExpenseRepository()
            val categories = FakeCategoryRepository(initial = listOf(aCategory(id = 1)))
            val sut = createSut(expensesRepo = expenses, categoriesRepo = categories, expenseId = null)
            advanceUntilIdle()
            sut.onAmountChange("10")

            // Act
            sut.onSave()
            advanceUntilIdle()

            // Assert
            assertTrue(expenses.calls.none { it is FakeExpenseRepository.Call.Create })
        }

    @Test
    fun `onSave in edit mode calls update with the row id`() =
        runTestWithMain {
            // Arrange
            val existing = FakeExpenseRepository.defaultExpense(id = 7, name = "Old", amount = 1.0)
            val expenses =
                FakeExpenseRepository(initial = listOf(existing)).apply {
                    updateResult = Either.Right(existing.copy(name = "New", amount = 2.0))
                }
            val categories = FakeCategoryRepository(initial = listOf(aCategory(id = 1)))
            val sut = createSut(expensesRepo = expenses, categoriesRepo = categories, expenseId = 7)
            advanceUntilIdle()
            sut.onNameChange("New")
            sut.onAmountChange("2")

            // Act
            sut.onSave()
            advanceUntilIdle()

            // Assert
            val call = expenses.calls.last() as FakeExpenseRepository.Call.Update
            assertEquals(7L, call.id)
            assertEquals("New", call.input.name)
            assertEquals(2.0, call.input.amount)
            assertEquals(ExpenseFormEvent.Saved, sut.events.first())
        }

    @Test
    fun `save failure emits ShowError and clears the saving flag`() =
        runTestWithMain {
            // Arrange
            val expenses =
                FakeExpenseRepository().apply { createResult = Either.Left(AppError.Network) }
            val categories = FakeCategoryRepository(initial = listOf(aCategory(id = 1)))
            val sut = createSut(expensesRepo = expenses, categoriesRepo = categories, expenseId = null)
            advanceUntilIdle()
            sut.onNameChange("Lunch")
            sut.onAmountChange("12")

            // Act
            sut.onSave()
            advanceUntilIdle()

            // Assert
            assertEquals(ExpenseFormEvent.ShowError(AppError.Network), sut.events.first())
            assertEquals(false, sut.state.value.isSaving)
        }

    @Test
    fun `onDelete in edit mode calls delete and emits Deleted`() =
        runTestWithMain {
            // Arrange
            val existing = FakeExpenseRepository.defaultExpense(id = 11)
            val expenses = FakeExpenseRepository(initial = listOf(existing))
            val categories = FakeCategoryRepository(initial = listOf(aCategory(id = 1)))
            val sut = createSut(expensesRepo = expenses, categoriesRepo = categories, expenseId = 11)
            advanceUntilIdle()

            // Act
            sut.onDelete()
            advanceUntilIdle()

            // Assert
            assertTrue(expenses.calls.any { it is FakeExpenseRepository.Call.Delete && it.id == 11L })
            assertEquals(ExpenseFormEvent.Deleted, sut.events.first())
        }

    @Test
    fun `onDelete in new mode is a no-op`() =
        runTestWithMain {
            // Arrange
            val expenses = FakeExpenseRepository()
            val categories = FakeCategoryRepository(initial = listOf(aCategory(id = 1)))
            val sut = createSut(expensesRepo = expenses, categoriesRepo = categories, expenseId = null)
            advanceUntilIdle()

            // Act
            sut.onDelete()
            advanceUntilIdle()

            // Assert
            assertTrue(expenses.calls.none { it is FakeExpenseRepository.Call.Delete })
        }

    @Test
    fun `category picker selects the first item when none was chosen yet`() =
        runTestWithMain {
            // Arrange
            val categories =
                FakeCategoryRepository(
                    initial = listOf(aCategory(id = 5), aCategory(id = 6)),
                )

            // Act
            val sut = createSut(categoriesRepo = categories, expenseId = null)
            advanceUntilIdle()

            // Assert
            assertEquals(5L, sut.state.value.draft.categoryId)
        }

    @Test
    fun `category picker leaves an existing selection alone`() =
        runTestWithMain {
            // Arrange
            val existing =
                FakeExpenseRepository.defaultExpense(id = 1, categoryId = 6)
            val expenses = FakeExpenseRepository(initial = listOf(existing))
            val categories =
                FakeCategoryRepository(
                    initial = listOf(aCategory(id = 5), aCategory(id = 6)),
                )

            // Act
            val sut = createSut(expensesRepo = expenses, categoriesRepo = categories, expenseId = 1)
            advanceUntilIdle()

            // Assert
            assertEquals(6L, sut.state.value.draft.categoryId)
            assertEquals(false, sut.state.value.notFound)
        }

    private fun createSut(
        expensesRepo: FakeExpenseRepository = FakeExpenseRepository(),
        categoriesRepo: FakeCategoryRepository = FakeCategoryRepository(),
        expenseId: Long? = null,
    ): ExpenseFormViewModel =
        ExpenseFormViewModel(
            expenseId = expenseId,
            getExpense = GetExpenseUseCase(expensesRepo),
            getCategories = GetCategoriesUseCase(categoriesRepo),
            refreshCategories = RefreshCategoriesUseCase(categoriesRepo),
            createExpense = CreateExpenseUseCase(expensesRepo),
            updateExpense = UpdateExpenseUseCase(expensesRepo),
            deleteExpense = DeleteExpenseUseCase(expensesRepo),
            clock = FixedClock(LocalDate(2026, 6, 8)),
            timeZone = TimeZone.UTC,
        )

    private fun aCategory(id: Long): Category = Category(id = id, name = "Cat $id", icon = "🍎", color = "#FF6B6B", isDefault = false)

    private class FixedClock(
        date: LocalDate,
    ) : Clock {
        private val pinned: Instant =
            Instant.fromEpochSeconds(date.toEpochDays() * SECONDS_PER_DAY)

        override fun now(): Instant = pinned

        private companion object {
            const val SECONDS_PER_DAY: Long = 86_400
        }
    }
}
