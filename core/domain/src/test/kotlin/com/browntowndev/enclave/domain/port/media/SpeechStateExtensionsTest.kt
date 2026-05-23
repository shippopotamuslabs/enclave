package com.browntowndev.enclave.domain.port.media

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SpeechStateExtensionsTest {
    @Test
    fun `isActiveVoiceInput is true during capture pipeline states`() {
        assertTrue(SpeechState.ModelLoading.isActiveVoiceInput)
        assertTrue(SpeechState.Listening(volume = 0f).isActiveVoiceInput)
        assertTrue(SpeechState.Transcribing.isActiveVoiceInput)
    }

    @Test
    fun `isActiveVoiceInput is false outside capture pipeline`() {
        assertFalse(SpeechState.Idle.isActiveVoiceInput)
        assertFalse(SpeechState.FinalText("hello").isActiveVoiceInput)
        assertFalse(SpeechState.Error("mic denied").isActiveVoiceInput)
    }
}
