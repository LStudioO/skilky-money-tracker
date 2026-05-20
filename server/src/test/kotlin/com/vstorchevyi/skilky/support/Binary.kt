package com.vstorchevyi.skilky.support

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Builds a minimal RIFF/WAVE header (44 bytes) the parse-audio validator
 * accepts as a real WAV. Audio payload (PCM samples) is appended by the
 * caller — for validation tests the payload can be anything since only
 * the header is inspected.
 */
fun aWavHeader(
    sampleRate: Int = 16_000,
    channels: Short = 1,
    bitsPerSample: Short = 16,
    dataSize: Int = 0,
): ByteArray {
    val byteRate = sampleRate * channels * bitsPerSample / 8
    val blockAlign: Short = (channels * bitsPerSample / 8).toShort()
    return ByteBuffer
        .allocate(44)
        .order(ByteOrder.LITTLE_ENDIAN)
        .apply {
            put("RIFF".toByteArray(Charsets.US_ASCII))
            putInt(36 + dataSize)
            put("WAVE".toByteArray(Charsets.US_ASCII))
            put("fmt ".toByteArray(Charsets.US_ASCII))
            putInt(16) // PCM chunk size
            putShort(1) // PCM format
            putShort(channels)
            putInt(sampleRate)
            putInt(byteRate)
            putShort(blockAlign)
            putShort(bitsPerSample)
            put("data".toByteArray(Charsets.US_ASCII))
            putInt(dataSize)
        }.array()
}

/** Minimal JPEG magic-byte prefix; enough to pass receipt validation. */
fun aJpegHeader(extraBytes: Int = 32): ByteArray =
    byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte()) + ByteArray(extraBytes)

/**
 * WAV header in the layout Apple's CoreAudio writers produce (`say`,
 * some `AVAudioRecorder` configurations): a `JUNK` alignment chunk
 * sits between `WAVE` and `fmt `. The validator must walk chunks to
 * find `fmt ` rather than reading fixed offsets.
 */
fun aCoreAudioWavHeader(
    sampleRate: Int = 16_000,
    channels: Short = 1,
    junkChunkSize: Int = 28,
): ByteArray {
    val standard = aWavHeader(sampleRate = sampleRate, channels = channels)
    // standard[0..11] = RIFF header; standard[12..43] = fmt + data chunks
    val junk =
        ByteBuffer
            .allocate(8 + junkChunkSize)
            .order(ByteOrder.LITTLE_ENDIAN)
            .apply {
                put("JUNK".toByteArray(Charsets.US_ASCII))
                putInt(junkChunkSize)
                put(ByteArray(junkChunkSize))
            }.array()
    return standard.copyOfRange(0, 12) + junk + standard.copyOfRange(12, standard.size)
}
