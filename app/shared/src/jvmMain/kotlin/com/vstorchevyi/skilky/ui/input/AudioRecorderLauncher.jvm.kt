package com.vstorchevyi.skilky.ui.input

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.TargetDataLine

@Composable
internal actual fun rememberAudioRecorderLauncher(onResult: (ByteArray?) -> Unit): AudioRecorderActions? {
    val scope = rememberCoroutineScope()
    val currentOnResult by rememberUpdatedState(onResult)
    val recorder =
        remember(scope) {
            DesktopWavRecorder(
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

private class DesktopWavRecorder(
    private val scope: CoroutineScope,
    private val onResult: (ByteArray?) -> Unit,
) {
    var isRecording: Boolean by mutableStateOf(false)
        private set

    private var line: TargetDataLine? = null
    private var readJob: Job? = null
    private var pcmBytes: ByteArrayOutputStream? = null

    fun start() {
        if (isRecording) return

        val format = audioFormat()
        val info = DataLine.Info(TargetDataLine::class.java, format)
        val targetLine =
            runCatching {
                (AudioSystem.getLine(info) as TargetDataLine).apply {
                    open(format)
                    start()
                }
            }.getOrElse {
                onResult(null)
                return
            }

        val pcm = ByteArrayOutputStream()
        line = targetLine
        pcmBytes = pcm
        isRecording = true
        readJob =
            scope.launch(Dispatchers.IO) {
                val buffer = ByteArray(BUFFER_BYTES)
                while (isActive) {
                    val read = targetLine.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        pcm.write(buffer, 0, read)
                    }
                }
            }
    }

    fun stop() {
        if (!isRecording) return
        isRecording = false

        val targetLine = line
        val job = readJob
        runCatching { targetLine?.stop() }
        runCatching { targetLine?.flush() }

        scope.launch {
            val wav =
                withContext(Dispatchers.IO) {
                    job?.cancelAndJoin()
                    targetLine?.close()
                    val pcm = pcmBytes?.toByteArray()
                    line = null
                    readJob = null
                    pcmBytes = null
                    if (pcm == null || pcm.isEmpty()) null else pcm.toWav()
                }
            onResult(wav)
        }
    }

    fun cancel() {
        isRecording = false
        runCatching { line?.stop() }
        runCatching { line?.close() }
        readJob?.cancel()
        line = null
        readJob = null
        pcmBytes = null
    }
}

private fun audioFormat(): AudioFormat =
    AudioFormat(
        AudioFormat.Encoding.PCM_SIGNED,
        SAMPLE_RATE_HZ.toFloat(),
        BITS_PER_SAMPLE.toInt(),
        CHANNEL_COUNT.toInt(),
        CHANNEL_COUNT * BYTES_PER_SAMPLE,
        SAMPLE_RATE_HZ.toFloat(),
        false,
    )

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
private const val BUFFER_BYTES = SAMPLE_RATE_HZ * BYTES_PER_SAMPLE
