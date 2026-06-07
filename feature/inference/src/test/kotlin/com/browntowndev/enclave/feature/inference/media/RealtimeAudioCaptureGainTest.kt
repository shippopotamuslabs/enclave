package com.browntowndev.enclave.feature.inference.media

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class RealtimeAudioCaptureGainTest {
    @Test
    @DisplayName("LV-CAPTURE-001: unity gain returns the original frame")
    fun lvCapture001_unityGainReturnsOriginal() {
        val frame = byteArrayOf(0x00, 0x10.toByte(), 0xFF.toByte(), 0x7F.toByte())

        val result = RealtimeAudioCaptureGain.amplifyPcm16Le(frame, gain = 1f)

        assertSame(frame, result)
    }

    @Test
    @DisplayName("LV-CAPTURE-002: gain amplifies quiet speech samples")
    fun lvCapture002_gainAmplifiesQuietSpeech() {
        val frame = byteArrayOf(0x64, 0x00, 0x9C.toByte(), 0xFF.toByte())

        val result = RealtimeAudioCaptureGain.amplifyPcm16Le(frame, gain = 2f)

        assertEquals(0xC8, result[0].toInt() and 0xFF)
        assertEquals(0x00, result[1].toInt() and 0xFF)
        assertEquals(0x38, result[2].toInt() and 0xFF)
        assertEquals(0xFF.toInt(), result[3].toInt() and 0xFF)
    }
}