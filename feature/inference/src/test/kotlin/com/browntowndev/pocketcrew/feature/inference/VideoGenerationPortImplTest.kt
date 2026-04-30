package com.browntowndev.pocketcrew.feature.inference

import com.browntowndev.pocketcrew.domain.model.config.MediaCapability
import com.browntowndev.pocketcrew.domain.model.config.MediaProviderAsset
import com.browntowndev.pocketcrew.domain.model.config.MediaProviderId
import com.browntowndev.pocketcrew.domain.model.inference.ApiProvider
import com.browntowndev.pocketcrew.domain.model.media.VideoGenerationSettings
import com.browntowndev.pocketcrew.domain.port.repository.StudioRepositoryPort
import com.browntowndev.pocketcrew.domain.port.security.ApiKeyProviderPort
import com.browntowndev.pocketcrew.feature.inference.media.GoogleVideoGenerationAdapter
import com.browntowndev.pocketcrew.feature.inference.media.OpenAiVideoGenerationAdapter
import com.browntowndev.pocketcrew.feature.inference.media.VideoGenerationPortImpl
import com.browntowndev.pocketcrew.feature.inference.media.XaiVideoGenerationAdapter
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class VideoGenerationPortImplTest {
    private val apiKeyProvider = mockk<ApiKeyProviderPort>()
    private val studioRepository = mockk<StudioRepositoryPort>()
    private val openAiAdapter = mockk<OpenAiVideoGenerationAdapter>()
    private val googleAdapter = mockk<GoogleVideoGenerationAdapter>()
    private val xaiAdapter = mockk<XaiVideoGenerationAdapter>()
    private lateinit var port: VideoGenerationPortImpl

    @BeforeEach
    fun setup() {
        port = VideoGenerationPortImpl(
            apiKeyProvider = apiKeyProvider,
            studioRepository = studioRepository,
            openAiAdapter = openAiAdapter,
            googleAdapter = googleAdapter,
            xaiAdapter = xaiAdapter,
        )
        every { apiKeyProvider.getApiKey("alias") } returns "key"
        coEvery { studioRepository.readMediaBytes("cache://reference.png") } returns "png".toByteArray()
    }

    @Test
    fun generateVideo_missingApiKey_returnsFailure() = runTest {
        every { apiKeyProvider.getApiKey("alias") } returns null

        val result = port.generateVideo("prompt", provider(ApiProvider.OPENAI), VideoGenerationSettings())

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("API key not found") == true)
    }

    @Test
    fun generateVideo_openAiProvider_loadsReferenceAndUsesDefaults() = runTest {
        coEvery {
            openAiAdapter.generateVideo(any(), any(), any(), any(), any(), any())
        } returns Result.success("mp4".toByteArray())

        val result = port.generateVideo(
            prompt = "prompt",
            provider = provider(ApiProvider.OPENAI, modelName = null),
            settings = VideoGenerationSettings(referenceImageUri = "cache://reference.png"),
        )

        assertTrue(result.isSuccess)
        assertArrayEquals("mp4".toByteArray(), result.getOrThrow())
        coVerify {
            openAiAdapter.generateVideo(
                prompt = "prompt",
                apiKey = "key",
                modelId = "sora-2",
                baseUrl = null,
                settings = VideoGenerationSettings(referenceImageUri = "cache://reference.png"),
                referenceImage = match { it.contentEquals("png".toByteArray()) },
            )
        }
    }

    @Test
    fun generateVideo_googleProvider_routesToGoogleAdapter() = runTest {
        coEvery {
            googleAdapter.generateVideo(any(), any(), any(), any(), any(), any())
        } returns Result.success("google".toByteArray())

        val result = port.generateVideo("prompt", provider(ApiProvider.GOOGLE), VideoGenerationSettings())

        assertTrue(result.isSuccess)
        assertArrayEquals("google".toByteArray(), result.getOrThrow())
        coVerify {
            googleAdapter.generateVideo(
                prompt = "prompt",
                apiKey = "key",
                modelId = "custom-model",
                baseUrl = null,
                settings = VideoGenerationSettings(),
                referenceImage = null,
            )
        }
    }

    @Test
    fun generateVideo_xaiProvider_routesToXaiAdapter() = runTest {
        coEvery {
            xaiAdapter.generateVideo(any(), any(), any(), any(), any(), any())
        } returns Result.success("xai".toByteArray())

        val result = port.generateVideo("prompt", provider(ApiProvider.XAI), VideoGenerationSettings())

        assertTrue(result.isSuccess)
        assertArrayEquals("xai".toByteArray(), result.getOrThrow())
        coVerify { xaiAdapter.generateVideo("prompt", "key", "custom-model", null, VideoGenerationSettings(), null) }
    }

    @Test
    fun generateVideo_unsupportedProvider_returnsFailure() = runTest {
        val result = port.generateVideo("prompt", provider(ApiProvider.ANTHROPIC), VideoGenerationSettings())

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is UnsupportedOperationException)
    }

    private fun provider(
        provider: ApiProvider,
        modelName: String? = "custom-model",
    ): MediaProviderAsset =
        MediaProviderAsset(
            id = MediaProviderId("provider"),
            displayName = provider.displayName,
            provider = provider,
            capability = MediaCapability.VIDEO,
            modelName = modelName,
            credentialAlias = "alias",
        )
}
