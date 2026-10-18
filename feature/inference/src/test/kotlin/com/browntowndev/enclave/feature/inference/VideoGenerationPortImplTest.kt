package com.browntowndev.enclave.feature.inference

import com.browntowndev.enclave.core.testing.FakeApiKeyProvider
import com.browntowndev.enclave.core.testing.FakeStudioRepository
import com.browntowndev.enclave.domain.model.config.MediaCapability
import com.browntowndev.enclave.domain.model.config.MediaProviderAsset
import com.browntowndev.enclave.domain.model.config.MediaProviderId
import com.browntowndev.enclave.domain.model.config.MediaTask
import com.browntowndev.enclave.domain.model.inference.ApiProvider
import com.browntowndev.enclave.domain.model.media.VideoGenerationSettings
import com.browntowndev.enclave.domain.port.security.ApiKeyProviderPort
import com.browntowndev.enclave.feature.inference.media.VideoGenerationPortImpl
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class VideoGenerationPortImplTest {
    private val apiKeyProvider = FakeApiKeyProvider()
    private val studioRepository = FakeStudioRepository()
    private val openAiAdapter = FakeOpenAiVideoGenerationAdapter()
    private val googleAdapter = FakeGoogleVideoGenerationAdapter()
    private val xaiAdapter = FakeXaiVideoGenerationAdapter()
    private val falAdapter = FakeFalVideoGenerationAdapter()
    private lateinit var port: VideoGenerationPortImpl

    @BeforeEach
    fun setup() {
        port = buildPort(apiKeyProvider)
        apiKeyProvider.saveApiKey("alias", "key")
        studioRepository.mediaBytes["cache://reference.png"] = "png".toByteArray()
    }

    private fun buildPort(apiKeyProvider: ApiKeyProviderPort): VideoGenerationPortImpl =
        VideoGenerationPortImpl(
            apiKeyProvider = apiKeyProvider,
            studioRepository = studioRepository,
            openAiAdapter = openAiAdapter,
            googleAdapter = googleAdapter,
            xaiAdapter = xaiAdapter,
            falAdapter = falAdapter,
        )

    @Test
    fun generateVideo_missingApiKey_returnsFailure() =
        runTest {
            port = buildPort(FakeApiKeyProvider())

            val result = port.generateVideo("prompt", provider(ApiProvider.OPENAI), VideoGenerationSettings())

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull()?.message?.contains("API key not found") == true)
        }

    @Test
    fun generateVideo_openAiProvider_loadsReferenceAndUsesDefaults() =
        runTest {
            openAiAdapter.result = Result.success("mp4".toByteArray())

            val result =
                port.generateVideo(
                    prompt = "prompt",
                    provider = provider(ApiProvider.OPENAI, modelName = null).copy(tasks = setOf(MediaTask.IMAGE_TO_VIDEO)),
                    settings = VideoGenerationSettings(referenceImageUri = "cache://reference.png"),
                )

            assertTrue(result.isSuccess)
            assertArrayEquals("mp4".toByteArray(), result.getOrThrow())
            val call = openAiAdapter.calls.single()
            assertEquals("prompt", call.prompt)
            assertEquals("key", call.apiKey)
            assertEquals("sora-2", call.modelId)
            assertEquals(null, call.baseUrl)
            assertEquals(VideoGenerationSettings(referenceImageUri = "cache://reference.png"), call.settings)
            assertTrue(call.referenceImage?.contentEquals("png".toByteArray()) == true)
        }

    @Test
    fun generateVideo_googleProvider_routesToGoogleAdapter() =
        runTest {
            googleAdapter.result = Result.success("google".toByteArray())

            val result = port.generateVideo("prompt", provider(ApiProvider.GOOGLE), VideoGenerationSettings())

            assertTrue(result.isSuccess)
            assertArrayEquals("google".toByteArray(), result.getOrThrow())
            val call = googleAdapter.calls.single()
            assertEquals("prompt", call.prompt)
            assertEquals("key", call.apiKey)
            assertEquals("custom-model", call.modelId)
            assertEquals(null, call.baseUrl)
            assertEquals(VideoGenerationSettings(), call.settings)
        }

    @Test
    fun generateVideo_xaiProvider_routesToXaiAdapter() =
        runTest {
            xaiAdapter.result = Result.success("xai".toByteArray())

            val result = port.generateVideo("prompt", provider(ApiProvider.XAI), VideoGenerationSettings())

            assertTrue(result.isSuccess)
            assertArrayEquals("xai".toByteArray(), result.getOrThrow())
            val call = xaiAdapter.calls.single()
            assertEquals("prompt", call.prompt)
            assertEquals("key", call.apiKey)
            assertEquals("custom-model", call.modelId)
            assertEquals(null, call.baseUrl)
            assertEquals(VideoGenerationSettings(), call.settings)
            assertEquals(null, call.referenceImage)
        }

    @Test
    fun generateVideo_falProvider_routesToFalAdapter() =
        runTest {
            falAdapter.result = Result.success("fal".toByteArray())

            val result = port.generateVideo("prompt", provider(ApiProvider.FAL), VideoGenerationSettings())

            assertTrue(result.isSuccess)
            assertArrayEquals("fal".toByteArray(), result.getOrThrow())
            val call = falAdapter.calls.single()
            assertEquals("prompt", call.prompt)
            assertEquals("key", call.apiKey)
            assertEquals("custom-model", call.modelId)
            assertEquals(null, call.baseUrl)
            assertEquals(VideoGenerationSettings(), call.settings)
            assertEquals(null, call.referenceImage)
            assertEquals(false, call.supportsImageToVideo)
        }

    @Test
    fun generateVideo_falProviderNullModelName_usesDefaultVideoModel() =
        runTest {
            falAdapter.result = Result.success("fal".toByteArray())

            val result = port.generateVideo("prompt", provider(ApiProvider.FAL, modelName = null), VideoGenerationSettings())

            assertTrue(result.isSuccess)
            assertArrayEquals("fal".toByteArray(), result.getOrThrow())
            val call = falAdapter.calls.single()
            assertEquals("prompt", call.prompt)
            assertEquals("key", call.apiKey)
            assertEquals("fal-ai/minimax/video-01", call.modelId)
            assertEquals(null, call.baseUrl)
            assertEquals(VideoGenerationSettings(), call.settings)
            assertEquals(null, call.referenceImage)
            assertEquals(false, call.supportsImageToVideo)
        }

    @Test
    fun generateVideo_unsupportedProvider_returnsFailure() =
        runTest {
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
