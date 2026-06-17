package com.browntowndev.enclave.feature.inference.liveVoice

import com.browntowndev.enclave.domain.model.inference.ApiProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class OpenAiRealtimeVadConfigTest {
    @Test
    @DisplayName("LV-VAD-001: OpenAI server VAD tuned for quiet speech pickup")
    fun lvVad001_openAiServerVadTunedForQuietSpeech() {
        val config = OpenAiRealtimeSessionVadConfig.forProvider(ApiProvider.OPENAI)

        assertEquals(0.35, config.threshold)
        assertEquals(400, config.prefixPaddingMs)
        assertEquals(900, config.silenceDurationMs)
    }

    @Test
    @DisplayName("LV-VAD-002: xAI server VAD uses documented defaults")
    fun lvVad002_xAiServerVadUsesDocumentedDefaults() {
        val config = OpenAiRealtimeSessionVadConfig.forProvider(ApiProvider.XAI)

        assertEquals(0.85, config.threshold)
        assertEquals(333, config.prefixPaddingMs)
        assertEquals(900, config.silenceDurationMs)
    }
}