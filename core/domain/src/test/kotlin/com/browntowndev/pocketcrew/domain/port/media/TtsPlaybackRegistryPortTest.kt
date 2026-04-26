package com.browntowndev.pocketcrew.domain.port.media

import com.browntowndev.pocketcrew.domain.model.config.TtsProviderAsset
import com.browntowndev.pocketcrew.domain.model.config.TtsProviderId
import com.browntowndev.pocketcrew.domain.model.inference.ApiProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TtsPlaybackRegistryPortTest {

    // Note: We test against the interface contract. The concrete InMemoryTtsPlaybackRegistry
    // will be tested in the app module where it's implemented, but we define the
    // expected behavior here as a reference.

    // Since we can't instantiate the interface, this test file serves as the
    // specification for the InMemoryTtsPlaybackRegistry implementation in :app.
    // The actual tests against the concrete impl are in the app module.

    private val testRequest = TtsPlaybackRequest(
        requestId = "test-req-1",
        text = "Hello world",
        provider = ApiProvider.OPENAI,
        voiceName = "alloy",
        modelName = "tts-1",
        baseUrl = null,
        credentialAlias = "openai-key",
    )

    @Test
    fun `TtsPlaybackRequest URI format is correct`() {
        val uri = testRequest.toUriString()
        assertEquals("pocketcrew-tts://play/test-req-1", uri)
    }

    @Test
    fun `toPlaybackRequest from TtsProviderAsset preserves fields`() {
        val asset = TtsProviderAsset(
            id = TtsProviderId("xai-1"),
            displayName = "xAI TTS",
            provider = ApiProvider.XAI,
            voiceName = "eve",
            modelName = "grok-tts",
            baseUrl = "https://api.x.ai",
            credentialAlias = "xai-key",
        )

        val request = asset.toPlaybackRequest(requestId = "r-2", text = "Test")

        assertEquals("r-2", request.requestId)
        assertEquals("Test", request.text)
        assertEquals(ApiProvider.XAI, request.provider)
        assertEquals("eve", request.voiceName)
        assertEquals("grok-tts", request.modelName)
        assertEquals("https://api.x.ai", request.baseUrl)
        assertEquals("xai-key", request.credentialAlias)
        assertEquals("xAI TTS", request.notificationArtist)
    }
}
