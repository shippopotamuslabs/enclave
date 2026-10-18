package com.browntowndev.enclave.feature.inference.media

import com.browntowndev.enclave.core.testing.FakeApiKeyProvider
import com.browntowndev.enclave.domain.model.config.MediaCapability
import com.browntowndev.enclave.domain.model.config.MediaProviderAsset
import com.browntowndev.enclave.domain.model.config.MediaProviderId
import com.browntowndev.enclave.domain.model.inference.ApiProvider
import com.browntowndev.enclave.domain.model.media.MusicGenerationSettings
import com.browntowndev.enclave.feature.inference.FakeFalMusicGenerationAdapter
import com.browntowndev.enclave.feature.inference.FakeGoogleMusicGenerationAdapter
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MusicGenerationIntegrationTest {
    private val apiKeyProvider = FakeApiKeyProvider()
    private val googleAdapter = FakeGoogleMusicGenerationAdapter()
    private val falAdapter = FakeFalMusicGenerationAdapter()
    private val musicPort = MusicGenerationPortImpl(apiKeyProvider, googleAdapter, falAdapter)

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

            apiKeyProvider.saveApiKey("google-alias", "test-key")
            googleAdapter.result = Result.success(expectedBytes)

            // When
            val result = musicPort.generateMusic(prompt, provider, settings)

            // Then
            assertTrue(result.isSuccess)
            assertArrayEquals(expectedBytes, result.getOrNull())
            val call = googleAdapter.calls.single()
            assertEquals(prompt, call.prompt)
            assertEquals("test-key", call.apiKey)
            assertEquals("lyria-3-clip-preview", call.modelId)
            assertEquals(settings, call.settings)
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

            apiKeyProvider.saveApiKey("google-alias", "test-key")

            val result = musicPort.generateMusic(prompt, provider, settings)

            assertTrue(result.isFailure)
            assertEquals(
                "No model configured for provider: MediaProviderId(value=google)",
                result.exceptionOrNull()?.message,
            )
        }

    @Test
    fun `generateMusic delegates to fal adapter when provider is fal`() =
        runTest {
            val prompt = "test music"
            val settings = MusicGenerationSettings()
            val provider =
                MediaProviderAsset(
                    id = MediaProviderId("fal"),
                    displayName = "fal.ai",
                    provider = ApiProvider.FAL,
                    capability = MediaCapability.MUSIC,
                    modelName = "fal-ai/stable-audio",
                    credentialAlias = "fal-alias",
                )
            val expectedBytes = "fal music bytes".toByteArray()

            apiKeyProvider.saveApiKey("fal-alias", "test-key")
            falAdapter.result = Result.success(expectedBytes)

            val result = musicPort.generateMusic(prompt, provider, settings)

            assertTrue(result.isSuccess)
            assertArrayEquals(expectedBytes, result.getOrNull())
            val call = falAdapter.calls.single()
            assertEquals(prompt, call.prompt)
            assertEquals("test-key", call.apiKey)
            assertEquals("fal-ai/stable-audio", call.modelId)
            assertEquals(settings, call.settings)
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

            apiKeyProvider.saveApiKey("openai-alias", "test-key")

            val result = musicPort.generateMusic(prompt, provider, settings)

            assertTrue(result.isFailure)
            assertEquals("Music generation not supported for provider: OPENAI", result.exceptionOrNull()?.message)
        }

    @Test
    fun `generateMusic uses default model when FAL provider has no model configured`() =
        runTest {
            val prompt = "test music"
            val settings = MusicGenerationSettings()
            val provider =
                MediaProviderAsset(
                    id = MediaProviderId("fal"),
                    displayName = "fal.ai",
                    provider = ApiProvider.FAL,
                    capability = MediaCapability.MUSIC,
                    credentialAlias = "fal-alias",
                )
            val expectedBytes = "fal music bytes".toByteArray()

            apiKeyProvider.saveApiKey("fal-alias", "test-key")
            falAdapter.result = Result.success(expectedBytes)

            val result = musicPort.generateMusic(prompt, provider, settings)

            assertTrue(result.isSuccess)
            assertArrayEquals(expectedBytes, result.getOrNull())
            val call = falAdapter.calls.single()
            assertEquals(prompt, call.prompt)
            assertEquals("test-key", call.apiKey)
            assertEquals("fal-ai/stable-audio", call.modelId)
            assertEquals(settings, call.settings)
        }
}
