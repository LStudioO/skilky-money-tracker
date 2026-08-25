package com.vstorchevyi.skilky.data.sync

import com.vstorchevyi.skilky.domain.repository.FakeExpenseRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class ExpenseSyncManagerTest {
    @Test
    fun `online event refreshes repository`() =
        runTest {
            val connectivity = MutableStateFlow(false)
            val repository = FakeExpenseRepository()
            ExpenseSyncManager(
                networkMonitor =
                    object : NetworkMonitor {
                        override val isOnline = connectivity
                    },
                repository = repository,
                scope = backgroundScope,
            )
            runCurrent()

            connectivity.value = true
            runCurrent()

            assertEquals(listOf<FakeExpenseRepository.Call>(FakeExpenseRepository.Call.Refresh), repository.calls)
        }

    @Test
    fun `offline event does not refresh repository`() =
        runTest {
            val connectivity = MutableStateFlow(false)
            val repository = FakeExpenseRepository()
            ExpenseSyncManager(
                networkMonitor =
                    object : NetworkMonitor {
                        override val isOnline = connectivity
                    },
                repository = repository,
                scope = backgroundScope,
            )
            runCurrent()

            runCurrent()

            assertEquals(emptyList<FakeExpenseRepository.Call>(), repository.calls)
        }
}
