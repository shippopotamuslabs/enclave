package com.browntowndev.enclave.feature.inference.tts.media

import com.browntowndev.enclave.core.testing.FakeLoggingPort
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import com.google.genai.Client
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class GoogleMediaStreamServiceTest {
    private val logger: LoggingPort = FakeLoggingPort()
    private lateinit var service: GoogleMediaStreamService

    @BeforeEach
    fun setUp() {
        service =
            GoogleMediaStreamService(
                // Never invoked by these tests; built lazily only if a stream is opened.
                modelsApiProvider = { Client.builder().apiKey("test").build().models },
                logger = logger,
            )
    }

    @Test
    fun `expected MIME type is audio wav for Google batch`() {
        // Google TTS returns PCM wrapped in WAV, so the stream should report audio/wav
        assertEquals("audio/wav", service.expectedMimeType())
    }

    @Test
    fun `service delegates to GoogleTtsService synthesizeSpeech for batch`() =
        runTest {
            // Google does not support streaming; it delegates to the batch API
            // and returns WAV bytes as a ByteArrayInputStream.
            // The actual call verification is in GoogleTtsServiceTest.
            assertEquals("batch", service.streamingMode())
        }
}
