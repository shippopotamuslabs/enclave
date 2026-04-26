package com.browntowndev.pocketcrew.app.tts

import com.browntowndev.pocketcrew.domain.model.inference.ApiProvider
import com.browntowndev.pocketcrew.domain.port.media.TtsPlaybackRequest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Tests for [TtsDataSource] URI parsing logic.
 *
 * The DataSource's `open`/`read`/`close` methods are tightly coupled to
 * Media3's `DataSpec`, `BaseDataSource`, and `android.net.Uri` — all
 * Android/framework classes. Per the project's testing contract (JUnit 5 + MockK),
 * those integration paths are covered by the TtsPlaybackService integration test
 * with Robolectric.
 *
 * This test class focuses on the pure-Kotlin URI parsing logic in
 * [TtsDataSource.extractRequestId], which is the core domain-relevant
 * behavior of the DataSource.
 */
class TtsDataSourceTest {
    @Test
    fun `extractRequestId returns ID from valid URI`() {
        val requestId = TtsDataSource.extractRequestId("pocketcrew-tts://play/abc-123")
        assertEquals("abc-123", requestId)
    }

    @Test
    fun `extractRequestId returns ID with complex UUID`() {
        val uuid = "550e8400-e29b-41d4-a716-446655440000"
        val requestId = TtsDataSource.extractRequestId("pocketcrew-tts://play/$uuid")
        assertEquals(uuid, requestId)
    }

    @Test
    fun `extractRequestId returns null for wrong scheme`() {
        val requestId = TtsDataSource.extractRequestId("https://play/test-id")
        assertNull(requestId)
    }

    @Test
    fun `extractRequestId returns null for wrong host`() {
        val requestId = TtsDataSource.extractRequestId("pocketcrew-tts://invalid/test-id")
        assertNull(requestId)
    }

    @Test
    fun `extractRequestId returns null for empty path`() {
        val requestId = TtsDataSource.extractRequestId("pocketcrew-tts://play/")
        assertNull(requestId)
    }

    @Test
    fun `extractRequestId returns null for null input`() {
        val requestId = TtsDataSource.extractRequestId(null)
        assertNull(requestId)
    }

    @Test
    fun `TtsPlaybackRequest toUriString produces expected format`() {
        val request =
            TtsPlaybackRequest(
                requestId = "test-req-1",
                text = "Hello world",
                provider = ApiProvider.OPENAI,
                voiceName = "alloy",
                modelName = "tts-1",
                baseUrl = null,
                credentialAlias = "openai-key",
            )
        val uriString = request.toUriString()
        assertTrue(uriString.startsWith("pocketcrew-tts://play/"))
        assertTrue(uriString.contains("test-req-1"))
    }

    @Test
    fun `TtsPlaybackRequest SCHEME and HOST are consistent`() {
        assertEquals("pocketcrew-tts", TtsPlaybackRequest.SCHEME)
        assertEquals("play", TtsPlaybackRequest.HOST)
    }

    @Test
    fun `extractRequestId round-trips with toUriString`() {
        val request =
            TtsPlaybackRequest(
                requestId = "round-trip-id",
                text = "Test",
                provider = ApiProvider.XAI,
                voiceName = "onyx",
                modelName = "grok-tts",
                baseUrl = "https://api.x.ai",
                credentialAlias = "xai-key",
            )
        val extracted = TtsDataSource.extractRequestId(request.toUriString())
        assertEquals("round-trip-id", extracted)
    }
}
