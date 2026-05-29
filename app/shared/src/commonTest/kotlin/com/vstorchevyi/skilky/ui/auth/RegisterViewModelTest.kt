package com.vstorchevyi.skilky.ui.auth

import com.vstorchevyi.skilky.domain.model.AppError
import com.vstorchevyi.skilky.domain.model.Either
import com.vstorchevyi.skilky.domain.repository.FakeAuthRepository
import com.vstorchevyi.skilky.domain.usecase.RegisterUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class RegisterViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `submit with any blank field is a no-op`() =
        runTest(dispatcher) {
            // Arrange
            val repository = FakeAuthRepository()
            val sut = createSut(repository = repository)
            sut.onIntent(RegisterIntent.EmailChanged("v@example.com"))
            sut.onIntent(RegisterIntent.PasswordChanged("hunter2"))
            // display name is intentionally left blank

            // Act
            sut.onIntent(RegisterIntent.Submit)
            dispatcher.scheduler.advanceUntilIdle()

            // Assert
            assertFalse(sut.state.value.canSubmit)
            assertTrue(repository.calls.isEmpty())
        }

    @Test
    fun `successful registration trims email plus name and emits NavigateToHome`() =
        runTest(dispatcher) {
            // Arrange
            val repository =
                FakeAuthRepository(registerResult = Either.Right(FakeAuthRepository.defaultSession()))
            val sut = createSut(repository = repository)
            sut.onIntent(RegisterIntent.DisplayNameChanged("  Vlad  "))
            sut.onIntent(RegisterIntent.EmailChanged("  v@example.com  "))
            sut.onIntent(RegisterIntent.PasswordChanged("hunter2"))

            // Act
            sut.onIntent(RegisterIntent.Submit)
            dispatcher.scheduler.advanceUntilIdle()

            // Assert
            assertEquals(RegisterEffect.NavigateToHome, sut.effects.first())
            val call = repository.calls.last() as FakeAuthRepository.Call.Register
            assertEquals("v@example.com", call.email)
            assertEquals("Vlad", call.displayName)
            assertEquals("hunter2", call.password)
        }

    @Test
    fun `taken email surfaces Conflict in state`() =
        runTest(dispatcher) {
            // Arrange
            val repository = FakeAuthRepository(registerResult = Either.Left(AppError.Conflict))
            val sut = createSut(repository = repository)
            sut.onIntent(RegisterIntent.DisplayNameChanged("Vlad"))
            sut.onIntent(RegisterIntent.EmailChanged("taken@example.com"))
            sut.onIntent(RegisterIntent.PasswordChanged("hunter2"))

            // Act
            sut.onIntent(RegisterIntent.Submit)
            dispatcher.scheduler.advanceUntilIdle()

            // Assert
            assertEquals(AppError.Conflict, sut.state.value.error)
            assertFalse(sut.state.value.isSubmitting)
        }

    @Test
    fun `GoToLogin emits the navigate effect without touching the repository`() =
        runTest(dispatcher) {
            // Arrange
            val repository = FakeAuthRepository()
            val sut = createSut(repository = repository)

            // Act
            sut.onIntent(RegisterIntent.GoToLogin)

            // Assert
            assertEquals(RegisterEffect.NavigateToLogin, sut.effects.first())
            assertTrue(repository.calls.isEmpty())
        }

    private fun createSut(repository: FakeAuthRepository = FakeAuthRepository()): RegisterViewModel =
        RegisterViewModel(register = RegisterUseCase(repository))
}
