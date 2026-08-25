package com.vstorchevyi.skilky.data.sync

import com.vstorchevyi.skilky.domain.repository.ExpenseRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Retries queued creates and refreshes expenses whenever a platform reports connectivity. */
internal class ExpenseSyncManager(
    networkMonitor: NetworkMonitor,
    repository: ExpenseRepository,
    scope: CoroutineScope,
) {
    init {
        scope.launch {
            networkMonitor.isOnline.collect { isOnline ->
                if (isOnline) repository.refresh()
            }
        }
    }
}
