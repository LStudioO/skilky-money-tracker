package com.vstorchevyi.skilky.ai

import com.vstorchevyi.skilky.support.aWavHeader
import io.kotest.matchers.shouldBe
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.Test

class AudioDiagnosticsTest {
    @Test
    fun `reads duration and signal levels from PCM samples`() {
        val samples = shortArrayOf(0, 1_000, -2_000, 3_000)
        val payload =
            ByteBuffer
                .allocate(samples.size * Short.SIZE_BYTES)
                .order(ByteOrder.LITTLE_ENDIAN)
                .apply { samples.forEach(::putShort) }
                .array()
        val wav = aWavHeader(dataSize = payload.size) + payload

        wav.wavAudioDiagnostics() shouldBe
            WavAudioDiagnostics(
                durationMs = 0,
                peak = 3_000,
                rms = 1_871,
            )
    }

    @Test
    fun `reports zero levels for silent PCM`() {
        val payload = ByteArray(32_000)
        val wav = aWavHeader(dataSize = payload.size) + payload

        wav.wavAudioDiagnostics() shouldBe WavAudioDiagnostics(durationMs = 1_000, peak = 0, rms = 0)
    }

    @Test
    fun `escapes transcript line breaks before logging`() {
        "кава\n10\\грн".forAudioLog() shouldBe "кава\\n10\\\\грн"
    }
}
