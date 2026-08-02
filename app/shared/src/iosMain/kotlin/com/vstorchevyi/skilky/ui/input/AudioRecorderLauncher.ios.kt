package com.vstorchevyi.skilky.ui.input

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import okio.FileSystem
import okio.Path.Companion.toPath
import platform.AVFAudio.AVAudioRecorder
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryRecord
import platform.AVFAudio.AVAudioSessionSetActiveOptionNotifyOthersOnDeactivation
import platform.AVFAudio.AVFormatIDKey
import platform.AVFAudio.AVLinearPCMBitDepthKey
import platform.AVFAudio.AVLinearPCMIsBigEndianKey
import platform.AVFAudio.AVLinearPCMIsFloatKey
import platform.AVFAudio.AVNumberOfChannelsKey
import platform.AVFAudio.AVSampleRateKey
import platform.AVFAudio.setActive
import platform.CoreAudioTypes.kAudioFormatLinearPCM
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUUID

@OptIn(ExperimentalForeignApi::class)
@Composable
internal actual fun rememberAudioRecorderLauncher(onResult: (ByteArray?) -> Unit): AudioRecorderActions? {
    val scope = rememberCoroutineScope()
    val currentOnResult by rememberUpdatedState(onResult)
    val recorder =
        remember(scope) {
            IosWavRecorder(
                scope = scope,
                onResult = { bytes -> currentOnResult(bytes) },
            )
        }

    DisposableEffect(recorder) {
        onDispose { recorder.cancel() }
    }

    return AudioRecorderActions(
        isRecording = recorder.isRecording,
        start = recorder::start,
        stop = recorder::stop,
    )
}

@OptIn(ExperimentalForeignApi::class)
private class IosWavRecorder(
    private val scope: CoroutineScope,
    private val onResult: (ByteArray?) -> Unit,
) {
    var isRecording: Boolean by mutableStateOf(false)
        private set

    private var permissionRequestPending = false
    private var audioRecorder: AVAudioRecorder? = null
    private var recordingUrl: NSURL? = null

    @Suppress("DEPRECATION")
    fun start() {
        if (isRecording || permissionRequestPending) return
        permissionRequestPending = true
        AVAudioSession.sharedInstance().requestRecordPermission { granted ->
            scope.launch {
                permissionRequestPending = false
                if (granted) {
                    startRecording()
                } else {
                    onResult(null)
                }
            }
        }
    }

    fun stop() {
        if (!isRecording) return
        isRecording = false
        audioRecorder?.stop()
        audioRecorder = null
        deactivateAudioSession()

        val url = recordingUrl
        recordingUrl = null
        onResult(url?.readAndDelete()?.takeIf(ByteArray::isNotEmpty))
    }

    fun cancel() {
        permissionRequestPending = false
        isRecording = false
        audioRecorder?.stop()
        audioRecorder?.deleteRecording()
        audioRecorder = null
        recordingUrl?.delete()
        recordingUrl = null
        deactivateAudioSession()
    }

    private fun startRecording() {
        if (isRecording) return
        val session = AVAudioSession.sharedInstance()
        if (!session.setCategory(AVAudioSessionCategoryRecord, error = null) ||
            !session.setActive(true, error = null)
        ) {
            onResult(null)
            return
        }

        val url = temporaryWavUrl()
        val recorder =
            runCatching {
                AVAudioRecorder(
                    uRL = url,
                    settings = wavSettings(),
                    error = null,
                )
            }.getOrNull()
        if (recorder == null || !recorder.prepareToRecord() || !recorder.record()) {
            recorder?.deleteRecording()
            url.delete()
            deactivateAudioSession()
            onResult(null)
            return
        }

        recordingUrl = url
        audioRecorder = recorder
        isRecording = true
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun wavSettings(): Map<Any?, *> =
    mapOf(
        AVFormatIDKey to kAudioFormatLinearPCM,
        AVSampleRateKey to SAMPLE_RATE_HZ,
        AVNumberOfChannelsKey to CHANNEL_COUNT,
        AVLinearPCMBitDepthKey to BITS_PER_SAMPLE,
        AVLinearPCMIsBigEndianKey to false,
        AVLinearPCMIsFloatKey to false,
    )

@OptIn(ExperimentalForeignApi::class)
private fun temporaryWavUrl(): NSURL {
    val fileName = "skilky-voice-${NSUUID.UUID().UUIDString}.wav"
    return NSURL(fileURLWithPath = "${NSTemporaryDirectory()}$fileName")
}

@OptIn(ExperimentalForeignApi::class)
private fun NSURL.readAndDelete(): ByteArray? {
    val path = path?.toPath() ?: return null
    return try {
        if (FileSystem.SYSTEM.exists(path)) {
            FileSystem.SYSTEM.read(path) { readByteArray() }
        } else {
            null
        }
    } finally {
        FileSystem.SYSTEM.delete(path, mustExist = false)
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun NSURL.delete() {
    path?.toPath()?.let { FileSystem.SYSTEM.delete(it, mustExist = false) }
}

@OptIn(ExperimentalForeignApi::class)
private fun deactivateAudioSession() {
    AVAudioSession.sharedInstance().setActive(
        active = false,
        withOptions = AVAudioSessionSetActiveOptionNotifyOthersOnDeactivation,
        error = null,
    )
}

private const val SAMPLE_RATE_HZ = 16_000.0
private const val CHANNEL_COUNT = 1
private const val BITS_PER_SAMPLE = 16
