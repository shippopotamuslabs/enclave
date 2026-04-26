package com.browntowndev.pocketcrew.feature.inference.tts.media

import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class XAiMediaStreamServiceTest {

    private val logger: LoggingPort = mockk(relaxed = true)
    private lateinit var service: XAiMediaStreamService

    @BeforeEach
    fun setUp() {
        service = XAiMediaStreamService(
            httpClient = mockk(),
            apiKey = "xai-test",
            logger = logger,
        )
    }

    @Test
    fun `expected MIME type is audio mpeg for MP3 codec`() {
        // xAI TTS uses codec=mp3 so the stream should report audio/mpeg
        assertEquals("audio/mpeg", service.expectedMimeType())
    }

    @Test
    fun `service uses WebSocket streaming for audio deltas`() = runTest {
        // Verify the service is designed to use WebSocket-based streaming
        // with codec=mp3 for direct ExoPlayer consumption
        assertTrue(service.usesWebSocketStreaming())
    }
}