package com.browntowndev.enclave.feature.inference.liveVoice

import com.browntowndev.enclave.domain.port.media.LiveVoiceEvent
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class OpenAiRealtimeEventParsingTest {
    @Test
    @DisplayName("LV-PARSE-OAI-001: user transcript completed maps to UserTranscriptDone")
    fun lvParseOai001_userTranscriptCompleted() {
        val json =
            """
            {"type":"conversation.item.input_audio_transcription.completed","transcript":"turn on the lights"}
            """.trimIndent()

        val result = parseOpenAiRealtimeEvent(json)

        assertEquals(LiveVoiceEvent.UserTranscriptDone("turn on the lights"), result)
    }

    @Test
    @DisplayName("LV-PARSE-OAI-002: audio delta maps to AssistantAudioChunk with decoded bytes")
    fun lvParseOai002_audioDeltaDecoded() {
        val json =
            """
            {"type":"response.audio.delta","delta":"AAEC"}
            """.trimIndent()

        val result = parseOpenAiRealtimeEvent(json)

        assertTrue(result is LiveVoiceEvent.AssistantAudioChunk)
        val chunk = result as LiveVoiceEvent.AssistantAudioChunk
        assertArrayEquals(byteArrayOf(0, 1, 2), chunk.pcm16)
    }

    @Test
    @DisplayName("LV-PARSE-OAI-003: speech_started maps to SpeechStarted")
    fun lvParseOai003_speechStarted() {
        val json =
            """
            {"type":"input_audio_buffer.speech_started"}
            """.trimIndent()

        val result = parseOpenAiRealtimeEvent(json)

        assertEquals(LiveVoiceEvent.SpeechStarted, result)
    }

    @Test
    @DisplayName("LV-PARSE-OAI-004: error event maps to Error(message)")
    fun lvParseOai004_errorEvent() {
        val json =
            """
            {"type":"error","error":{"message":"invalid_api_key"}}
            """.trimIndent()

        val result = parseOpenAiRealtimeEvent(json)

        assertEquals(LiveVoiceEvent.Error("invalid_api_key"), result)
    }

    @Test
    @DisplayName("LV-PARSE-OAI-005: transcript delta maps to UserTranscriptDelta")
    fun lvParseOai005_transcriptDelta() {
        val json =
            """
            {"type":"conversation.item.input_audio_transcription.delta","delta":"lights"}
            """.trimIndent()

        val result = parseOpenAiRealtimeEvent(json)

        assertEquals(LiveVoiceEvent.UserTranscriptDelta("lights"), result)
    }

    @Test
    @DisplayName("LV-PARSE-XAI-001: cumulative transcription.updated maps to UserTranscriptDone")
    fun lvParseXai001_cumulativeTranscriptUpdated() {
        val json =
            """
            {"type":"conversation.item.input_audio_transcription.updated","transcript":"turn on the"}
            """.trimIndent()

        val result = parseOpenAiRealtimeEvent(json)

        assertEquals(LiveVoiceEvent.UserTranscriptDone("turn on the"), result)
    }

    @Test
    @DisplayName("LV-PARSE-OAI-006: response.done maps to AssistantResponseComplete")
    fun lvParseOai006_responseDone() {
        val json =
            """
            {"type":"response.done"}
            """.trimIndent()

        val result = parseOpenAiRealtimeEvent(json)

        assertEquals(LiveVoiceEvent.AssistantResponseComplete, result)
    }

    @Test
    @DisplayName("LV-PARSE-OAI-007: response.output_audio.done maps to AssistantResponseComplete")
    fun lvParseOai007_outputAudioDone() {
        val json =
            """
            {"type":"response.output_audio.done"}
            """.trimIndent()

        val result = parseOpenAiRealtimeEvent(json)

        assertEquals(LiveVoiceEvent.AssistantResponseComplete, result)
    }

    @Test
    @DisplayName("LV-PARSE-XAI-002: output audio delta maps to AssistantAudioChunk")
    fun lvParseXai002_outputAudioDeltaDecoded() {
        val json =
            """
            {"type":"response.output_audio.delta","delta":"AAEC"}
            """.trimIndent()

        val result = parseOpenAiRealtimeEvent(json)

        assertTrue(result is LiveVoiceEvent.AssistantAudioChunk)
        val chunk = result as LiveVoiceEvent.AssistantAudioChunk
        assertArrayEquals(byteArrayOf(0, 1, 2), chunk.pcm16)
    }
}