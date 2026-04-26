package com.browntowndev.pocketcrew.app.tts

import com.browntowndev.pocketcrew.domain.model.inference.ApiProvider
import com.browntowndev.pocketcrew.domain.port.media.TtsPlaybackRequest
import com.browntowndev.pocketcrew.domain.port.media.TtsPlaybackStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class InMemoryTtsPlaybackRegistryTest {
    private lateinit var registry: InMemoryTtsPlaybackRegistry

    private val testRequest =
        TtsPlaybackRequest(
            requestId = "test-req-1",
            text = "Hello world",
            provider = ApiProvider.OPENAI,
            voiceName = "alloy",
            modelName = "tts-1",
            baseUrl = null,
            credentialAlias = "openai-key",
        )

    @BeforeEach
    fun setUp() {
        registry = InMemoryTtsPlaybackRegistry()
    }

    @Test
    fun `register and resolve request`() {
        registry.register(testRequest)

        val resolved = registry.resolve("test-req-1")
        assertNotNull(resolved)
        assertEquals("Hello world", resolved!!.text)
        assertEquals(ApiProvider.OPENAI, resolved.provider)
    }

    @Test
    fun `resolve returns null for unknown request`() {
        val resolved = registry.resolve("nonexistent")
        assertNull(resolved)
    }

    @Test
    fun `remove request`() {
        registry.register(testRequest)
        assertTrue(registry.remove("test-req-1"))
        assertNull(registry.resolve("test-req-1"))
    }

    @Test
    fun `remove returns false for unknown request`() {
        assertTrue(!registry.remove("nonexistent"))
    }

    @Test
    fun `clear removes all requests`() {
        val request2 =
            TtsPlaybackRequest(
                requestId = "test-req-2",
                text = "Goodbye",
                provider = ApiProvider.XAI,
                voiceName = "eve",
                modelName = null,
                baseUrl = null,
                credentialAlias = "xai-key",
            )
        registry.register(testRequest)
        registry.register(request2)

        registry.clear()

        assertNull(registry.resolve("test-req-1"))
        assertNull(registry.resolve("test-req-2"))
    }

    @Test
    fun `register multiple requests and resolve each`() {
        val request2 =
            TtsPlaybackRequest(
                requestId = "test-req-2",
                text = "Second request",
                provider = ApiProvider.GOOGLE,
                voiceName = "Puck",
                modelName = "gemini-3.1-flash-tts-preview",
                baseUrl = null,
                credentialAlias = "google-key",
            )
        registry.register(testRequest)
        registry.register(request2)

        val resolved1 = registry.resolve("test-req-1")
        val resolved2 = registry.resolve("test-req-2")

        assertNotNull(resolved1)
        assertNotNull(resolved2)
        assertEquals("Hello world", resolved1!!.text)
        assertEquals("Second request", resolved2!!.text)
    }

    @Test
    fun `observeStatus returns Initializing for registered request`() =
        runTest {
            registry.register(testRequest)

            val status = registry.observeStatus("test-req-1")?.first()

            assertEquals(TtsPlaybackStatus.Initializing, status)
        }

    @Test
    fun `publishStatus updates observed request status`() =
        runTest {
            registry.register(testRequest)
            val statuses = registry.observeStatus("test-req-1")

            assertTrue(registry.publishStatus("test-req-1", TtsPlaybackStatus.Playing))

            assertEquals(TtsPlaybackStatus.Playing, statuses?.first())
        }

    @Test
    fun `publishStatus returns false for unknown request`() {
        assertTrue(!registry.publishStatus("missing", TtsPlaybackStatus.Playing))
    }

    @Test
    fun `observeStatus returns null for unknown request`() {
        assertNull(registry.observeStatus("missing"))
    }
}
