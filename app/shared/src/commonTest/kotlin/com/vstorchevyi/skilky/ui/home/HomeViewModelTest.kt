package com.vstorchevyi.skilky.ui.home

import com.vstorchevyi.skilky.api.Currency
import com.vstorchevyi.skilky.api.InputType
import com.vstorchevyi.skilky.domain.model.AppError
import com.vstorchevyi.skilky.domain.model.Either
import com.vstorchevyi.skilky.domain.model.Expense
import com.vstorchevyi.skilky.domain.model.ExpenseCategorySnapshot
import com.vstorchevyi.skilky.domain.repository.FakeAuthRepository
import com.vstorchevyi.skilky.domain.repository.FakeExpenseRepository
import com.vstorchevyi.skilky.domain.usecase.DeletePendingExpenseUseCase
import com.vstorchevyi.skilky.domain.usecase.GetCurrentSessionUseCase
import com.vstorchevyi.skilky.domain.usecase.GetExpensesUseCase
import com.vstorchevyi.skilky.domain.usecase.LogoutUseCase
import com.vstorchevyi.skilky.domain.usecase.RefreshExpensesUseCase
import com.vstorchevyi.skilky.domain.usecase.RetryPendingExpenseUseCase
import com.vstorchevyi.skilky.support.runTestWithMain
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    @Test
    fun `state reflects the persisted session at init`() =
        runTestWithMain {
            // Arrange
            val auth = FakeAuthRepository().apply { setSession(FakeAuthRepository.defaultSession()) }

            // Act
            val sut = createSut(auth = auth)
            advanceUntilIdle()

            // Assert
            assertEquals("Vlad", sut.state.value.displayName)
            assertEquals("v@example.com", sut.state.value.email)
        }

    @Test
    fun `state groups the latest expenses by date with per-group totals`() =
        runTestWithMain {
            // Arrange
            val expenses =
                FakeExpenseRepository(
                    initial =
                        listOf(
                            anExpense(id = 1, name = "Milk", amount = 45.0, date = LocalDate(2026, 6, 8)),
                            anExpense(id = 2, name = "Bread", amount = 22.5, date = LocalDate(2026, 6, 8)),
                            anExpense(id = 3, name = "Taxi", amount = 120.0, date = LocalDate(2026, 6, 7)),
                        ),
                )
            val sut = createSut(expenses = expenses)

            // Act
            advanceUntilIdle()

            // Assert
            val groups = sut.state.value.groups
            assertEquals(2, groups.size)
            assertEquals(LocalDate(2026, 6, 8), groups[0].date)
            assertEquals(67.5, groups[0].total)
            assertEquals(LocalDate(2026, 6, 7), groups[1].date)
            assertEquals(120.0, groups[1].total)
        }

    @Test
    fun `refresh failure emits ShowError`() =
        runTestWithMain {
            // Arrange
            val expenses =
                FakeExpenseRepository().apply { refreshResult = Either.Left(AppError.Network) }
            val sut = createSut(expenses = expenses)

            // Act
            advanceUntilIdle()

            // Assert
            assertEquals(HomeEvent.ShowError(AppError.Network), sut.events.first())
        }

    @Test
    fun `session read failure emits ShowError`() =
        runTestWithMain {
            val auth =
                FakeAuthRepository().apply {
                    currentSessionResult = Either.Left(AppError.Storage)
                }

            val sut = createSut(auth = auth)
            advanceUntilIdle()

            assertEquals(HomeEvent.ShowError(AppError.Storage), sut.events.first())
        }

    @Test
    fun `expense read failure emits ShowError`() =
        runTestWithMain {
            val expenses = FakeExpenseRepository().apply { setReadError(AppError.Storage) }

            val sut = createSut(expenses = expenses)
            advanceUntilIdle()

            assertEquals(HomeEvent.ShowError(AppError.Storage), sut.events.first())
        }

    @Test
    fun `SignOut clears the session and emits NavigateToLogin`() =
        runTestWithMain {
            // Arrange
            val auth = FakeAuthRepository().apply { setSession(FakeAuthRepository.defaultSession()) }
            val sut = createSut(auth = auth)
            advanceUntilIdle()

            // Act
            sut.onSignOut()
            advanceUntilIdle()

            // Assert
            assertEquals(HomeEvent.NavigateToLogin, sut.events.first())
            assertTrue(auth.calls.any { it is FakeAuthRepository.Call.Logout })
        }

    @Test
    fun `SignOut storage failure stays on the screen and emits ShowError`() =
        runTestWithMain {
            val auth =
                FakeAuthRepository().apply {
                    setSession(FakeAuthRepository.defaultSession())
                    logoutResult = Either.Left(AppError.Storage)
                }
            val sut = createSut(auth = auth)
            advanceUntilIdle()

            sut.onSignOut()
            advanceUntilIdle()

            assertEquals(HomeEvent.ShowError(AppError.Storage), sut.events.first())
        }

    @Test
    fun `retry pending delegates to repository`() =
        runTestWithMain {
            val expenses = FakeExpenseRepository()
            val sut = createSut(expenses = expenses)
            advanceUntilIdle()

            sut.onRetryPending(-7)
            advanceUntilIdle()

            assertTrue(expenses.calls.contains(FakeExpenseRepository.Call.RetryPending(-7)))
        }

    @Test
    fun `retry pending failure emits ShowError`() =
        runTestWithMain {
            val expenses = FakeExpenseRepository().apply { retryPendingResult = Either.Left(AppError.Network) }
            val sut = createSut(expenses = expenses)
            advanceUntilIdle()

            sut.onRetryPending(-7)
            advanceUntilIdle()

            assertEquals(HomeEvent.ShowError(AppError.Network), sut.events.first())
        }

    @Test
    fun `delete pending delegates to repository`() =
        runTestWithMain {
            val expenses = FakeExpenseRepository()
            val sut = createSut(expenses = expenses)
            advanceUntilIdle()

            sut.onDeletePending(-7)
            advanceUntilIdle()

            assertTrue(expenses.calls.contains(FakeExpenseRepository.Call.DeletePending(-7)))
        }

    private fun createSut(
        auth: FakeAuthRepository = FakeAuthRepository(),
        expenses: FakeExpenseRepository = FakeExpenseRepository(),
    ): HomeViewModel =
        HomeViewModel(
            getCurrentSession = GetCurrentSessionUseCase(auth),
            logout = LogoutUseCase(auth),
            getExpenses = GetExpensesUseCase(expenses),
            refreshExpenses = RefreshExpensesUseCase(expenses),
            retryPendingExpense = RetryPendingExpenseUseCase(expenses),
            deletePendingExpense = DeletePendingExpenseUseCase(expenses),
        )

    private fun anExpense(
        id: Long,
        name: String,
        amount: Double,
        date: LocalDate,
    ): Expense =
        Expense(
            id = id,
            name = name,
            amount = amount,
            currency = Currency.UAH,
            category = ExpenseCategorySnapshot(id = 1, name = "Food", icon = "🍎", color = "#FF6B6B"),
            note = null,
            inputType = InputType.TEXT,
            date = date,
            createdAt = Instant.fromEpochMilliseconds(0),
        )
}
