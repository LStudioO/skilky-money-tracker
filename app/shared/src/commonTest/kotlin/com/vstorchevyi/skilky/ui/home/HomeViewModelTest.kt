package com.vstorchevyi.skilky.ui.home

import com.vstorchevyi.skilky.domain.repository.FakeAuthRepository
import com.vstorchevyi.skilky.domain.usecase.GetCurrentSessionUseCase
import com.vstorchevyi.skilky.domain.usecase.LogoutUseCase
import com.vstorchevyi.skilky.support.runTestWithMain
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    @Test
    fun `state reflects the persisted session at init`() =
        runTestWithMain {
            // Arrange
            val repository = FakeAuthRepository()
            repository.setSession(FakeAuthRepository.defaultSession())

            // Act
            val sut = createSut(repository = repository)
            advanceUntilIdle()

            // Assert
            assertEquals("Vlad", sut.state.value.displayName)
            assertEquals("v@example.com", sut.state.value.email)
        }

    @Test
    fun `state stays blank when no session is stored`() =
        runTestWithMain {
            // Arrange
            val sut = createSut(repository = FakeAuthRepository())

            // Act
            advanceUntilIdle()

            // Assert
            assertEquals(HomeUiState(), sut.state.value)
        }

    @Test
    fun `SignOut clears the session and emits NavigateToLogin`() =
        runTestWithMain {
            // Arrange
            val repository = FakeAuthRepository()
            repository.setSession(FakeAuthRepository.defaultSession())
            val sut = createSut(repository = repository)
            advanceUntilIdle()

            // Act
            sut.onSignOut()
            advanceUntilIdle()

            // Assert
            assertEquals(HomeEffect.NavigateToLogin, sut.effects.first())
            assertTrue(repository.calls.any { it is FakeAuthRepository.Call.Logout })
        }

    private fun createSut(repository: FakeAuthRepository = FakeAuthRepository()): HomeViewModel =
        HomeViewModel(
            getCurrentSession = GetCurrentSessionUseCase(repository),
            logout = LogoutUseCase(repository),
        )
}
