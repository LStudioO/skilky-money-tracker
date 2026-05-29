package com.vstorchevyi.skilky.data.remote

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * One-shot signal published when the persisted session becomes invalid mid-app
 * (for example, the refresh-token call returns 401, or the server has revoked
 * the session). UI code observes this and bounces the user back to Login.
 *
 * Implemented as a [SharedFlow] so multiple observers (the nav host and any
 * future telemetry sink) can pick it up. The replay buffer is zero so a
 * sign-out that fired before anyone subscribed is dropped, which is the right
 * behavior: the app's start-up flow already inspects the stored session
 * directly and routes accordingly.
 */
class SessionEvents {
    private val _signedOut =
        MutableSharedFlow<Unit>(
            replay = 0,
            extraBufferCapacity = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )

    val signedOut: SharedFlow<Unit> = _signedOut.asSharedFlow()

    fun emitSignedOut() {
        _signedOut.tryEmit(Unit)
    }
}
