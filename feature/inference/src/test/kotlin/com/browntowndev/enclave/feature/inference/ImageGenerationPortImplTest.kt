package com.browntowndev.enclave.feature.inference

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.browntowndev.enclave.core.testing.FakeApiKeyProvider
import com.browntowndev.enclave.core.testing.FakeMediaProviderRepository
import com.browntowndev.enclave.core.testing.FakeStudioRepository
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
import com.browntowndev.enclave.domain.port.media.LOCAL_IMAGE_GENERATION_UNSUPPORTED_MESSAGE
import com.browntowndev.enclave.domain.port.security.ApiKeyProviderPort
import com.browntowndev.enclave.feature.inference.media.ImageGenerationPortImpl
import com.browntowndev.enclave.feature.inference.media.local.LocalStableDiffusionGenerationResult
import com.browntowndev.enclave.localimagegen.NativeLocalImageGenRuntime
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [34], shadows = [ShadowFakeNativeLocalImageGenRuntime::class])
class ImageGenerationPortImplTest {
    private val apiKeyProvider = FakeApiKeyProvider()
    private val studioRepository = FakeStudioRepository()
    private val mediaProviderRepository = FakeMediaProviderRepository()
    private val gpuCapabilityPort = KnobbedGpuCapabilityPort(supported = true)
    private lateinit var localStableDiffusionImageGenerator: FakeLocalStableDiffusionImageGenerator
    private val openAiAdapter = FakeOpenAiImageGenerationAdapter()
    private val googleAdapter = FakeGoogleImageGenerationAdapter()
    private val xaiAdapter = FakeXaiImageGenerationAdapter()
    private val falAdapter = FakeFalImageGenerationAdapter()
    private lateinit var port: ImageGenerationPortImpl

    @BeforeEach
    fun setup() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val runtime = NativeLocalImageGenRuntime(context)
        localStableDiffusionImageGenerator = FakeLocalStableDiffusionImageGenerator(context, runtime)
        port = buildPort(apiKeyProvider)
        apiKeyProvider.saveApiKey("alias", "key")
        studioRepository.mediaBytes["cache://reference.png"] = "png".toByteArray()
    }

    private fun buildPort(apiKeyProvider: ApiKeyProviderPort): ImageGenerationPortImpl =
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

    @Test
    fun generateImage_missingApiKey_returnsFailure() =
        runTest {
            port = buildPort(FakeApiKeyProvider())
            mediaProviderRepository.emitProviders(listOf(provider(ApiProvider.OPENAI)))

            val result = port.generateImage("prompt", remoteTarget(), ImageGenerationSettings())

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull()?.message?.contains("API key not found") == true)
        }

    @Test
    fun generateImage_falProvider_loadsReferenceAndUsesDefaultModel() =
        runTest {
            val falProvider = provider(ApiProvider.FAL, modelName = null).copy(tasks = setOf(MediaTask.EDIT_IMAGE))
            mediaProviderRepository.emitProviders(listOf(falProvider))
            falAdapter.result = Result.success(listOf("fal".toByteArray()))

            val settings = ImageGenerationSettings(referenceImageUri = "cache://reference.png")

            val result = port.generateImage("draw", remoteTarget(), settings)

            assertTrue(result.isSuccess)
            assertArrayEquals("fal".toByteArray(), result.getOrThrow().single())
            val call = falAdapter.calls.single()
            assertEquals("draw", call.prompt)
            assertEquals("key", call.apiKey)
            assertEquals("fal-ai/flux-pro/kontext", call.modelId)
            assertEquals(null, call.baseUrl)
            assertEquals(settings, call.settings)
            assertTrue(call.referenceImage?.contentEquals("png".toByteArray()) == true)
            assertEquals(true, call.supportsEdit)
        }

    @Test
    fun generateImage_unsupportedProvider_returnsFailure() =
        runTest {
            mediaProviderRepository.emitProviders(listOf(provider(ApiProvider.ANTHROPIC)))

            val result = port.generateImage("prompt", remoteTarget(), ImageGenerationSettings())

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is UnsupportedOperationException)
        }

    @Test
    fun generateImage_remoteTarget_providerNotFound_returnsFailure() =
        runTest {
            mediaProviderRepository.emitProviders(emptyList())

            val result = port.generateImage("prompt", remoteTarget(), ImageGenerationSettings())

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull()?.message?.contains("Assigned media provider not found") == true)
        }

    @Test
    fun generateImage_localTarget_readsEveryGeneratedOutput() =
        runTest {
            val target = localTarget()
            localStableDiffusionImageGenerator.result =
                Result.success(
                    LocalStableDiffusionGenerationResult(
                        outputFileReferences = listOf("/cache/bonsai-0.png", "/cache/bonsai-1.png"),
                        width = 512,
                        height = 512,
                        backend = com.browntowndev.enclave.domain.model.media.LocalImageGenerationBackendKind.QNN_NPU,
                    ),
                )
            studioRepository.mediaBytes["/cache/bonsai-0.png"] = "img-0".toByteArray()
            studioRepository.mediaBytes["/cache/bonsai-1.png"] = "img-1".toByteArray()

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
            localStableDiffusionImageGenerator.result =
                Result.failure(IllegalStateException("Local image generation failed."))

            val result = port.generateImage("bonsai", target, ImageGenerationSettings())

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull()?.message == "Local image generation failed.")
        }

    @Test
    fun generateImage_unsupportedDevice_rejectsLocalTargetBeforeNativeGeneration() =
        runTest {
            gpuCapabilityPort.supported = false

            val result = port.generateImage("bonsai", localTarget(), ImageGenerationSettings())

            assertTrue(result.isFailure)
            assertEquals(LOCAL_IMAGE_GENERATION_UNSUPPORTED_MESSAGE, result.exceptionOrNull()?.message)
            assertTrue(localStableDiffusionImageGenerator.calls.isEmpty())
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
    fun generateImage_localTarget_generationCount3_generatesSequentially() =
        runTest {
            val target = localTarget()
            val settings = ImageGenerationSettings(generationCount = 3)
            localStableDiffusionImageGenerator.resultQueue.addAll(
                listOf(
                    Result.success(
                        LocalStableDiffusionGenerationResult(
                            outputFileReferences = listOf("/cache/bonsai-0.png"),
                            width = 512,
                            height = 512,
                            backend = com.browntowndev.enclave.domain.model.media.LocalImageGenerationBackendKind.QNN_NPU,
                        ),
                    ),
                    Result.success(
                        LocalStableDiffusionGenerationResult(
                            outputFileReferences = listOf("/cache/bonsai-1.png"),
                            width = 512,
                            height = 512,
                            backend = com.browntowndev.enclave.domain.model.media.LocalImageGenerationBackendKind.QNN_NPU,
                        ),
                    ),
                    Result.success(
                        LocalStableDiffusionGenerationResult(
                            outputFileReferences = listOf("/cache/bonsai-2.png"),
                            width = 512,
                            height = 512,
                            backend = com.browntowndev.enclave.domain.model.media.LocalImageGenerationBackendKind.QNN_NPU,
                        ),
                    ),
                ),
            )
            studioRepository.mediaBytes["/cache/bonsai-0.png"] = "img-0".toByteArray()
            studioRepository.mediaBytes["/cache/bonsai-1.png"] = "img-1".toByteArray()
            studioRepository.mediaBytes["/cache/bonsai-2.png"] = "img-2".toByteArray()

            val result = port.generateImage("bonsai", target, settings)

            assertTrue(result.isSuccess)
            assertEquals(3, result.getOrThrow().size)
            assertEquals(3, localStableDiffusionImageGenerator.calls.size)
            assertTrue(
                localStableDiffusionImageGenerator.calls.all {
                    it.prompt == "bonsai" && it.target == target && it.settings.generationCount == 1
                },
            )
        }

    @Test
    fun `generateImage SD15 local target propagates correct parameters`() =
        runTest {
            val target = localTarget()
            val settings = ImageGenerationSettings(imageResolution = "512x512")
            localStableDiffusionImageGenerator.result =
                Result.success(
                    LocalStableDiffusionGenerationResult(
                        outputFileReferences = listOf("/cache/bonsai-0.png"),
                        width = 512,
                        height = 512,
                        backend = com.browntowndev.enclave.domain.model.media.LocalImageGenerationBackendKind.QNN_NPU,
                    ),
                )
            studioRepository.mediaBytes["/cache/bonsai-0.png"] = "img".toByteArray()

            val result = port.generateImage("bonsai", target, settings)

            assertTrue(result.isSuccess)
            val call = localStableDiffusionImageGenerator.calls.single()
            assertEquals("512x512", call.settings.imageResolution)
            assertEquals("bonsai", call.prompt)
            assertEquals(target, call.target)
            assertEquals(settings, call.settings)
        }

    @Test
    fun `generateImage SDXL local target propagates correct parameters`() =
        runTest {
            val target =
                localTarget().copy(
                    modelId = com.browntowndev.enclave.domain.model.media.LocalImageGenerationModelId.SDXL_QNN,
                    capabilities =
                        ProviderCapabilities(
                            supportedAspectRatios = listOf(AspectRatio.ONE_ONE),
                            supportedImageQualities = listOf(GenerationQuality.SPEED),
                            supportedImageResolutions = listOf("1024x1024"),
                            supportedImageSteps = listOf(8, 12, 16, 20, 24, 30),
                            defaultImageStep = 8,
                            supportedVideoQualities = emptyList(),
                            supportedVideoResolutions = emptyList(),
                            supportedVideoDurations = emptyList(),
                            supportsVideo = false,
                        ),
                )
            val settings = ImageGenerationSettings(imageResolution = "1024x1024", diffusionSteps = 8)
            localStableDiffusionImageGenerator.result =
                Result.success(
                    LocalStableDiffusionGenerationResult(
                        outputFileReferences = listOf("/cache/sdxl-0.png"),
                        width = 1024,
                        height = 1024,
                        backend = com.browntowndev.enclave.domain.model.media.LocalImageGenerationBackendKind.QNN_NPU,
                    ),
                )
            studioRepository.mediaBytes["/cache/sdxl-0.png"] = "img".toByteArray()

            val result = port.generateImage("bonsai", target, settings)

            assertTrue(result.isSuccess)
            val call = localStableDiffusionImageGenerator.calls.single()
            assertEquals("1024x1024", call.settings.imageResolution)
            assertEquals(8, call.settings.diffusionSteps)
            assertEquals("bonsai", call.prompt)
            assertEquals(target, call.target)
            assertEquals(settings, call.settings)
        }

    @Test
    fun generateImage_localTarget_multiCountFailure_returnsPartialResults() =
        runTest {
            val target = localTarget()
            val settings = ImageGenerationSettings(generationCount = 3)
            localStableDiffusionImageGenerator.resultQueue.addAll(
                listOf(
                    Result.success(
                        LocalStableDiffusionGenerationResult(
                            outputFileReferences = listOf("/cache/bonsai-0.png"),
                            width = 512,
                            height = 512,
                            backend = com.browntowndev.enclave.domain.model.media.LocalImageGenerationBackendKind.QNN_NPU,
                        ),
                    ),
                    Result.failure(IllegalStateException("Local image generation failed on image 2")),
                ),
            )
            studioRepository.mediaBytes["/cache/bonsai-0.png"] = "img-0".toByteArray()

            val result = port.generateImage("bonsai", target, settings)

            assertTrue(result.isSuccess)
            assertEquals(1, result.getOrThrow().size)
            assertArrayEquals("img-0".toByteArray(), result.getOrThrow().single())
            assertEquals(2, localStableDiffusionImageGenerator.calls.size)
            assertTrue(
                localStableDiffusionImageGenerator.calls.all {
                    it.prompt == "bonsai" && it.target == target && it.settings.generationCount == 1
                },
            )
        }

    @Test
    fun `generateImage Juggernaut IMAGE_EDIT local target propagates correct parameters`() =
        runTest {
            val target =
                localTarget().copy(
                    localConfigId = LocalModelConfigurationId("juggernaut-local-config"),
                    modelId = com.browntowndev.enclave.domain.model.media.LocalImageGenerationModelId.JUGGERNAUT_XL_QNN,
                    capabilities =
                        ProviderCapabilities(
                            supportedAspectRatios = listOf(AspectRatio.ONE_ONE),
                            supportedImageQualities = emptyList(),
                            supportedImageResolutions = listOf("1024x1024"),
                            supportedImageSteps = listOf(20, 22, 24, 26, 28, 30),
                            defaultImageStep = 24,
                            supportedVideoQualities = emptyList(),
                            supportedVideoResolutions = emptyList(),
                            supportedVideoDurations = emptyList(),
                            supportsVideo = false,
                        ),
                )
            val settings = ImageGenerationSettings(imageResolution = "1024x1024", diffusionSteps = 24)
            localStableDiffusionImageGenerator.result =
                Result.success(
                    LocalStableDiffusionGenerationResult(
                        outputFileReferences = listOf("/cache/juggernaut-0.png"),
                        width = 1024,
                        height = 1024,
                        backend = com.browntowndev.enclave.domain.model.media.LocalImageGenerationBackendKind.QNN_NPU,
                    ),
                )
            studioRepository.mediaBytes["/cache/juggernaut-0.png"] = "img".toByteArray()

            val result = port.generateImage("bonsai", target, settings)

            assertTrue(result.isSuccess)
            val call = localStableDiffusionImageGenerator.calls.single()
            assertEquals("1024x1024", call.settings.imageResolution)
            assertEquals(24, call.settings.diffusionSteps)
        }
}
