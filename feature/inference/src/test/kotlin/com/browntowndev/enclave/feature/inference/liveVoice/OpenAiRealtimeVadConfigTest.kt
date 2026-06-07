package com.browntowndev.enclave.feature.inference.liveVoice

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class OpenAiRealtimeVadConfigTest {
    @Test
    @DisplayName("LV-VAD-001: server VAD tuned for quiet speech pickup")
    fun lvVad001_serverVadTunedForQuietSpeech() {
        assertEquals(0.35, OpenAiRealtimeSessionVadConfig.threshold)
        assertEquals(400, OpenAiRealtimeSessionVadConfig.prefixPaddingMs)
        assertEquals(900, OpenAiRealtimeSessionVadConfig.silenceDurationMs)
    }
}