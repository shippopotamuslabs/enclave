package com.browntowndev.enclave.domain.usecase.media

import com.browntowndev.enclave.core.testing.FakeDefaultModelRepository
import com.browntowndev.enclave.core.testing.FakeGpuCapability
import com.browntowndev.enclave.core.testing.FakeImageGenerationPort
import com.browntowndev.enclave.core.testing.FakeLocalImageGenerationCapability
import com.browntowndev.enclave.core.testing.FakeLocalModelRepository
import com.browntowndev.enclave.core.testing.FakeMediaProviderRepository
import com.browntowndev.enclave.core.testing.FakeMusicGenerationPort
import com.browntowndev.enclave.core.testing.FakeStudioRepository
import com.browntowndev.enclave.core.testing.FakeUtilityModelFile
import com.browntowndev.enclave.core.testing.FakeVideoGenerationPort
import com.browntowndev.enclave.domain.model.config.DefaultModelAssignment
import com.browntowndev.enclave.domain.model.config.LocalModelAsset
import com.browntowndev.enclave.domain.model.config.LocalModelConfiguration
import com.browntowndev.enclave.domain.model.config.LocalModelConfigurationId
import com.browntowndev.enclave.domain.model.config.LocalModelId
import com.browntowndev.enclave.domain.model.config.LocalModelMetadata
import com.browntowndev.enclave.domain.model.config.MediaCapability
import com.browntowndev.enclave.domain.model.config.MediaProviderAsset
import com.browntowndev.enclave.domain.model.config.MediaProviderId
import com.browntowndev.enclave.domain.model.inference.ApiProvider
import com.browntowndev.enclave.domain.model.inference.ModelFileFormat
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.model.media.ImageGenerationProgress
import com.browntowndev.enclave.domain.model.media.ImageGenerationSettings
import com.browntowndev.enclave.domain.model.media.LocalImageGenTriState
import com.browntowndev.enclave.domain.model.media.LocalImageGenerationBackendKind
import com.browntowndev.enclave.domain.model.media.LocalImageGenerationCapabilities
import com.browntowndev.enclave.domain.model.media.LocalImageGenerationModelId
import com.browntowndev.enclave.domain.model.media.LocalImageGenerationModelRegistry
import com.browntowndev.enclave.domain.model.media.MediaGenerationOrigin
import com.browntowndev.enclave.domain.model.media.MediaGenerationRequest
import com.browntowndev.enclave.domain.model.media.MusicGenerationSettings
import com.browntowndev.enclave.domain.model.media.VideoGenerationSettings
import com.browntowndev.enclave.domain.util.Clock
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class GenerateMediaUseCaseTest {
    private val defaultModelRepository = FakeDefaultModelRepository()
    private val mediaProviderRepository = FakeMediaProviderRepository()
    private val localModelRepository = FakeLocalModelRepository()
    private val utilityModelFile = FakeUtilityModelFile()
    private val gpuCapability = FakeGpuCapability()
    private val capabilityPort = FakeLocalImageGenerationCapability()
    private val imageGenerationPort = FakeImageGenerationPort()
    private val videoGenerationPort = FakeVideoGenerationPort()
    private val musicGenerationPort = FakeMusicGenerationPort()
    private val studioRepository = FakeStudioRepository()
    private val clock = FakeClock()

    private lateinit var useCase: GenerateMediaUseCase

    @BeforeEach
    fun setUp() {
        clock.now = 0L
        // Seed the default slots the REAL delegate use cases resolve through:
        // IMAGE_GENERATION -> "remote" provider, VIDEO/MUSIC -> their providers.
        defaultModelRepository.seed(
            DefaultModelAssignment(
                modelType = ModelType.IMAGE_GENERATION,
                mediaProviderId = MediaProviderId("remote"),
            ),
        )
        defaultModelRepository.seed(
            DefaultModelAssignment(
                modelType = ModelType.VIDEO_GENERATION,
                mediaProviderId = MediaProviderId("video-provider"),
            ),
        )
        defaultModelRepository.seed(
            DefaultModelAssignment(
                modelType = ModelType.MUSIC_GENERATION,
                mediaProviderId = MediaProviderId("music-provider"),
            ),
        )
        mediaProviderRepository.emitProviders(
            listOf(
                MediaProviderAsset(
                    id = MediaProviderId("remote"),
                    displayName = "Remote",
                    provider = ApiProvider.FAL,
                    capability = MediaCapability.IMAGE,
                    credentialAlias = "fal",
                ),
                MediaProviderAsset(
                    id = MediaProviderId("video-provider"),
                    displayName = "Video Provider",
                    provider = ApiProvider.FAL,
                    capability = MediaCapability.VIDEO,
                    credentialAlias = "fal",
                ),
                MediaProviderAsset(
                    id = MediaProviderId("music-provider"),
                    displayName = "Music Provider",
                    provider = ApiProvider.FAL,
                    capability = MediaCapability.MUSIC,
                    credentialAlias = "fal",
                ),
            ),
        )
        val resolveMediaModelTypeUseCase = ResolveMediaModelTypeUseCase()
        val resolveAssignedImageGenerationTargetUseCase =
            ResolveAssignedImageGenerationTargetUseCase(
                defaultModelRepository = defaultModelRepository,
                mediaProviderRepository = mediaProviderRepository,
                localStableDiffusionTargetResolver =
                    LocalStableDiffusionTargetResolver(
                        localModelRepository = localModelRepository,
                        utilityModelFilePort = utilityModelFile,
                        gpuCapabilityPort = gpuCapability,
                        capabilityPort = capabilityPort,
                        selectBackend = SelectLocalImageGenerationBackendUseCase(),
                    ),
            )
        useCase =
            GenerateMediaUseCase(
                generateImageUseCase =
                    GenerateImageUseCase(
                        resolveMediaModelTypeUseCase = resolveMediaModelTypeUseCase,
                        resolveAssignedImageGenerationTargetUseCase = resolveAssignedImageGenerationTargetUseCase,
                        imageGenerationPort = imageGenerationPort,
                    ),
                generateVideoUseCase =
                    GenerateVideoUseCase(
                        resolveMediaModelTypeUseCase = resolveMediaModelTypeUseCase,
                        resolveAssignedMediaProviderUseCase =
                            ResolveAssignedMediaProviderUseCase(
                                defaultModelRepository = defaultModelRepository,
                                mediaProviderRepository = mediaProviderRepository,
                            ),
                        videoGenerationPort = videoGenerationPort,
                    ),
                generateMusicUseCase =
                    GenerateMusicUseCase(
                        defaultModelRepository = defaultModelRepository,
                        mediaProviderRepository = mediaProviderRepository,
                        musicGenerationPort = musicGenerationPort,
                    ),
                resolveMediaModelTypeUseCase = resolveMediaModelTypeUseCase,
                resolveAssignedImageGenerationTargetUseCase = resolveAssignedImageGenerationTargetUseCase,
                studioRepository = studioRepository,
                clock = clock,
            )
    }

    @Test
    fun `S-GM-01 IMAGE dispatches to GenerateImageUseCase with same prompt and settings`() =
        runTest {
            val settings = ImageGenerationSettings(generationCount = 1)
            imageGenerationPort.result = Result.success(listOf(byteArrayOf(1)))

            useCase(
                MediaGenerationRequest(
                    prompt = "p1",
                    capability = MediaCapability.IMAGE,
                    settings = settings,
                    origin = MediaGenerationOrigin.CHAT_TOOL,
                ),
            )

            assertEquals(listOf("p1"), imageGenerationPort.calls)
            assertEquals(listOf(settings), imageGenerationPort.settingsCalls)
        }

    @Test
    fun `S-GM-02 VIDEO dispatches to GenerateVideoUseCase with same prompt and settings`() =
        runTest {
            val settings = VideoGenerationSettings(videoDuration = 6)
            videoGenerationPort.result = Result.success(byteArrayOf(2))

            useCase(
                MediaGenerationRequest(
                    prompt = "clip",
                    capability = MediaCapability.VIDEO,
                    settings = settings,
                    origin = MediaGenerationOrigin.CHAT_TOOL,
                ),
            )

            assertEquals(listOf("clip"), videoGenerationPort.calls)
            assertEquals(listOf(settings), videoGenerationPort.settingsCalls)
        }

    @Test
    fun `S-GM-03 MUSIC dispatches to GenerateMusicUseCase with same prompt and settings`() =
        runTest {
            val settings = MusicGenerationSettings(duration = 30)
            musicGenerationPort.result = Result.success(byteArrayOf(3))

            useCase(
                MediaGenerationRequest(
                    prompt = "beat",
                    capability = MediaCapability.MUSIC,
                    settings = settings,
                    origin = MediaGenerationOrigin.CHAT_TOOL,
                ),
            )

            assertEquals(listOf("beat"), musicGenerationPort.calls)
            assertEquals(listOf(settings), musicGenerationPort.settingsCalls)
        }

    @Test
    fun `S-GM-04 Success caches with IMAGE mediaType and addEphemeralMedia once per byte array`() =
        runTest {
            val settings = ImageGenerationSettings(generationCount = 1)
            imageGenerationPort.result = Result.success(listOf(byteArrayOf(9), byteArrayOf(8)))
            studioRepository.cacheEphemeralUris.addAll(listOf("file:///cache/a", "file:///cache/b"))

            useCase(
                MediaGenerationRequest(
                    prompt = "p",
                    capability = MediaCapability.IMAGE,
                    settings = settings,
                    origin = MediaGenerationOrigin.CHAT_TOOL,
                ),
            )

            assertEquals(listOf("IMAGE", "IMAGE"), studioRepository.cacheEphemeralMediaCalls)
            assertEquals(2, studioRepository.cacheEphemeralMediaBytesCalls.size)
            assertTrue(studioRepository.cacheEphemeralMediaBytesCalls[0].contentEquals(byteArrayOf(9)))
            assertTrue(studioRepository.cacheEphemeralMediaBytesCalls[1].contentEquals(byteArrayOf(8)))
            assertEquals(2, studioRepository.addEphemeralMediaCalls.size)
        }

    @Test
    fun `S-GM-05 IMAGE multi-byte success returns two GeneratedMediaAsset entries`() =
        runTest {
            val settings = ImageGenerationSettings(generationCount = 1)
            imageGenerationPort.result = Result.success(listOf(byteArrayOf(9), byteArrayOf(8)))
            studioRepository.cacheEphemeralUris.addAll(listOf("file:///cache/a", "file:///cache/b"))

            val result =
                useCase(
                    MediaGenerationRequest(
                        prompt = "p",
                        capability = MediaCapability.IMAGE,
                        settings = settings,
                        origin = MediaGenerationOrigin.CHAT_TOOL,
                    ),
                )

            assertTrue(result.isSuccess)
            val assets = result.getOrThrow()
            assertEquals(2, assets.size)
            assertEquals("file:///cache/a", assets[0].localUri)
            assertEquals("file:///cache/b", assets[1].localUri)
            assertEquals("IMAGE", assets[0].mediaType)
            assertEquals("IMAGE", assets[1].mediaType)
        }

    @Test
    fun `S-GM-06 Delegate failure returns failure without cache or addEphemeralMedia`() =
        runTest {
            val settings = ImageGenerationSettings(generationCount = 1)
            imageGenerationPort.result =
                Result.failure(IllegalStateException("No image generation provider assigned"))

            val result =
                useCase(
                    MediaGenerationRequest(
                        prompt = "p",
                        capability = MediaCapability.IMAGE,
                        settings = settings,
                        origin = MediaGenerationOrigin.CHAT_TOOL,
                    ),
                )

            assertTrue(result.isFailure)
            assertTrue(studioRepository.cacheEphemeralMediaCalls.isEmpty())
            assertTrue(studioRepository.addEphemeralMediaCalls.isEmpty())
            assertEquals("No image generation provider assigned", result.exceptionOrNull()?.message)
        }

    @Test
    fun `S-GM-07 CHAT_TOOL origin uses id prefix chat_media_`() =
        runTest {
            val settings = ImageGenerationSettings(generationCount = 1)
            imageGenerationPort.result = Result.success(listOf(byteArrayOf(1)))
            studioRepository.cacheEphemeralUri = "file:///cache/a"

            val result =
                useCase(
                    MediaGenerationRequest(
                        prompt = "p",
                        capability = MediaCapability.IMAGE,
                        settings = settings,
                        origin = MediaGenerationOrigin.CHAT_TOOL,
                    ),
                )

            assertTrue(
                result
                    .getOrThrow()
                    .first()
                    .id
                    .startsWith("chat_media_"),
            )
        }

    @Test
    fun `S-GM-08 STUDIO_FOREGROUND origin uses createdAt 1000 then 1001 for two assets`() =
        runTest {
            clock.now = 1000L
            val settings = ImageGenerationSettings(generationCount = 1)
            imageGenerationPort.result = Result.success(listOf(byteArrayOf(1), byteArrayOf(2)))
            studioRepository.cacheEphemeralUris.addAll(listOf("file:///cache/a", "file:///cache/b"))

            val result =
                useCase(
                    MediaGenerationRequest(
                        prompt = "p",
                        capability = MediaCapability.IMAGE,
                        settings = settings,
                        origin = MediaGenerationOrigin.STUDIO_FOREGROUND,
                    ),
                )

            val assets = result.getOrThrow()
            assertEquals(1000L, assets[0].createdAt)
            assertEquals(1001L, assets[1].createdAt)
            assertFalse(assets[0].id.startsWith("chat_media_"))
        }

    @Test
    fun `E-GM-01 Mismatched settings type returns IllegalArgumentException`() =
        runTest {
            val result =
                useCase(
                    MediaGenerationRequest(
                        prompt = "p",
                        capability = MediaCapability.IMAGE,
                        settings = MusicGenerationSettings(duration = 30),
                        origin = MediaGenerationOrigin.CHAT_TOOL,
                    ),
                )

            assertTrue(result.isFailure)
            assertInstanceOf(IllegalArgumentException::class.java, result.exceptionOrNull())
            assertEquals(
                "Settings type does not match capability IMAGE",
                result.exceptionOrNull()?.message,
            )
        }

    @Test
    fun `M-GM-01 GenerateMediaUseCase IMAGE path never calls ImageGenerationPort`() =
        runTest {
            val settings = ImageGenerationSettings(generationCount = 1)
            imageGenerationPort.result = Result.success(listOf(byteArrayOf(1)))
            studioRepository.cacheEphemeralUri = "file:///cache/a"

            useCase(
                MediaGenerationRequest(
                    prompt = "p1",
                    capability = MediaCapability.IMAGE,
                    settings = settings,
                    origin = MediaGenerationOrigin.CHAT_TOOL,
                ),
            )

            // GenerateMediaUseCase holds no ImageGenerationPort reference; the single recorded
            // port call arrives exclusively through the GenerateImageUseCase delegate.
            assertEquals(listOf("p1"), imageGenerationPort.calls)
            assertEquals(listOf(settings), imageGenerationPort.settingsCalls)
        }

    @Test
    fun `S-GM-09 STUDIO_FOREGROUND local generationCount 3 generates sequentially and adds incrementally`() =
        runTest {
            clock.now = 1000L
            val settings = ImageGenerationSettings(generationCount = 3)
            seedLocalImageAssignment()
            imageGenerationPort.results.addAll(
                listOf(
                    Result.success(listOf(byteArrayOf(1))),
                    Result.success(listOf(byteArrayOf(2))),
                    Result.success(listOf(byteArrayOf(3))),
                ),
            )
            studioRepository.cacheEphemeralUris.addAll(
                listOf("file:///cache/a", "file:///cache/b", "file:///cache/c"),
            )

            val result =
                useCase(
                    MediaGenerationRequest(
                        prompt = "p",
                        capability = MediaCapability.IMAGE,
                        settings = settings,
                        origin = MediaGenerationOrigin.STUDIO_FOREGROUND,
                    ),
                )

            assertTrue(result.isSuccess)
            assertEquals(3, result.getOrThrow().size)
            assertEquals(3, imageGenerationPort.calls.size)
            assertTrue(
                imageGenerationPort.settingsCalls.all {
                    it is ImageGenerationSettings && it.generationCount == 1
                },
            )
            assertEquals(3, studioRepository.addEphemeralMediaCalls.size)
            assertTrue(
                imageGenerationPort.settingsCalls.none {
                    it is ImageGenerationSettings && it.generationCount == 3
                },
            )
        }

    @Test
    fun `IMAGE forwards progress callback to GenerateImageUseCase`() =
        runTest {
            val settings = ImageGenerationSettings()
            imageGenerationPort.progressToEmit = listOf(ImageGenerationProgress.LoadingModel)
            imageGenerationPort.result = Result.success(listOf(byteArrayOf(1)))
            studioRepository.cacheEphemeralUri = "file:///cache/a"
            val observed = mutableListOf<ImageGenerationProgress>()

            useCase(
                request =
                    MediaGenerationRequest(
                        prompt = "p",
                        capability = MediaCapability.IMAGE,
                        settings = settings,
                        origin = MediaGenerationOrigin.STUDIO_FOREGROUND,
                    ),
                onImageProgress = observed::add,
            )

            assertEquals(listOf(ImageGenerationProgress.LoadingModel), observed)
        }

    /** Re-points the IMAGE_GENERATION default slot at a local SD config (S-GM-09). */
    private fun seedLocalImageAssignment() {
        val configId = LocalModelConfigurationId("sd15-local-config")
        defaultModelRepository.seed(
            DefaultModelAssignment(
                modelType = ModelType.IMAGE_GENERATION,
                localConfigId = configId,
            ),
        )
        localModelRepository.seedAssetForConfig(configId, sd15Asset())
        utilityModelFile.localModelPath = "/abs/models/stable-diffusion-v1-5-Q4_0.gguf"
        gpuCapability.localImageGenerationSupported = true
        capabilityPort.capabilities =
            LocalImageGenerationCapabilities(
                npuAvailable = LocalImageGenTriState.YES,
                sd15NpuSupported = LocalImageGenTriState.YES,
                sd15CpuSupported = LocalImageGenTriState.NO,
                sdxlNpuSupported = LocalImageGenTriState.NO,
                recommendedBackend = LocalImageGenerationBackendKind.QNN_NPU,
                recommendedModelId = LocalImageGenerationModelId.SD15_QNN,
                diagnostics = emptyList(),
            )
    }

    private fun sd15Asset(): LocalModelAsset =
        LocalModelAsset(
            metadata =
                LocalModelMetadata(
                    id = LocalModelId("sd15-model"),
                    displayName = "Stable Diffusion 1.5",
                    huggingFaceModelName = "gpustack/stable-diffusion-v1-5-GGUF",
                    remoteFileName = "stable-diffusion-v1-5-Q4_0.gguf",
                    localFileName = "stable-diffusion-v1-5-Q4_0.gguf",
                    sha256 = LocalImageGenerationModelRegistry.SD15_QNN_SHA256,
                    sizeInBytes = 1_747_190_784L,
                    modelFileFormat = ModelFileFormat.GGUF,
                ),
            configurations =
                listOf(
                    LocalModelConfiguration(
                        id = LocalModelConfigurationId("sd15-local-config"),
                        localModelId = LocalModelId("sd15-model"),
                        displayName = "Stable Diffusion 1.5",
                        maxTokens = 0,
                        contextWindow = 0,
                        temperature = 0.0,
                        topP = 1.0,
                        topK = 1,
                        repetitionPenalty = 1.0,
                        systemPrompt = "",
                        defaultAssignments = listOf(ModelType.IMAGE_GENERATION),
                    ),
                ),
        )

    private class FakeClock : Clock {
        var now: Long = 0L

        override fun currentTimeMillis(): Long = now
    }
}
