package com.vstorchevyi.skilky.data.remote

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * One-shot signals published by bearer authentication when the persisted
 * session becomes invalid or its local storage cannot be accessed. UI code
 * observes these to return to Login or show a storage error.
 *
 * Implemented as a [SharedFlow] so multiple observers (the nav host and any
 * future telemetry sink) can pick it up. The replay buffer is zero so a
 * event that fired before anyone subscribed is dropped. Startup session reads
 * report their result directly instead of using these flows.
 */
class SessionEvents {
    private val _signedOut =
        MutableSharedFlow<Unit>(
            replay = 0,
            extraBufferCapacity = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )

    val signedOut: SharedFlow<Unit> = _signedOut.asSharedFlow()

    private val _storageFailures =
        MutableSharedFlow<Unit>(
            replay = 0,
            extraBufferCapacity = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )

    val storageFailures: SharedFlow<Unit> = _storageFailures.asSharedFlow()

    fun emitSignedOut() {
        _signedOut.tryEmit(Unit)
    }

    fun emitStorageFailure() {
        _storageFailures.tryEmit(Unit)
    }
}
