package com.browntowndev.enclave.feature.inference.liveVoice

import com.browntowndev.enclave.domain.port.media.LiveVoiceEvent
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class GeminiLiveEventParsingTest {
    @Test
    @DisplayName("LV-PARSE-GEM-001: setupComplete maps to Connected")
    fun lvParseGem001_setupComplete() {
        val json =
            """
            {"setupComplete":{}}
            """.trimIndent()

        val result = parseGeminiLiveEvent(json)

        assertEquals(LiveVoiceEvent.Connected, result)
    }

    @Test
    @DisplayName("LV-PARSE-GEM-002: inline audio data maps to AssistantAudioChunk")
    fun lvParseGem002_inlineAudioData() {
        val json =
            """
            {"serverContent":{"modelTurn":{"parts":[{"inlineData":{"mimeType":"audio/pcm","data":"AAEC"}}]}}}
            """.trimIndent()

        val result = parseGeminiLiveEvent(json)

        assertTrue(result is LiveVoiceEvent.AssistantAudioChunk)
        val chunk = result as LiveVoiceEvent.AssistantAudioChunk
        assertArrayEquals(byteArrayOf(0, 1, 2), chunk.pcm16)
    }

    @Test
    @DisplayName("LV-PARSE-GEM-004: turnComplete maps to AssistantResponseComplete")
    fun lvParseGem004_turnComplete() {
        val json =
            """
            {"serverContent":{"turnComplete":true}}
            """.trimIndent()

        val result = parseGeminiLiveEvent(json)

        assertEquals(LiveVoiceEvent.AssistantResponseComplete, result)
    }

    @Test
    @DisplayName("LV-PARSE-GEM-003: output transcription maps to AssistantTranscriptDelta")
    fun lvParseGem003_outputTranscription() {
        val json =
            """
            {"serverContent":{"outputTranscription":{"text":"hello"}}}
            """.trimIndent()

        val result = parseGeminiLiveEvent(json)

        assertEquals(LiveVoiceEvent.AssistantTranscriptDelta("hello"), result)
    }
}