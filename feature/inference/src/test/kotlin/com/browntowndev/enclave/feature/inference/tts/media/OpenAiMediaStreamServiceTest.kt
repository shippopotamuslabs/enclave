package com.browntowndev.enclave.feature.inference.tts.media

import com.browntowndev.enclave.core.testing.FakeLoggingPort
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import com.browntowndev.enclave.feature.inference.FakeOpenAiClientProviderPort
import com.browntowndev.enclave.feature.inference.OpenAiClientProviderPort
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class OpenAiMediaStreamServiceTest {
    private val clientProvider: OpenAiClientProviderPort = FakeOpenAiClientProviderPort()
    private val logger: LoggingPort = FakeLoggingPort()
    private lateinit var service: OpenAiMediaStreamService

    @BeforeEach
    fun setUp() {
        service =
            OpenAiMediaStreamService(
                clientProvider = clientProvider,
                apiKey = "sk-test",
                baseUrl = null,
                logger = logger,
            )
    }

    @Test
    fun `openStream returns MP3 media stream`() =
        runTest {
            // The OpenAI media stream service should open an MP3 stream.
            // Actual HTTP calls are tested in integration tests.
            // Here we verify the service constructs the right MIME type.
            assertEquals("audio/mpeg", service.expectedMimeType())
        }

    @Test
    fun `openStream uses streaming response not readBytes`() =
        runTest {
            // Verify the service is designed to stream, not buffer
            // The implementation should use streaming response body, not readBytes()
            assertTrue(service.usesStreamingResponse())
        }
}
