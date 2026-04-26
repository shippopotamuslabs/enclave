package com.browntowndev.pocketcrew.feature.inference.tts.media

import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class GoogleMediaStreamServiceTest {

    private val logger: LoggingPort = mockk(relaxed = true)
    private lateinit var service: GoogleMediaStreamService

    @BeforeEach
    fun setUp() {
        service = GoogleMediaStreamService(
            modelsApiProvider = { mockk(relaxed = true) },
            logger = logger,
        )
    }

    @Test
    fun `expected MIME type is audio wav for Google batch`() {
        // Google TTS returns PCM wrapped in WAV, so the stream should report audio/wav
        assertEquals("audio/wav", service.expectedMimeType())
    }

    @Test
    fun `service delegates to GoogleTtsService synthesizeSpeech for batch`() = runTest {
        // Google does not support streaming; it delegates to the batch API
        // and returns WAV bytes as a ByteArrayInputStream.
        // The actual call verification is in GoogleTtsServiceTest.
        assertEquals("batch", service.streamingMode())
    }
}