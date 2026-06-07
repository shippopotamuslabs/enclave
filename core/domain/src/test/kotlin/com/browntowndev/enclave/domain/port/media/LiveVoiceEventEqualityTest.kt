package com.browntowndev.enclave.domain.port.media

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class LiveVoiceEventEqualityTest {
    @Test
    @DisplayName("LV-EVENT-EQ-001")
    fun lvEventEq001_assistantAudioChunkUsesContentEquality() {
        val a = LiveVoiceEvent.AssistantAudioChunk(byteArrayOf(1, 2, 3))
        val b = LiveVoiceEvent.AssistantAudioChunk(byteArrayOf(1, 2, 3))

        assertTrue(a == b)
        assertEquals(a.hashCode(), b.hashCode())
    }
}