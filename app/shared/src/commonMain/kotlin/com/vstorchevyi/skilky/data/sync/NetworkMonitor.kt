package com.vstorchevyi.skilky.data.sync

import kotlinx.coroutines.flow.Flow

internal interface NetworkMonitor {
    val isOnline: Flow<Boolean>
}
