package com.browntowndev.enclave.feature.inference.media

import com.browntowndev.enclave.domain.model.config.MediaCapability
import com.browntowndev.enclave.domain.model.config.MediaProviderAsset
import com.browntowndev.enclave.domain.model.config.MediaProviderId
import com.browntowndev.enclave.domain.model.inference.ApiProvider
import com.browntowndev.enclave.domain.model.media.MusicGenerationSettings
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MusicGenerationIntegrationTest {
    private val apiKeyProvider = mockk<com.browntowndev.enclave.domain.port.security.ApiKeyProviderPort>()
    private val googleAdapter = mockk<GoogleMusicGenerationAdapter>()
    private val musicPort = MusicGenerationPortImpl(apiKeyProvider, googleAdapter)

    @Test
    fun `generateMusic delegates to Google adapter when provider is Google`() =
        runTest {
            // Given
            val prompt = "test music"
            val settings = MusicGenerationSettings()
            val provider =
                MediaProviderAsset(
                    id = MediaProviderId("google"),
                    displayName = "Google",
                    provider = ApiProvider.GOOGLE,
                    capability = MediaCapability.MUSIC,
                    modelName = "lyria-3-clip-preview",
                    credentialAlias = "google-alias",
                )
            val expectedBytes = "music bytes".toByteArray()

            coEvery { apiKeyProvider.getApiKey("google-alias") } returns "test-key"
            coEvery {
                googleAdapter.generateMusic(prompt, "test-key", "lyria-3-clip-preview", any(), settings)
            } returns Result.success(expectedBytes)

            // When
            val result = musicPort.generateMusic(prompt, provider, settings)

            // Then
            assertTrue(result.isSuccess)
            assertArrayEquals(expectedBytes, result.getOrNull())
        }

    @Test
    fun `generateMusic returns failure when Google provider has no model configured`() =
        runTest {
            val prompt = "test music"
            val settings = MusicGenerationSettings()
            val provider =
                MediaProviderAsset(
                    id = MediaProviderId("google"),
                    displayName = "Google",
                    provider = ApiProvider.GOOGLE,
                    capability = MediaCapability.MUSIC,
                    credentialAlias = "google-alias",
                )

            coEvery { apiKeyProvider.getApiKey("google-alias") } returns "test-key"

            val result = musicPort.generateMusic(prompt, provider, settings)

            assertTrue(result.isFailure)
            assertEquals(
                "No model configured for provider: MediaProviderId(value=google)",
                result.exceptionOrNull()?.message,
            )
        }

    @Test
    fun `generateMusic returns failure for unsupported provider`() =
        runTest {
            val prompt = "test music"
            val settings = MusicGenerationSettings()
            val provider =
                MediaProviderAsset(
                    id = MediaProviderId("openai"),
                    displayName = "OpenAI",
                    provider = ApiProvider.OPENAI,
                    capability = MediaCapability.MUSIC,
                    credentialAlias = "openai-alias",
                )

            coEvery { apiKeyProvider.getApiKey("openai-alias") } returns "test-key"

            val result = musicPort.generateMusic(prompt, provider, settings)

            assertTrue(result.isFailure)
            assertEquals("Music generation not supported for provider: OPENAI", result.exceptionOrNull()?.message)
        }
}
