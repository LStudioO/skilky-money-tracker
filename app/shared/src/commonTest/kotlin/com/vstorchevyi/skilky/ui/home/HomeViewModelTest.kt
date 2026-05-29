package com.vstorchevyi.skilky.ui.home

import com.vstorchevyi.skilky.domain.repository.FakeAuthRepository
import com.vstorchevyi.skilky.domain.usecase.GetCurrentSessionUseCase
import com.vstorchevyi.skilky.domain.usecase.LogoutUseCase
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
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
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
    fun `state reflects the persisted session at init`() =
        runTest(dispatcher) {
            // Arrange
            val repository = FakeAuthRepository()
            repository.setSession(FakeAuthRepository.defaultSession())

            // Act
            val sut = createSut(repository = repository)
            dispatcher.scheduler.advanceUntilIdle()

            // Assert
            assertEquals("Vlad", sut.state.value.displayName)
            assertEquals("v@example.com", sut.state.value.email)
        }

    @Test
    fun `state stays blank when no session is stored`() =
        runTest(dispatcher) {
            // Arrange
            val sut = createSut(repository = FakeAuthRepository())

            // Act
            dispatcher.scheduler.advanceUntilIdle()

            // Assert
            assertEquals(HomeUiState(), sut.state.value)
        }

    @Test
    fun `SignOut clears the session and emits NavigateToLogin`() =
        runTest(dispatcher) {
            // Arrange
            val repository = FakeAuthRepository()
            repository.setSession(FakeAuthRepository.defaultSession())
            val sut = createSut(repository = repository)
            dispatcher.scheduler.advanceUntilIdle()

            // Act
            sut.onIntent(HomeIntent.SignOut)
            dispatcher.scheduler.advanceUntilIdle()

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
