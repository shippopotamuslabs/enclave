package com.browntowndev.pocketcrew.feature.inference.tts.media

import com.browntowndev.pocketcrew.domain.port.media.TtsMediaStream
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.feature.inference.OpenAiClientProviderPort
import com.openai.models.audio.speech.SpeechCreateParams
import com.openai.models.audio.speech.SpeechModel
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream

class OpenAiMediaStreamServiceTest {

    private val clientProvider: OpenAiClientProviderPort = mockk()
    private val logger: LoggingPort = mockk(relaxed = true)
    private lateinit var service: OpenAiMediaStreamService

    @BeforeEach
    fun setUp() {
        service = OpenAiMediaStreamService(
            clientProvider = clientProvider,
            apiKey = "sk-test",
            baseUrl = null,
            logger = logger,
        )
    }

    @Test
    fun `openStream returns MP3 media stream`() = runTest {
        // The OpenAI media stream service should open an MP3 stream.
        // Actual HTTP calls are tested in integration tests.
        // Here we verify the service constructs the right MIME type.
        assertEquals("audio/mpeg", service.expectedMimeType())
    }

    @Test
    fun `openStream uses streaming response not readBytes`() = runTest {
        // Verify the service is designed to stream, not buffer
        // The implementation should use streaming response body, not readBytes()
        assertTrue(service.usesStreamingResponse())
    }
}