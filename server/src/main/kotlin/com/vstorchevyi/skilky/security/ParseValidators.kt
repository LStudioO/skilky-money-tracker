package com.vstorchevyi.skilky.security

import com.vstorchevyi.skilky.api.ParseTextRequest
import com.vstorchevyi.skilky.errors.ValidationException
import java.nio.ByteBuffer
import java.nio.ByteOrder

private const val PARSE_TEXT_MIN_LENGTH = 1
private const val PARSE_TEXT_MAX_LENGTH = 2_000

/** Conservative upper bound. Gemma 4 caps audio around 30-60 s; 16 kHz mono 16-bit WAV is ~32 KB/s. */
private const val PARSE_AUDIO_MAX_BYTES = 10 * 1024 * 1024
private const val PARSE_AUDIO_REQUIRED_SAMPLE_RATE = 16_000
private const val PARSE_AUDIO_REQUIRED_CHANNELS: Short = 1

private const val PARSE_RECEIPT_MAX_BYTES = 10 * 1024 * 1024
private const val IMAGE_MAGIC_PREFIX_BYTES = 8

fun validateParseTextRequest(req: ParseTextRequest) {
    val trimmed = req.text.trim()
    if (trimmed.length < PARSE_TEXT_MIN_LENGTH) {
        throw ValidationException("Text must not be blank")
    }
    if (trimmed.length > PARSE_TEXT_MAX_LENGTH) {
        throw ValidationException("Text must be $PARSE_TEXT_MAX_LENGTH characters or fewer")
    }
}

/**
 * Verifies the bytes are a WAV container the Ollama Gemma 4 audio path
 * accepts: RIFF/WAVE, 16 kHz, mono. Anything else is rejected 422 here
 * rather than handed to the model — Ollama silently misreads raw PCM
 * as an image and the request fails with a useless 500.
 *
 * Parses RIFF chunks rather than reading fixed offsets, because the
 * `fmt ` chunk is not always at byte 12. Apple's CoreAudio (the writer
 * behind `say` and `AVAudioRecorder` in some configs) inserts a `JUNK`
 * alignment chunk before `fmt `; Android and iOS PCM writers usually
 * put `fmt ` first. The walk handles both.
 *
 * Client contract: each platform records to WAV 16 kHz mono before
 * upload. Android uses `AudioRecord` with `PCM_16BIT` plus a manual
 * 44-byte RIFF header; iOS / Wear OS use the platform-native equivalents.
 */
fun validateParseAudioBytes(bytes: ByteArray) {
    checkAudioSize(bytes)
    val (channels, sampleRate) = readWavFormat(bytes)
    if (channels != PARSE_AUDIO_REQUIRED_CHANNELS) {
        throw ValidationException("Audio must be mono (1 channel), got $channels")
    }
    if (sampleRate != PARSE_AUDIO_REQUIRED_SAMPLE_RATE) {
        throw ValidationException(
            "Audio must be sampled at $PARSE_AUDIO_REQUIRED_SAMPLE_RATE Hz, got $sampleRate",
        )
    }
}

private fun checkAudioSize(bytes: ByteArray) {
    if (bytes.isEmpty()) {
        throw ValidationException("Audio file is empty")
    }
    if (bytes.size > PARSE_AUDIO_MAX_BYTES) {
        throw ValidationException("Audio file exceeds $PARSE_AUDIO_MAX_BYTES bytes")
    }
}

private fun readWavFormat(bytes: ByteArray): Pair<Short, Int> {
    if (bytes.size < RIFF_HEADER_BYTES) {
        throw ValidationException("Audio file is not a valid WAV (too short)")
    }
    val riff = String(bytes, 0, 4, Charsets.US_ASCII)
    val wave = String(bytes, 8, 4, Charsets.US_ASCII)
    if (riff != "RIFF" || wave != "WAVE") {
        throw ValidationException("Audio file is not a WAV (missing RIFF/WAVE header)")
    }
    val fmt =
        findRiffChunk(bytes, "fmt ")
            ?: throw ValidationException("Audio file is missing the 'fmt ' chunk")
    if (fmt.size < FMT_CHUNK_MIN_BYTES) {
        throw ValidationException("Audio 'fmt ' chunk is truncated")
    }
    val view = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
    return view.getShort(fmt.offset + FMT_OFFSET_CHANNELS) to view.getInt(fmt.offset + FMT_OFFSET_SAMPLE_RATE)
}

private const val RIFF_HEADER_BYTES = 12
private const val CHUNK_HEADER_BYTES = 8
private const val FMT_CHUNK_MIN_BYTES = 16
private const val FMT_OFFSET_CHANNELS = 2
private const val FMT_OFFSET_SAMPLE_RATE = 4

private data class ChunkLocation(
    val offset: Int,
    val size: Int,
)

/**
 * Walks RIFF chunks starting after the 12-byte file header, returns
 * the data-offset and size of the first chunk matching [id], or null
 * if the file ends before one appears. Chunks are 2-byte aligned in
 * the spec; odd-sized chunks have a trailing pad byte.
 */
private fun findRiffChunk(
    bytes: ByteArray,
    id: String,
): ChunkLocation? {
    var cursor = RIFF_HEADER_BYTES
    val view = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
    while (cursor + CHUNK_HEADER_BYTES <= bytes.size) {
        val chunkId = String(bytes, cursor, 4, Charsets.US_ASCII)
        val chunkSize = view.getInt(cursor + 4)
        if (chunkSize < 0) return null
        val dataOffset = cursor + CHUNK_HEADER_BYTES
        if (chunkId == id) {
            return ChunkLocation(offset = dataOffset, size = chunkSize)
        }
        cursor = dataOffset + chunkSize + (chunkSize and 1)
    }
    return null
}

private val JPEG_MAGIC = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())
private val PNG_MAGIC =
    byteArrayOf(
        0x89.toByte(),
        0x50.toByte(),
        0x4E.toByte(),
        0x47.toByte(),
        0x0D.toByte(),
        0x0A.toByte(),
        0x1A.toByte(),
        0x0A.toByte(),
    )

/**
 * Receipt images are JPEG or PNG. We check the magic bytes rather than
 * trust the multipart `Content-Type`: clients lie or omit, and Gemma
 * needs a real image to do OCR.
 */
fun validateParseReceiptBytes(bytes: ByteArray) {
    if (bytes.isEmpty()) {
        throw ValidationException("Image file is empty")
    }
    if (bytes.size > PARSE_RECEIPT_MAX_BYTES) {
        throw ValidationException("Image file exceeds $PARSE_RECEIPT_MAX_BYTES bytes")
    }
    if (bytes.size < IMAGE_MAGIC_PREFIX_BYTES) {
        throw ValidationException("Image file is too short to be a JPEG or PNG")
    }
    if (!bytes.startsWith(JPEG_MAGIC) && !bytes.startsWith(PNG_MAGIC)) {
        throw ValidationException("Image must be JPEG or PNG")
    }
}

private fun ByteArray.startsWith(prefix: ByteArray): Boolean {
    if (size < prefix.size) return false
    for (i in prefix.indices) {
        if (this[i] != prefix[i]) return false
    }
    return true
}
