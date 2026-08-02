package com.vstorchevyi.skilky.ai

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

internal data class WavAudioDiagnostics(
    val durationMs: Long,
    val peak: Int,
    val rms: Int,
)

/** Reads signal-level diagnostics from a PCM 16-bit WAV without retaining its samples. */
@Suppress("ReturnCount")
internal fun ByteArray.wavAudioDiagnostics(): WavAudioDiagnostics? {
    if (size < RIFF_HEADER_BYTES || ascii(0, 4) != "RIFF" || ascii(8, 4) != "WAVE") return null

    val format = findChunk("fmt ") ?: return null
    val data = findChunk("data") ?: return null
    if (format.size < PCM_FORMAT_CHUNK_BYTES) return null

    val view = ByteBuffer.wrap(this).order(ByteOrder.LITTLE_ENDIAN)
    val audioFormat = view.getShort(format.offset)
    val bitsPerSample = view.getShort(format.offset + BITS_PER_SAMPLE_OFFSET)
    val byteRate = view.getInt(format.offset + BYTE_RATE_OFFSET)
    if (audioFormat != PCM_AUDIO_FORMAT || bitsPerSample != PCM_BITS_PER_SAMPLE || byteRate <= 0) return null

    val availableDataBytes = maxOf(0, minOf(data.size, size - data.offset))
    val sampleCount = availableDataBytes / BYTES_PER_SAMPLE
    if (sampleCount == 0) return WavAudioDiagnostics(durationMs = 0, peak = 0, rms = 0)

    var peak = 0
    var squareSum = 0.0
    repeat(sampleCount) { index ->
        val sample = view.getShort(data.offset + index * BYTES_PER_SAMPLE).toInt()
        peak = maxOf(peak, abs(sample))
        squareSum += sample.toDouble() * sample
    }

    return WavAudioDiagnostics(
        durationMs = availableDataBytes.toLong() * MILLIS_PER_SECOND / byteRate,
        peak = peak,
        rms = sqrt(squareSum / sampleCount).roundToInt(),
    )
}

internal fun String.forAudioLog(): String =
    replace("\\", "\\\\")
        .replace("\r", "\\r")
        .replace("\n", "\\n")
        .take(MAX_LOGGED_TRANSCRIPT_CHARS)

private fun ByteArray.findChunk(id: String): WavChunk? {
    val view = ByteBuffer.wrap(this).order(ByteOrder.LITTLE_ENDIAN)
    var cursor = RIFF_HEADER_BYTES
    while (cursor + CHUNK_HEADER_BYTES <= size) {
        val chunkSize = view.getInt(cursor + CHUNK_SIZE_OFFSET)
        if (chunkSize < 0) return null
        val dataOffset = cursor + CHUNK_HEADER_BYTES
        if (ascii(cursor, CHUNK_ID_BYTES) == id) return WavChunk(dataOffset, chunkSize)
        cursor = dataOffset + chunkSize + (chunkSize and 1)
    }
    return null
}

private fun ByteArray.ascii(
    offset: Int,
    length: Int,
): String = String(this, offset, length, Charsets.US_ASCII)

private data class WavChunk(
    val offset: Int,
    val size: Int,
)

private const val RIFF_HEADER_BYTES = 12
private const val CHUNK_HEADER_BYTES = 8
private const val CHUNK_ID_BYTES = 4
private const val CHUNK_SIZE_OFFSET = 4
private const val PCM_FORMAT_CHUNK_BYTES = 16
private const val BYTE_RATE_OFFSET = 8
private const val BITS_PER_SAMPLE_OFFSET = 14
private const val PCM_AUDIO_FORMAT: Short = 1
private const val PCM_BITS_PER_SAMPLE: Short = 16
private const val BYTES_PER_SAMPLE = 2
private const val MILLIS_PER_SECOND = 1_000L
private const val MAX_LOGGED_TRANSCRIPT_CHARS = 500
