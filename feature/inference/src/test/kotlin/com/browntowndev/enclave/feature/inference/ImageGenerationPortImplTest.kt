package com.browntowndev.enclave.feature.inference

import com.browntowndev.enclave.domain.model.config.LocalModelConfigurationId
import com.browntowndev.enclave.domain.model.config.MediaCapability
import com.browntowndev.enclave.domain.model.config.MediaProviderAsset
import com.browntowndev.enclave.domain.model.config.MediaProviderId
import com.browntowndev.enclave.domain.model.config.MediaTask
import com.browntowndev.enclave.domain.model.inference.ApiProvider
import com.browntowndev.enclave.domain.model.media.AspectRatio
import com.browntowndev.enclave.domain.model.media.GenerationQuality
import com.browntowndev.enclave.domain.model.media.ImageGenerationSettings
import com.browntowndev.enclave.domain.model.media.ImageGenerationTarget
import com.browntowndev.enclave.domain.model.media.ProviderCapabilities
import com.browntowndev.enclave.domain.port.media.GpuCapabilityPort
import com.browntowndev.enclave.domain.port.media.LOCAL_IMAGE_GENERATION_UNSUPPORTED_MESSAGE
import com.browntowndev.enclave.domain.port.repository.MediaProviderRepositoryPort
import com.browntowndev.enclave.domain.port.repository.StudioRepositoryPort
import com.browntowndev.enclave.domain.port.security.ApiKeyProviderPort
import com.browntowndev.enclave.feature.inference.media.FalImageGenerationAdapter
import com.browntowndev.enclave.feature.inference.media.GoogleImageGenerationAdapter
import com.browntowndev.enclave.feature.inference.media.ImageGenerationPortImpl
import com.browntowndev.enclave.feature.inference.media.OpenAiImageGenerationAdapter
import com.browntowndev.enclave.feature.inference.media.XaiImageGenerationAdapter
import com.browntowndev.enclave.feature.inference.media.local.LocalStableDiffusionGenerationResult
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

class ImageGenerationPortImplTest {
    private val apiKeyProvider = mockk<ApiKeyProviderPort>()
    private val studioRepository = mockk<StudioRepositoryPort>()
    private val mediaProviderRepository = mockk<MediaProviderRepositoryPort>()
    private val gpuCapabilityPort = mockk<GpuCapabilityPort>()
    private val localStableDiffusionImageGenerator = mockk<LocalStableDiffusionImageGenerator>()
    private val openAiAdapter = mockk<OpenAiImageGenerationAdapter>()
    private val googleAdapter = mockk<GoogleImageGenerationAdapter>()
    private val xaiAdapter = mockk<XaiImageGenerationAdapter>()
    private val falAdapter = mockk<FalImageGenerationAdapter>()
    private lateinit var port: ImageGenerationPortImpl

    @BeforeEach
    fun setup() {
        port =
            ImageGenerationPortImpl(
                apiKeyProvider = apiKeyProvider,
                studioRepository = studioRepository,
                mediaProviderRepository = mediaProviderRepository,
                gpuCapabilityPort = gpuCapabilityPort,
                localStableDiffusionImageGenerator = localStableDiffusionImageGenerator,
                openAiAdapter = openAiAdapter,
                googleAdapter = googleAdapter,
                xaiAdapter = xaiAdapter,
                falAdapter = falAdapter,
            )
        coEvery { apiKeyProvider.getApiKey("alias") } returns "key"
        coEvery { studioRepository.readMediaBytes("cache://reference.png") } returns "png".toByteArray()
        every { gpuCapabilityPort.isLocalImageGenerationSupported() } returns true
    }

    @Test
    fun generateImage_missingApiKey_returnsFailure() =
        runTest {
            coEvery { apiKeyProvider.getApiKey("alias") } returns null
            coEvery { mediaProviderRepository.getMediaProvider(any()) } returns provider(ApiProvider.OPENAI)

            val result = port.generateImage("prompt", remoteTarget(), ImageGenerationSettings())

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull()?.message?.contains("API key not found") == true)
        }

    @Test
    fun generateImage_falProvider_loadsReferenceAndUsesDefaultModel() =
        runTest {
            val falProvider = provider(ApiProvider.FAL, modelName = null).copy(tasks = setOf(MediaTask.EDIT_IMAGE))
            coEvery { mediaProviderRepository.getMediaProvider(any()) } returns falProvider
            coEvery {
                falAdapter.generateImage(any(), any(), any(), any(), any(), any(), any())
            } returns Result.success(listOf("fal".toByteArray()))

            val settings = ImageGenerationSettings(referenceImageUri = "cache://reference.png")

            val result = port.generateImage("draw", remoteTarget(), settings)

            assertTrue(result.isSuccess)
            assertArrayEquals("fal".toByteArray(), result.getOrThrow().single())
            coVerify {
                falAdapter.generateImage(
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
    fun generateImage_unsupportedProvider_returnsFailure() =
        runTest {
            coEvery { mediaProviderRepository.getMediaProvider(any()) } returns provider(ApiProvider.ANTHROPIC)

            val result = port.generateImage("prompt", remoteTarget(), ImageGenerationSettings())

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is UnsupportedOperationException)
        }

    @Test
    fun generateImage_remoteTarget_providerNotFound_returnsFailure() =
        runTest {
            coEvery { mediaProviderRepository.getMediaProvider(any()) } returns null

            val result = port.generateImage("prompt", remoteTarget(), ImageGenerationSettings())

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull()?.message?.contains("Assigned media provider not found") == true)
        }

    @Test
    fun generateImage_localTarget_readsEveryGeneratedOutput() =
        runTest {
            val target = localTarget()
            coEvery { localStableDiffusionImageGenerator.generate(any(), eq(target), any(), any()) } returns
                Result.success(
                    LocalStableDiffusionGenerationResult(
                        outputFileReferences = listOf("/cache/bonsai-0.png", "/cache/bonsai-1.png"),
                        width = 512,
                        height = 512,
                        backend = com.browntowndev.enclave.domain.model.media.LocalImageGenerationBackendKind.QNN_NPU,
                    ),
                )
            coEvery { studioRepository.readMediaBytes("/cache/bonsai-0.png") } returns "img-0".toByteArray()
            coEvery { studioRepository.readMediaBytes("/cache/bonsai-1.png") } returns "img-1".toByteArray()

            val result = port.generateImage("bonsai", target, ImageGenerationSettings(imageResolution = "512x512"))

            assertTrue(result.isSuccess)
            assertEquals(2, result.getOrThrow().size)
            assertArrayEquals("img-0".toByteArray(), result.getOrThrow()[0])
            assertArrayEquals("img-1".toByteArray(), result.getOrThrow()[1])
        }

    @Test
    fun generateImage_localTarget_propagatesGeneratorFailure() =
        runTest {
            val target = localTarget()
            coEvery { localStableDiffusionImageGenerator.generate(any(), eq(target), any(), any()) } returns
                Result.failure(IllegalStateException("Local image generation failed."))

            val result = port.generateImage("bonsai", target, ImageGenerationSettings())

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull()?.message == "Local image generation failed.")
        }

    @Test
    fun generateImage_unsupportedDevice_rejectsLocalTargetBeforeNativeGeneration() =
        runTest {
            every { gpuCapabilityPort.isLocalImageGenerationSupported() } returns false

            val result = port.generateImage("bonsai", localTarget(), ImageGenerationSettings())

            assertTrue(result.isFailure)
            assertEquals(LOCAL_IMAGE_GENERATION_UNSUPPORTED_MESSAGE, result.exceptionOrNull()?.message)
            coVerify(exactly = 0) { localStableDiffusionImageGenerator.generate(any(), any(), any(), any()) }
        }

    private fun remoteTarget(): ImageGenerationTarget.Remote =
        ImageGenerationTarget.Remote(
            providerAssetId = MediaProviderId("provider"),
            providerDisplayName = "Provider",
        )

    private fun localTarget(): ImageGenerationTarget.LocalStableDiffusion =
        ImageGenerationTarget.LocalStableDiffusion(
            localConfigId = LocalModelConfigurationId("sd15-local-config"),
            displayName = "Stable Diffusion 1.5",
            modelId = com.browntowndev.enclave.domain.model.media.LocalImageGenerationModelId.SD15_QNN,
            modelDir = "/models",
            catalogSha256 = "sha",
            selectedBackend = com.browntowndev.enclave.domain.model.media.LocalImageGenerationBackendKind.QNN_NPU,
            capabilities =
                ProviderCapabilities(
                    supportedAspectRatios = listOf(AspectRatio.ONE_ONE),
                    supportedImageQualities = listOf(GenerationQuality.SPEED),
                    supportedImageResolutions = listOf("512x512"),
                    supportedVideoQualities = emptyList(),
                    supportedVideoResolutions = emptyList(),
                    supportedVideoDurations = emptyList(),
                    supportsVideo = false,
                ),
            deviceCapabilities =
                com.browntowndev.enclave.domain.model.media.LocalImageGenerationCapabilities(
                    npuAvailable = com.browntowndev.enclave.domain.model.media.LocalImageGenTriState.YES,
                    sd15NpuSupported = com.browntowndev.enclave.domain.model.media.LocalImageGenTriState.YES,
                    sd15CpuSupported = com.browntowndev.enclave.domain.model.media.LocalImageGenTriState.NO,
                    sdxlNpuSupported = com.browntowndev.enclave.domain.model.media.LocalImageGenTriState.NO,
                    recommendedBackend =
                        com.browntowndev.enclave.domain.model.media.LocalImageGenerationBackendKind.QNN_NPU,
                    recommendedModelId =
                        com.browntowndev.enclave.domain.model.media.LocalImageGenerationModelId.SD15_QNN,
                    diagnostics = emptyList(),
                ),
        )

    private fun provider(
        provider: ApiProvider,
        modelName: String? = "custom-model",
    ): MediaProviderAsset =
        MediaProviderAsset(
            id = MediaProviderId("provider"),
            displayName = provider.displayName,
            provider = provider,
            capability = MediaCapability.IMAGE,
            modelName = modelName,
            credentialAlias = "alias",
        )

    @Test
    fun `generateImage SD15 local target propagates correct parameters`() =
        runTest {
            val target = localTarget()
            val settings = ImageGenerationSettings(imageResolution = "512x512")
            coEvery { localStableDiffusionImageGenerator.generate(any(), eq(target), any(), any()) } returns
                Result.success(
                    LocalStableDiffusionGenerationResult(
                        outputFileReferences = listOf("/cache/bonsai-0.png"),
                        width = 512,
                        height = 512,
                        backend = com.browntowndev.enclave.domain.model.media.LocalImageGenerationBackendKind.QNN_NPU,
                    ),
                )
            coEvery { studioRepository.readMediaBytes(any()) } returns "img".toByteArray()

            val result = port.generateImage("bonsai", target, settings)

            assertTrue(result.isSuccess)
            coVerify {
                localStableDiffusionImageGenerator.generate(
                    prompt = "bonsai",
                    target = target,
                    settings = settings,
                    onProgress = any()
                )
            }
        }

    @Test
    fun `generateImage SDXL local target propagates correct parameters`() =
        runTest {
            val target = localTarget().copy(
                modelId = com.browntowndev.enclave.domain.model.media.LocalImageGenerationModelId.SDXL_QNN,
                capabilities = ProviderCapabilities(
                    supportedAspectRatios = listOf(AspectRatio.ONE_ONE),
                    supportedImageQualities = listOf(GenerationQuality.SPEED),
                    supportedImageResolutions = listOf("1024x1024"),
                    supportedImageSteps = listOf(8, 12, 16, 20, 24, 30),
                    defaultImageStep = 16,
                    supportedVideoQualities = emptyList(),
                    supportedVideoResolutions = emptyList(),
                    supportedVideoDurations = emptyList(),
                    supportsVideo = false,
                )
            )
            val settings = ImageGenerationSettings(imageResolution = "1024x1024", diffusionSteps = 8)
            coEvery { localStableDiffusionImageGenerator.generate(any(), eq(target), any(), any()) } returns
                Result.success(
                    LocalStableDiffusionGenerationResult(
                        outputFileReferences = listOf("/cache/sdxl-0.png"),
                        width = 1024,
                        height = 1024,
                        backend = com.browntowndev.enclave.domain.model.media.LocalImageGenerationBackendKind.QNN_NPU,
                    ),
                )
            coEvery { studioRepository.readMediaBytes(any()) } returns "img".toByteArray()

            val result = port.generateImage("bonsai", target, settings)

            assertTrue(result.isSuccess)
            coVerify {
                localStableDiffusionImageGenerator.generate(
                    prompt = "bonsai",
                    target = target,
                    settings = settings,
                    onProgress = any()
                )
            }
        }
}
