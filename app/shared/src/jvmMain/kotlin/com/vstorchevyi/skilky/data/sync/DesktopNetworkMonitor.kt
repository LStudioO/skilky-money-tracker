package com.vstorchevyi.skilky.data.sync

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlin.time.Duration.Companion.seconds

/** Desktop retries periodically because the JVM has no portable connectivity callback. */
internal class DesktopNetworkMonitor : NetworkMonitor {
    override val isOnline: Flow<Boolean> =
        flow {
            while (currentCoroutineContext().isActive) {
                emit(true)
                delay(RETRY_INTERVAL)
            }
        }

    private companion object {
        val RETRY_INTERVAL = 30.seconds
    }
}
