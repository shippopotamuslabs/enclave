package com.browntowndev.enclave.domain.model.inference

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class LiveVoiceModelTypeTest {
    @Test
    @DisplayName("LV-MODELTYPE-001")
    fun lvModelType001_apiValueRoundTrip() {
        assertEquals("live_voice", ModelType.LIVE_VOICE.apiValue)
        assertEquals(ModelType.LIVE_VOICE, ModelType.fromApiValue("live_voice"))
    }

    @Test
    @DisplayName("LV-MODELTYPE-002")
    fun lvModelType002_displayNameAndDescription() {
        assertEquals("Live Voice", ModelType.LIVE_VOICE.displayName())
        assertEquals(
            "Realtime speech-to-speech voice conversation.",
            ModelType.LIVE_VOICE.description,
        )
    }

    @Test
    @DisplayName("LV-MODELTYPE-003")
    fun lvModelType003_isNotTextModel() {
        assertFalse(ModelType.LIVE_VOICE.isTextModel)
    }
}