package com.browntowndev.pocketcrew.feature.inference.tts

import com.google.genai.Models
import com.google.genai.types.Blob
import com.google.genai.types.Candidate
import com.google.genai.types.Content
import com.google.genai.types.GenerateContentConfig
import com.google.genai.types.GenerateContentResponse
import com.google.genai.types.Part
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GoogleTtsServiceTest {

    @Test
    fun `synthesizeSpeech uses Gemini SDK for Gemini voices`() = runBlocking {
        val models = mockk<Models>(relaxed = true)

        val voice = "Puck"
        val text = "Hello"
        val modelId = "gemini-3.1-flash-tts-preview"
        val expectedAudio = byteArrayOf(1, 2, 3)

        val response = GenerateContentResponse.builder()
            .candidates(
                listOf(
                    Candidate.builder()
                        .content(
                            Content.builder()
                                .parts(
                                    listOf(
                                        Part.builder()
                                            .inlineData(
                                                Blob.builder()
                                                    .data(expectedAudio)
                                                    .mimeType("audio/mpeg")
                                                    .build()
                                            )
                                            .build()
                                    )
                                )
                                .build()
                        )
                        .build()
                )
            )
            .build()

        every {
            models.generateContent(any<String>(), any<String>(), any<GenerateContentConfig>())
        } returns response

        val service = GoogleTtsService { models }
        val result = service.synthesizeSpeech(text, voice, modelId)

        assertTrue(result.isSuccess, "Result should be success: ${result.exceptionOrNull()}")
        val audioBytes = result.getOrNull()!!
        // Gemini TTS output is wrapped in a WAV/RIFF header (44 bytes) before returning
        assertTrue(
            audioBytes.size >= 44 + expectedAudio.size,
            "Output should contain WAV header + PCM data"
        )
        assertEquals("RIFF", String(audioBytes.copyOfRange(0, 4), Charsets.US_ASCII))
        assertEquals("WAVE", String(audioBytes.copyOfRange(8, 12), Charsets.US_ASCII))
        assertArrayEquals(expectedAudio, audioBytes.copyOfRange(44, audioBytes.size))
    }

    @Test
    fun `synthesizeSpeech falls back to Puck for legacy Cloud TTS voice format`() = runBlocking {
        val models = mockk<Models>(relaxed = true)
        val expectedAudio = byteArrayOf(4, 5, 6)
        val actualVoiceArg = mutableListOf<String>()

        every {
            models.generateContent(any<String>(), any<String>(), any<GenerateContentConfig>())
        } answers {
            val configArg = arg<GenerateContentConfig>(2)
            val voiceName = configArg.speechConfig()
                .flatMap { it.voiceConfig() }
                .flatMap { it.prebuiltVoiceConfig() }
                .map { it.voiceName().orElse("unknown") }
                .orElse("unknown")
            actualVoiceArg.add(voiceName)
            GenerateContentResponse.builder()
                .candidates(
                    listOf(
                        Candidate.builder()
                            .content(
                                Content.builder()
                                    .parts(
                                        listOf(
                                            Part.builder()
                                                .inlineData(
                                                    Blob.builder()
                                                        .data(expectedAudio)
                                                        .mimeType("audio/mpeg")
                                                        .build()
                                                )
                                                .build()
                                        )
                                    )
                                    .build()
                            )
                            .build()
                    )
                )
                .build()
        }

        val service = GoogleTtsService { models }
        val result = service.synthesizeSpeech("Hello", "en-US:en-US-Neural2-D", "gemini-3.1-flash-tts-preview")

        assertTrue(result.isSuccess, "Result should be success: ${result.exceptionOrNull()}")
        assertEquals("Puck", actualVoiceArg.first())
        val audioBytes = result.getOrNull()!!
        assertEquals("RIFF", String(audioBytes.copyOfRange(0, 4), Charsets.US_ASCII))
        assertArrayEquals(expectedAudio, audioBytes.copyOfRange(44, audioBytes.size))
    }

    @Test
    fun `wrapPcmInWav produces a valid WAV header`() {
        // Use reflection to test the private helper
        val pcmData = byteArrayOf(0x01, 0x02, 0x03, 0x04)
        val service = GoogleTtsService { mockk() }
        val method = GoogleTtsService::class.java.getDeclaredMethod("wrapPcmInWav", ByteArray::class.java)
        method.isAccessible = true
        val wavBytes = method.invoke(service, pcmData) as ByteArray

        assertEquals(44 + pcmData.size, wavBytes.size)
        assertEquals("RIFF", String(wavBytes.copyOfRange(0, 4), Charsets.US_ASCII))
        assertEquals("WAVE", String(wavBytes.copyOfRange(8, 12), Charsets.US_ASCII))
        assertEquals("fmt ", String(wavBytes.copyOfRange(12, 16), Charsets.US_ASCII))
        assertEquals("data", String(wavBytes.copyOfRange(36, 40), Charsets.US_ASCII))
        assertArrayEquals(pcmData, wavBytes.copyOfRange(44, wavBytes.size))

        // Verify little-endian fmt values
        val buffer = java.nio.ByteBuffer.wrap(wavBytes).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        assertEquals(1, buffer.getShort(20).toInt()) // AudioFormat: PCM
        assertEquals(1, buffer.getShort(22).toInt()) // NumChannels: mono
        assertEquals(24000, buffer.getInt(24)) // SampleRate
        assertEquals(48000, buffer.getInt(28)) // ByteRate = 24000 * 1 * 2
        assertEquals(2, buffer.getShort(32).toInt()) // BlockAlign
        assertEquals(16, buffer.getShort(34).toInt()) // BitsPerSample
        assertEquals(pcmData.size, buffer.getInt(40)) // Subchunk2Size
    }
}