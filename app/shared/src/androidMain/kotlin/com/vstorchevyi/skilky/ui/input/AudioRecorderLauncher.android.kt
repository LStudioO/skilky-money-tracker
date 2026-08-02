package com.vstorchevyi.skilky.ui.input

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

@Composable
internal actual fun rememberAudioRecorderLauncher(onResult: (ByteArray?) -> Unit): AudioRecorderActions? {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentOnResult by rememberUpdatedState(onResult)
    val recorder =
        remember(scope) {
            AndroidWavRecorder(
                scope = scope,
                onResult = { bytes -> currentOnResult(bytes) },
            )
        }
    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                recorder.start()
            } else {
                currentOnResult(null)
            }
        }

    DisposableEffect(recorder) {
        onDispose { recorder.cancel() }
    }

    return AudioRecorderActions(
        isRecording = recorder.isRecording,
        start = {
            if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                recorder.start()
            } else {
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        },
        stop = recorder::stop,
    )
}

private class AndroidWavRecorder(
    private val scope: CoroutineScope,
    private val onResult: (ByteArray?) -> Unit,
) {
    var isRecording: Boolean by mutableStateOf(false)
        private set

    private var audioRecord: AudioRecord? = null
    private var readJob: Job? = null
    private var pcmBytes: ByteArrayOutputStream? = null

    @SuppressLint("MissingPermission")
    fun start() {
        if (isRecording) return

        val bufferSize = audioBufferSize()
        val format =
            AudioFormat
                .Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(SAMPLE_RATE_HZ)
                .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                .build()
        val record =
            AudioRecord
                .Builder()
                .setAudioSource(MediaRecorder.AudioSource.MIC)
                .setAudioFormat(format)
                .setBufferSizeInBytes(bufferSize)
                .build()

        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            onResult(null)
            return
        }

        val pcm = ByteArrayOutputStream()
        audioRecord = record
        pcmBytes = pcm
        isRecording = true
        record.startRecording()
        readJob =
            scope.launch(Dispatchers.Default) {
                val buffer = ByteArray(bufferSize)
                while (isActive) {
                    val read = record.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        pcm.write(buffer, 0, read)
                    }
                }
            }
    }

    fun stop() {
        if (!isRecording) return
        isRecording = false
        val record = audioRecord
        val job = readJob
        runCatching {
            if (record?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                record.stop()
            }
        }
        scope.launch {
            val wav =
                withContext(Dispatchers.Default) {
                    job?.cancelAndJoin()
                    record?.release()
                    val pcm = pcmBytes?.toByteArray()
                    audioRecord = null
                    readJob = null
                    pcmBytes = null
                    if (pcm == null || pcm.isEmpty()) null else pcm.toWav()
                }
            onResult(wav)
        }
    }

    fun cancel() {
        isRecording = false
        runCatching { audioRecord?.stop() }
        audioRecord?.release()
        readJob?.cancel()
        audioRecord = null
        readJob = null
        pcmBytes = null
    }

    private fun audioBufferSize(): Int {
        val minimum =
            AudioRecord.getMinBufferSize(
                SAMPLE_RATE_HZ,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            )
        return maxOf(minimum, SAMPLE_RATE_HZ * BYTES_PER_SAMPLE)
    }
}

private fun ByteArray.toWav(): ByteArray =
    ByteArrayOutputStream(RIFF_HEADER_BYTES + size).use { output ->
        output.writeAscii("RIFF")
        output.writeLittleEndianInt(RIFF_HEADER_BYTES - RIFF_PREFIX_BYTES + size)
        output.writeAscii("WAVE")
        output.writeAscii("fmt ")
        output.writeLittleEndianInt(PCM_FORMAT_CHUNK_BYTES)
        output.writeLittleEndianShort(PCM_AUDIO_FORMAT)
        output.writeLittleEndianShort(CHANNEL_COUNT)
        output.writeLittleEndianInt(SAMPLE_RATE_HZ)
        output.writeLittleEndianInt(SAMPLE_RATE_HZ * CHANNEL_COUNT * BYTES_PER_SAMPLE)
        output.writeLittleEndianShort((CHANNEL_COUNT * BYTES_PER_SAMPLE).toShort())
        output.writeLittleEndianShort(BITS_PER_SAMPLE)
        output.writeAscii("data")
        output.writeLittleEndianInt(size)
        output.write(this)
        output.toByteArray()
    }

private fun ByteArrayOutputStream.writeAscii(value: String) {
    write(value.encodeToByteArray())
}

private fun ByteArrayOutputStream.writeLittleEndianInt(value: Int) {
    write(value and BYTE_MASK)
    write(value shr 8 and BYTE_MASK)
    write(value shr 16 and BYTE_MASK)
    write(value shr 24 and BYTE_MASK)
}

private fun ByteArrayOutputStream.writeLittleEndianShort(value: Short) {
    write(value.toInt() and BYTE_MASK)
    write(value.toInt() shr 8 and BYTE_MASK)
}

private const val SAMPLE_RATE_HZ = 16_000
private const val CHANNEL_COUNT: Short = 1
private const val BITS_PER_SAMPLE: Short = 16
private const val BYTES_PER_SAMPLE = 2
private const val PCM_AUDIO_FORMAT: Short = 1
private const val PCM_FORMAT_CHUNK_BYTES = 16
private const val RIFF_PREFIX_BYTES = 8
private const val RIFF_HEADER_BYTES = 44
private const val BYTE_MASK = 0xFF
