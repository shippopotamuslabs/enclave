package com.browntowndev.pocketcrew.domain.port.media

import com.browntowndev.pocketcrew.domain.model.config.TtsProviderAsset
import com.browntowndev.pocketcrew.domain.model.config.TtsProviderId
import com.browntowndev.pocketcrew.domain.model.inference.ApiProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class TtsPlaybackRequestTest {

    private val testAsset = TtsProviderAsset(
        id = TtsProviderId("1"),
        displayName = "OpenAI TTS",
        provider = ApiProvider.OPENAI,
        voiceName = "alloy",
        modelName = "tts-1",
        baseUrl = null,
        credentialAlias = "openai-key",
    )

    @Test
    fun `toUriString constructs correct URI`() {
        val request = TtsPlaybackRequest(
            requestId = "abc-123",
            text = "Hello world",
            provider = ApiProvider.OPENAI,
            voiceName = "alloy",
            modelName = "tts-1",
            baseUrl = null,
            credentialAlias = "openai-key",
        )

        assertEquals("pocketcrew-tts://play/abc-123", request.toUriString())
    }

    @Test
    fun `toPlaybackRequest creates request from TtsProviderAsset`() {
        val request = testAsset.toPlaybackRequest(
            requestId = "xyz-456",
            text = "Test text",
        )

        assertEquals("xyz-456", request.requestId)
        assertEquals("Test text", request.text)
        assertEquals(ApiProvider.OPENAI, request.provider)
        assertEquals("alloy", request.voiceName)
        assertEquals("tts-1", request.modelName)
        assertNull(request.baseUrl)
        assertEquals("openai-key", request.credentialAlias)
        assertEquals(TtsPlaybackRequest.MIME_TYPE_MP3, request.audioMimeType)
        assertEquals("OpenAI TTS", request.notificationArtist)
    }

    @Test
    fun `request contains scheme and host constants`() {
        assertEquals("pocketcrew-tts", TtsPlaybackRequest.SCHEME)
        assertEquals("play", TtsPlaybackRequest.HOST)
    }

    @Test
    fun `default notification values are set`() {
        val request = TtsPlaybackRequest(
            requestId = "req-1",
            text = "Hello",
            provider = ApiProvider.XAI,
            voiceName = "eve",
            modelName = null,
            baseUrl = null,
            credentialAlias = "key",
        )

        assertEquals("Pocket Crew", request.notificationTitle)
        assertEquals("TTS", request.notificationArtist)
    }

    @Test
    fun `toPlaybackRequest uses WAV MIME type for Google providers`() {
        val request = testAsset.copy(provider = ApiProvider.GOOGLE).toPlaybackRequest(
            requestId = "google-req",
            text = "Google text",
        )

        assertEquals(TtsPlaybackRequest.MIME_TYPE_WAV, request.audioMimeType)
    }

    @Test
    fun `toPlaybackRequest uses MP3 MIME type for OpenAI and xAI providers`() {
        val openAiRequest = testAsset.copy(provider = ApiProvider.OPENAI).toPlaybackRequest(
            requestId = "openai-req",
            text = "OpenAI text",
        )
        val xAiRequest = testAsset.copy(provider = ApiProvider.XAI).toPlaybackRequest(
            requestId = "xai-req",
            text = "xAI text",
        )

        assertEquals(TtsPlaybackRequest.MIME_TYPE_MP3, openAiRequest.audioMimeType)
        assertEquals(TtsPlaybackRequest.MIME_TYPE_MP3, xAiRequest.audioMimeType)
    }
}
