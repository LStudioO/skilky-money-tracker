package com.vstorchevyi.skilky.ui.auth

import com.vstorchevyi.skilky.domain.model.AppError
import com.vstorchevyi.skilky.domain.model.Either
import com.vstorchevyi.skilky.domain.repository.FakeAuthRepository
import com.vstorchevyi.skilky.domain.usecase.RegisterUseCase
import com.vstorchevyi.skilky.support.runTestWithMain
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class RegisterViewModelTest {
    @Test
    fun `submit with any blank field is a no-op`() =
        runTestWithMain {
            // Arrange
            val repository = FakeAuthRepository()
            val sut = createSut(repository = repository)
            sut.onEmailChange("v@example.com")
            sut.onPasswordChange("hunter2")
            // display name is intentionally left blank

            // Act
            sut.onSubmit()
            advanceUntilIdle()

            // Assert
            assertFalse(sut.state.value.canSubmit)
            assertTrue(repository.calls.isEmpty())
        }

    @Test
    fun `successful registration trims email plus name and emits NavigateToHome`() =
        runTestWithMain {
            // Arrange
            val repository =
                FakeAuthRepository(registerResult = Either.Right(FakeAuthRepository.defaultSession()))
            val sut = createSut(repository = repository)
            sut.onDisplayNameChange("  Vlad  ")
            sut.onEmailChange("  v@example.com  ")
            sut.onPasswordChange("hunter2")

            // Act
            sut.onSubmit()
            advanceUntilIdle()

            // Assert
            assertEquals(RegisterEffect.NavigateToHome, sut.effects.first())
            val call = repository.calls.last() as FakeAuthRepository.Call.Register
            assertEquals("v@example.com", call.email)
            assertEquals("Vlad", call.displayName)
            assertEquals("hunter2", call.password)
        }

    @Test
    fun `taken email surfaces Conflict in state`() =
        runTestWithMain {
            // Arrange
            val repository = FakeAuthRepository(registerResult = Either.Left(AppError.Conflict))
            val sut = createSut(repository = repository)
            sut.onDisplayNameChange("Vlad")
            sut.onEmailChange("taken@example.com")
            sut.onPasswordChange("hunter2")

            // Act
            sut.onSubmit()
            advanceUntilIdle()

            // Assert
            assertEquals(AppError.Conflict, sut.state.value.error)
            assertFalse(sut.state.value.isSubmitting)
        }

    @Test
    fun `onGoToLogin emits the navigate effect without touching the repository`() =
        runTestWithMain {
            // Arrange
            val repository = FakeAuthRepository()
            val sut = createSut(repository = repository)

            // Act
            sut.onGoToLogin()

            // Assert
            assertEquals(RegisterEffect.NavigateToLogin, sut.effects.first())
            assertTrue(repository.calls.isEmpty())
        }

    private fun createSut(repository: FakeAuthRepository = FakeAuthRepository()): RegisterViewModel =
        RegisterViewModel(register = RegisterUseCase(repository))
}
