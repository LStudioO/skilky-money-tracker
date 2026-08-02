package com.vstorchevyi.skilky.ui.input

import androidx.compose.runtime.Composable

internal data class AudioRecorderActions(
    val isRecording: Boolean,
    val start: () -> Unit,
    val stop: () -> Unit,
)

/** Returns audio recording actions on targets with a WAV recorder, or null when unavailable. */
@Composable
internal expect fun rememberAudioRecorderLauncher(onResult: (ByteArray?) -> Unit): AudioRecorderActions?
