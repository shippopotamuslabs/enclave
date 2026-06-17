package com.browntowndev.enclave.feature.inference

import com.browntowndev.enclave.domain.model.config.MediaCapability
import com.browntowndev.enclave.domain.model.config.MediaProviderAsset
import com.browntowndev.enclave.domain.model.config.MediaProviderId
import com.browntowndev.enclave.domain.model.config.MediaTask
import com.browntowndev.enclave.domain.model.inference.ApiProvider
import com.browntowndev.enclave.domain.model.media.ImageGenerationSettings
import com.browntowndev.enclave.domain.model.media.ImageGenerationTarget
import com.browntowndev.enclave.domain.model.media.VideoGenerationSettings
import com.browntowndev.enclave.domain.port.media.GpuCapabilityPort
import com.browntowndev.enclave.domain.port.repository.MediaProviderRepositoryPort
import com.browntowndev.enclave.domain.port.repository.StudioRepositoryPort
import com.browntowndev.enclave.domain.port.security.ApiKeyProviderPort
import com.browntowndev.enclave.feature.inference.media.FalImageGenerationAdapter
import com.browntowndev.enclave.feature.inference.media.FalVideoGenerationAdapter
import com.browntowndev.enclave.feature.inference.media.GoogleImageGenerationAdapter
import com.browntowndev.enclave.feature.inference.media.GoogleVideoGenerationAdapter
import com.browntowndev.enclave.feature.inference.media.ImageGenerationPortImpl
import com.browntowndev.enclave.feature.inference.media.OpenAiImageGenerationAdapter
import com.browntowndev.enclave.feature.inference.media.OpenAiVideoGenerationAdapter
import com.browntowndev.enclave.feature.inference.media.VideoGenerationPortImpl
import com.browntowndev.enclave.feature.inference.media.XaiImageGenerationAdapter
import com.browntowndev.enclave.feature.inference.media.XaiVideoGenerationAdapter
import com.browntowndev.enclave.feature.inference.media.local.LocalStableDiffusionImageGenerator
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class MediaPortTasksRedTest {
    private val apiKeyProvider = mockk<ApiKeyProviderPort>()
    private val studioRepository = mockk<StudioRepositoryPort>()
    private val mediaProviderRepository = mockk<MediaProviderRepositoryPort>()
    private val gpuCapabilityPort = mockk<GpuCapabilityPort>()
    private val localStableDiffusionImageGenerator = mockk<LocalStableDiffusionImageGenerator>()
    private val falImageAdapter = mockk<FalImageGenerationAdapter>()
    private val falVideoAdapter = mockk<FalVideoGenerationAdapter>()
    private lateinit var imagePort: ImageGenerationPortImpl
    private lateinit var videoPort: VideoGenerationPortImpl

    @BeforeEach
    fun setUp() {
        imagePort =
            ImageGenerationPortImpl(
                apiKeyProvider = apiKeyProvider,
                studioRepository = studioRepository,
                mediaProviderRepository = mediaProviderRepository,
                gpuCapabilityPort = gpuCapabilityPort,
                localStableDiffusionImageGenerator = localStableDiffusionImageGenerator,
                openAiAdapter = mockk<OpenAiImageGenerationAdapter>(),
                googleAdapter = mockk<GoogleImageGenerationAdapter>(),
                xaiAdapter = mockk<XaiImageGenerationAdapter>(),
                falAdapter = falImageAdapter,
            )
        videoPort =
            VideoGenerationPortImpl(
                apiKeyProvider = apiKeyProvider,
                studioRepository = studioRepository,
                openAiAdapter = mockk<OpenAiVideoGenerationAdapter>(),
                googleAdapter = mockk<GoogleVideoGenerationAdapter>(),
                xaiAdapter = mockk<XaiVideoGenerationAdapter>(),
                falAdapter = falVideoAdapter,
            )
        coEvery { apiKeyProvider.getApiKey("alias") } returns "key"
        every { gpuCapabilityPort.isLocalImageGenerationSupported() } returns true
        coEvery { studioRepository.readMediaBytes("cache://reference.png") } returns "png".toByteArray()
        coEvery { falImageAdapter.generateImage(any(), any(), any(), any(), any(), any(), any()) } returns
            Result.success(listOf("image".toByteArray()))
        coEvery { falVideoAdapter.generateVideo(any(), any(), any(), any(), any(), any(), any()) } returns
            Result.success("video".toByteArray())
    }

    @Test
    fun `IMGPORT-01 edit-capable provider loads reference and forwards supportsEdit true`() =
        runTest {
            val settings = ImageGenerationSettings(referenceImageUri = "cache://reference.png")
            val provider = imageProvider(setOf(MediaTask.TEXT_TO_IMAGE, MediaTask.EDIT_IMAGE))
            coEvery { mediaProviderRepository.getMediaProvider(provider.id) } returns provider
            val target = ImageGenerationTarget.Remote(provider.id, provider.displayName)

            val result = imagePort.generateImage("draw", target, settings)

            assertTrue(result.isSuccess)
            coVerify {
                falImageAdapter.generateImage(
                    prompt = "draw",
                    apiKey = "key",
                    modelId = "fal-ai/flux-pro/kontext",
                    baseUrl = null,
                    settings = settings,
                    referenceImage = match { it.contentEquals("png".toByteArray()) },
                    supportsEdit = true,
                )
            }
        }

    @Test
    fun `IMGPORT-02 text-to-image-only provider with reference fails fast`() =
        runTest {
            val provider = imageProvider(setOf(MediaTask.TEXT_TO_IMAGE), modelName = "fal-ai/recraft/v3")
            coEvery { mediaProviderRepository.getMediaProvider(provider.id) } returns provider
            val target = ImageGenerationTarget.Remote(provider.id, provider.displayName)
            val result =
                imagePort.generateImage(
                    "draw",
                    target,
                    ImageGenerationSettings(referenceImageUri = "cache://reference.png"),
                )

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is IllegalArgumentException)
            assertEquals("Model 'fal-ai/recraft/v3' does not support image editing.", result.exceptionOrNull()?.message)
            coVerify(exactly = 0) { studioRepository.readMediaBytes(any()) }
            coVerify(exactly = 0) { falImageAdapter.generateImage(any(), any(), any(), any(), any(), any(), any()) }
        }

    @Test
    fun `IMGPORT-03 text-to-image-only provider without reference forwards supportsEdit false`() =
        runTest {
            val provider = imageProvider(setOf(MediaTask.TEXT_TO_IMAGE), modelName = "fal-ai/recraft/v3")
            coEvery { mediaProviderRepository.getMediaProvider(provider.id) } returns provider
            val target = ImageGenerationTarget.Remote(provider.id, provider.displayName)
            val result =
                imagePort.generateImage(
                    "draw",
                    target,
                    ImageGenerationSettings(referenceImageUri = null),
                )

            assertTrue(result.isSuccess)
            assertArrayEquals("image".toByteArray(), result.getOrThrow().single())
            coVerify {
                falImageAdapter.generateImage(
                    prompt = "draw",
                    apiKey = "key",
                    modelId = "fal-ai/recraft/v3",
                    baseUrl = null,
                    settings = any(),
                    referenceImage = null,
                    supportsEdit = false,
                )
            }
        }

    @Test
    fun `VIDPORT-01 image-to-video provider loads reference and forwards supportsImageToVideo true`() =
        runTest {
            val settings = VideoGenerationSettings(referenceImageUri = "cache://reference.png")

            val result =
                videoPort.generateVideo(
                    "animate",
                    videoProvider(setOf(MediaTask.TEXT_TO_VIDEO, MediaTask.IMAGE_TO_VIDEO)),
                    settings,
                )

            assertTrue(result.isSuccess)
            coVerify {
                falVideoAdapter.generateVideo(
                    prompt = "animate",
                    apiKey = "key",
                    modelId = "fal-ai/minimax/video-01",
                    baseUrl = null,
                    settings = settings,
                    referenceImage = match { it.contentEquals("png".toByteArray()) },
                    supportsImageToVideo = true,
                )
            }
        }

    @Test
    fun `VIDPORT-02 text-to-video-only provider with reference fails fast`() =
        runTest {
            val result =
                videoPort.generateVideo(
                    "animate",
                    videoProvider(setOf(MediaTask.TEXT_TO_VIDEO), modelName = "fal-ai/minimax/video-01"),
                    VideoGenerationSettings(referenceImageUri = "cache://reference.png"),
                )

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is IllegalArgumentException)
            assertEquals("Model 'fal-ai/minimax/video-01' does not support image-to-video.", result.exceptionOrNull()?.message)
            coVerify(exactly = 0) { studioRepository.readMediaBytes(any()) }
            coVerify(exactly = 0) { falVideoAdapter.generateVideo(any(), any(), any(), any(), any(), any(), any()) }
        }

    @Test
    fun `VIDPORT-03 text-to-video-only provider without reference forwards supportsImageToVideo false`() =
        runTest {
            val result =
                videoPort.generateVideo(
                    "animate",
                    videoProvider(setOf(MediaTask.TEXT_TO_VIDEO), modelName = "fal-ai/minimax/video-01"),
                    VideoGenerationSettings(referenceImageUri = null),
                )

            assertTrue(result.isSuccess)
            coVerify {
                falVideoAdapter.generateVideo(
                    prompt = "animate",
                    apiKey = "key",
                    modelId = "fal-ai/minimax/video-01",
                    baseUrl = null,
                    settings = any(),
                    referenceImage = null,
                    supportsImageToVideo = false,
                )
            }
        }

    private fun imageProvider(
        tasks: Set<MediaTask>,
        modelName: String = "fal-ai/flux-pro/kontext",
    ): MediaProviderAsset =
        MediaProviderAsset(
            id = MediaProviderId("image-provider"),
            displayName = modelName,
            provider = ApiProvider.FAL,
            capability = MediaCapability.IMAGE,
            modelName = modelName,
            baseUrl = null,
            credentialAlias = "alias",
            tasks = tasks,
        )

    private fun videoProvider(
        tasks: Set<MediaTask>,
        modelName: String = "fal-ai/minimax/video-01",
    ): MediaProviderAsset =
        MediaProviderAsset(
            id = MediaProviderId("video-provider"),
            displayName = modelName,
            provider = ApiProvider.FAL,
            capability = MediaCapability.VIDEO,
            modelName = modelName,
            baseUrl = null,
            credentialAlias = "alias",
            tasks = tasks,
        )
}
