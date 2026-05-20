package com.vstorchevyi.skilky.security

import com.vstorchevyi.skilky.api.Currency
import com.vstorchevyi.skilky.api.ParseTextRequest
import com.vstorchevyi.skilky.errors.ValidationException
import com.vstorchevyi.skilky.support.aCoreAudioWavHeader
import com.vstorchevyi.skilky.support.aWavHeader
import io.kotest.assertions.throwables.shouldNotThrow
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.string.shouldContain
import kotlin.test.Test

class ParseValidatorsTest {
    // --- text -------------------------------------------------------------

    @Test
    fun `non-blank text within length budget passes`() {
        val request = aParseTextRequest(text = "milk 45, bread 22")

        shouldNotThrow<ValidationException> { validateParseTextRequest(request) }
    }

    @Test
    fun `blank text is rejected`() {
        val request = aParseTextRequest(text = "   ")

        shouldThrow<ValidationException> { validateParseTextRequest(request) }
    }

    @Test
    fun `text over 2000 characters is rejected`() {
        val request = aParseTextRequest(text = "a".repeat(2_001))

        shouldThrow<ValidationException> { validateParseTextRequest(request) }
    }

    @Test
    fun `text exactly at 2000 characters passes`() {
        val request = aParseTextRequest(text = "a".repeat(2_000))

        shouldNotThrow<ValidationException> { validateParseTextRequest(request) }
    }

    // --- audio ------------------------------------------------------------

    @Test
    fun `valid 16k mono WAV passes`() {
        val wav = aWavHeader() + ByteArray(64) // 44-byte header + a bit of data

        shouldNotThrow<ValidationException> { validateParseAudioBytes(wav) }
    }

    @Test
    fun `empty audio is rejected`() {
        shouldThrow<ValidationException> { validateParseAudioBytes(ByteArray(0)) }
    }

    @Test
    fun `audio without RIFF header is rejected`() {
        val notWav = ByteArray(64) // all zeros — no RIFF

        val ex = shouldThrow<ValidationException> { validateParseAudioBytes(notWav) }
        ex.message!! shouldContain "WAV"
    }

    @Test
    fun `48k WAV is rejected with a helpful sample-rate message`() {
        val wav = aWavHeader(sampleRate = 48_000) + ByteArray(64)

        val ex = shouldThrow<ValidationException> { validateParseAudioBytes(wav) }
        ex.message!! shouldContain "16000"
    }

    @Test
    fun `stereo WAV is rejected with a helpful channels message`() {
        val wav = aWavHeader(channels = 2) + ByteArray(64)

        val ex = shouldThrow<ValidationException> { validateParseAudioBytes(wav) }
        ex.message!! shouldContain "mono"
    }

    @Test
    fun `CoreAudio-style WAV with a JUNK chunk before fmt is accepted`() {
        // macOS `say` and some AVAudioRecorder configs emit this layout.
        val wav = aCoreAudioWavHeader() + ByteArray(64)

        shouldNotThrow<ValidationException> { validateParseAudioBytes(wav) }
    }

    // --- receipt ----------------------------------------------------------

    @Test
    fun `valid JPEG magic bytes pass`() {
        val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()) + ByteArray(32)

        shouldNotThrow<ValidationException> { validateParseReceiptBytes(jpeg) }
    }

    @Test
    fun `valid PNG magic bytes pass`() {
        val png =
            byteArrayOf(
                0x89.toByte(),
                0x50.toByte(),
                0x4E.toByte(),
                0x47.toByte(),
                0x0D.toByte(),
                0x0A.toByte(),
                0x1A.toByte(),
                0x0A.toByte(),
            ) + ByteArray(32)

        shouldNotThrow<ValidationException> { validateParseReceiptBytes(png) }
    }

    @Test
    fun `non-image bytes are rejected`() {
        val pdf = "%PDF-1.4 hello".toByteArray() + ByteArray(32)

        val ex = shouldThrow<ValidationException> { validateParseReceiptBytes(pdf) }
        ex.message!! shouldContain "JPEG"
    }

    @Test
    fun `empty image is rejected`() {
        shouldThrow<ValidationException> { validateParseReceiptBytes(ByteArray(0)) }
    }

    private fun aParseTextRequest(
        text: String = "milk 45",
        currency: Currency = Currency.UAH,
    ) = ParseTextRequest(text = text, currency = currency)
}
