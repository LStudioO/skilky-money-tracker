package com.vstorchevyi.skilky.ui.auth

import com.vstorchevyi.skilky.domain.model.AppError
import com.vstorchevyi.skilky.domain.model.Either
import com.vstorchevyi.skilky.domain.repository.FakeAuthRepository
import com.vstorchevyi.skilky.domain.usecase.LoginUseCase
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {
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
    fun `email and password edits update state and clear any prior error`() =
        runTest(dispatcher) {
            // Arrange
            val sut = createSut()
            sut.onEmailChange("v@example.com")

            // Act
            sut.onPasswordChange("hunter2")

            // Assert
            val state = sut.state.value
            assertEquals("v@example.com", state.email)
            assertEquals("hunter2", state.password)
            assertNull(state.error)
            assertTrue(state.canSubmit)
        }

    @Test
    fun `submit with blank fields is a no-op`() =
        runTest(dispatcher) {
            // Arrange
            val repository = FakeAuthRepository()
            val sut = createSut(repository = repository)

            // Act
            sut.onSubmit()
            dispatcher.scheduler.advanceUntilIdle()

            // Assert
            assertFalse(sut.state.value.isSubmitting)
            assertTrue(repository.calls.isEmpty())
        }

    @Test
    fun `successful login emits NavigateToHome and forwards the trimmed credentials`() =
        runTest(dispatcher) {
            // Arrange
            val repository =
                FakeAuthRepository(loginResult = Either.Right(FakeAuthRepository.defaultSession()))
            val sut = createSut(repository = repository)
            sut.onEmailChange("  v@example.com  ")
            sut.onPasswordChange("hunter2")

            // Act
            sut.onSubmit()
            dispatcher.scheduler.advanceUntilIdle()

            // Assert
            assertEquals(LoginEffect.NavigateToHome, sut.effects.first())
            assertFalse(sut.state.value.isSubmitting)
            val call = repository.calls.last() as FakeAuthRepository.Call.Login
            assertEquals("v@example.com", call.email)
            assertEquals("hunter2", call.password)
        }

    @Test
    fun `failed login surfaces the AppError in state and emits no effect`() =
        runTest(dispatcher) {
            // Arrange
            val repository = FakeAuthRepository(loginResult = Either.Left(AppError.Unauthorized))
            val sut = createSut(repository = repository)
            sut.onEmailChange("v@example.com")
            sut.onPasswordChange("wrong")

            // Act
            sut.onSubmit()
            dispatcher.scheduler.advanceUntilIdle()

            // Assert
            assertEquals(AppError.Unauthorized, sut.state.value.error)
            assertFalse(sut.state.value.isSubmitting)
        }

    @Test
    fun `onGoToRegister emits the navigate effect without touching the repository`() =
        runTest(dispatcher) {
            // Arrange
            val repository = FakeAuthRepository()
            val sut = createSut(repository = repository)

            // Act
            sut.onGoToRegister()

            // Assert
            assertEquals(LoginEffect.NavigateToRegister, sut.effects.first())
            assertTrue(repository.calls.isEmpty())
        }

    private fun createSut(repository: FakeAuthRepository = FakeAuthRepository()): LoginViewModel =
        LoginViewModel(login = LoginUseCase(repository))
}
