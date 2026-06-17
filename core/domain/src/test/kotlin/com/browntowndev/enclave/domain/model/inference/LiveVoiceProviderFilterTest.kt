package com.browntowndev.enclave.domain.model.inference

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class LiveVoiceProviderFilterTest {
    @Test
    @DisplayName("LV-PROVIDER-FILTER-001")
    fun lvProviderFilter001_realtimeCapableProviders() {
        assertTrue(ApiProvider.OPENAI.supportsRealtimeVoice)
        assertTrue(ApiProvider.GOOGLE.supportsRealtimeVoice)
        assertTrue(ApiProvider.XAI.supportsRealtimeVoice)
    }

    @Test
    @DisplayName("LV-PROVIDER-FILTER-002")
    fun lvProviderFilter002_nonRealtimeProviders() {
        assertFalse(ApiProvider.ANTHROPIC.supportsRealtimeVoice)
        assertFalse(ApiProvider.OPENROUTER.supportsRealtimeVoice)
        assertFalse(ApiProvider.FAL.supportsRealtimeVoice)
    }
}