package com.browntowndev.enclave.domain.usecase.byok

import com.browntowndev.enclave.domain.model.config.ApiCredentials
import com.browntowndev.enclave.domain.model.config.ApiCredentialsId
import com.browntowndev.enclave.domain.model.config.ApiModelConfiguration
import com.browntowndev.enclave.domain.model.config.ApiModelConfigurationId
import com.browntowndev.enclave.domain.model.config.LocalModelAsset
import com.browntowndev.enclave.domain.model.config.LocalModelConfiguration
import com.browntowndev.enclave.domain.model.config.LocalModelConfigurationId
import com.browntowndev.enclave.domain.model.config.LocalModelId
import com.browntowndev.enclave.domain.model.config.LocalModelMetadata
import com.browntowndev.enclave.domain.model.config.LiveVoiceProviderId
import com.browntowndev.enclave.domain.model.config.TtsProviderId
import com.browntowndev.enclave.domain.model.download.ModelPackCompleteness
import com.browntowndev.enclave.domain.model.download.ModelPackTestFixtures
import com.browntowndev.enclave.domain.model.inference.ApiProvider
import com.browntowndev.enclave.domain.model.inference.ModelFileFormat
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.model.media.LocalImageGenerationModelRegistry

import com.browntowndev.enclave.domain.port.media.GpuCapabilityPort
import com.browntowndev.enclave.domain.port.media.LOCAL_IMAGE_GENERATION_UNSUPPORTED_MESSAGE
import com.browntowndev.enclave.domain.port.media.LocalImageGenerationCapabilityPort
import com.browntowndev.enclave.domain.port.repository.ApiModelRepositoryPort
import com.browntowndev.enclave.domain.port.repository.LocalModelRepositoryPort
import com.browntowndev.enclave.domain.usecase.FakeDefaultModelRepository
import com.browntowndev.enclave.domain.usecase.download.ResolveModelCatalogUseCase
import com.browntowndev.enclave.domain.usecase.download.VerifyModelPackCompletenessUseCase
import com.browntowndev.enclave.domain.usecase.media.LocalStableDiffusionTargetResolver
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith

class SetDefaultModelUseCaseTest {
    private lateinit var repository: FakeDefaultModelRepository
    private lateinit var apiModelRepository: ApiModelRepositoryPort
    private lateinit var localModelRepository: LocalModelRepositoryPort
    private lateinit var verifyModelPackCompleteness: VerifyModelPackCompletenessUseCase
    private lateinit var resolveModelCatalog: ResolveModelCatalogUseCase
    private lateinit var gpuCapabilityPort: GpuCapabilityPort
    private lateinit var capabilityPort: LocalImageGenerationCapabilityPort
    private lateinit var localStableDiffusionTargetResolver: LocalStableDiffusionTargetResolver
    private lateinit var useCase: SetDefaultModelUseCase

    @BeforeEach
    fun setUp() {
        repository = FakeDefaultModelRepository()
        apiModelRepository = mockk(relaxed = true)
        localModelRepository = mockk(relaxed = true)
        verifyModelPackCompleteness = mockk()
        resolveModelCatalog = mockk()
        gpuCapabilityPort = mockk()
        capabilityPort = mockk()
        localStableDiffusionTargetResolver = mockk()
        coEvery { resolveModelCatalog() } returns Result.success(ModelPackTestFixtures.parsedCatalog())
        every { gpuCapabilityPort.isLocalImageGenerationSupported() } returns true
        every { capabilityPort.getCapabilities() } returns
            com.browntowndev.enclave.domain.model.media.LocalImageGenerationCapabilities(
                npuAvailable = com.browntowndev.enclave.domain.model.media.LocalImageGenTriState.YES,
                sd15NpuSupported = com.browntowndev.enclave.domain.model.media.LocalImageGenTriState.YES,
                sd15CpuSupported = com.browntowndev.enclave.domain.model.media.LocalImageGenTriState.NO,
                sdxlNpuSupported = com.browntowndev.enclave.domain.model.media.LocalImageGenTriState.NO,
                recommendedBackend = com.browntowndev.enclave.domain.model.media.LocalImageGenerationBackendKind.QNN_NPU,
                recommendedModelId = com.browntowndev.enclave.domain.model.media.LocalImageGenerationModelId.SD15_QNN,
                diagnostics = emptyList(),
            )
        useCase =
            SetDefaultModelUseCaseImpl(
                repository,
                apiModelRepository,
                localModelRepository,
                verifyModelPackCompleteness,
                resolveModelCatalog,
                gpuCapabilityPort,
                capabilityPort,
                localStableDiffusionTargetResolver,
            )
    }

    @Test
    fun `set FAST slot to API source`() =
        runTest {
            useCase(ModelType.FAST, localConfigId = null, apiConfigId = ApiModelConfigurationId("5"))

            val assignment = repository.getDefault(ModelType.FAST)
            assertEquals(ApiModelConfigurationId("5"), assignment?.apiConfigId)
        }

    @Test
    fun `set THINKING slot back to ON_DEVICE`() =
        runTest {
            useCase(ModelType.THINKING, localConfigId = LocalModelConfigurationId("1"), apiConfigId = null)

            val assignment = repository.getDefault(ModelType.THINKING)
            assertEquals(LocalModelConfigurationId("1"), assignment?.localConfigId)
        }

    @Test
    fun `set TTS slot to TTS provider`() =
        runTest {
            val ttsId = TtsProviderId("tts-1")
            useCase(ModelType.TTS, localConfigId = null, apiConfigId = null, ttsProviderId = ttsId)

            val assignment = repository.getDefault(ModelType.TTS)
            assertEquals(ttsId, assignment?.ttsProviderId)
        }

    @Test
    @DisplayName("LV-SETDEFAULT-001")
    fun lvSetDefault001_liveVoiceSlotAcceptsOnlyLiveVoiceProviderId() =
        runTest {
            val liveVoiceId = LiveVoiceProviderId("lv-1")
            useCase(
                ModelType.LIVE_VOICE,
                localConfigId = null,
                apiConfigId = null,
                ttsProviderId = null,
                mediaProviderId = null,
                liveVoiceProviderId = liveVoiceId,
            )

            val assignment = repository.getDefault(ModelType.LIVE_VOICE)
            assertEquals(liveVoiceId, assignment?.liveVoiceProviderId)
            assertNull(assignment?.localConfigId)
            assertNull(assignment?.apiConfigId)
            assertNull(assignment?.ttsProviderId)
            assertNull(assignment?.mediaProviderId)

            val error =
                assertFailsWith<IllegalArgumentException> {
                    useCase(
                        ModelType.LIVE_VOICE,
                        localConfigId = null,
                        apiConfigId = ApiModelConfigurationId("api-1"),
                        ttsProviderId = null,
                        mediaProviderId = null,
                        liveVoiceProviderId = liveVoiceId,
                    )
                }
            assertEquals("Live Voice slot is LIVE_VOICE-only.", error.message)
        }

    @Test
    @DisplayName("LV-SETDEFAULT-002")
    fun lvSetDefault002_liveVoiceSlotRejectsMissingProviderId() =
        runTest {
            val error =
                assertFailsWith<IllegalArgumentException> {
                    useCase(
                        ModelType.LIVE_VOICE,
                        localConfigId = null,
                        apiConfigId = null,
                        ttsProviderId = null,
                        mediaProviderId = null,
                        liveVoiceProviderId = null,
                    )
                }

            assertEquals("Live Voice slot requires a Live Voice provider assignment.", error.message)
        }

    @Test
    @DisplayName("MPK-010 Local IMAGE_GENERATION assign allowed when pack complete")
    fun mpk010_localImageGenerationAssignAllowedWhenPackComplete() =
        runTest {
            val configId = LocalModelConfigurationId(ModelPackTestFixtures.SD15_CONFIG_ID)
            coEvery { localModelRepository.getAssetByConfigId(configId) } returns
                localAsset(configId, LocalImageGenerationModelRegistry.SD15_QNN_SHA256)
            coEvery { verifyModelPackCompleteness(ModelPackTestFixtures.PACK_ID) } returns
                Result.success(
                    ModelPackCompleteness(
                        packId = ModelPackTestFixtures.PACK_ID,
                        installedCount = 1,
                        totalCount = 1,
                        isComplete = true,
                        members = emptyList(),
                    ),
                )

            useCase(ModelType.IMAGE_GENERATION, localConfigId = configId, apiConfigId = null)

            assertEquals(configId, repository.getDefault(ModelType.IMAGE_GENERATION)?.localConfigId)
        }

    @Test
    @DisplayName("MPK-016 Partial assign blocked with exact message")
    fun mpk016_partialAssignBlockedWithExactMessage() =
        runTest {
            val configId = LocalModelConfigurationId(ModelPackTestFixtures.SD15_CONFIG_ID)
            coEvery { localModelRepository.getAssetByConfigId(configId) } returns
                localAsset(configId, LocalImageGenerationModelRegistry.SD15_QNN_SHA256)
            coEvery { verifyModelPackCompleteness(ModelPackTestFixtures.PACK_ID) } returns
                Result.success(
                    ModelPackCompleteness(
                        packId = ModelPackTestFixtures.PACK_ID,
                        installedCount = 0,
                        totalCount = 1,
                        isComplete = false,
                        members = emptyList(),
                    ),
                )

            val error =
                assertFailsWith<IllegalArgumentException> {
                    useCase(ModelType.IMAGE_GENERATION, localConfigId = configId, apiConfigId = null)
                }

            assertEquals(ModelPackTestFixtures.PACK_INCOMPLETE_MESSAGE, error.message)
            assertNull(repository.getDefault(ModelType.IMAGE_GENERATION)?.localConfigId)
        }

    @Test
    @DisplayName("MPK-018 Assign does not auto-queue downloads")
    fun mpk018_assignDoesNotAutoQueueDownloads() =
        runTest {
            val configId = LocalModelConfigurationId(ModelPackTestFixtures.SD15_CONFIG_ID)
            coEvery { localModelRepository.getAssetByConfigId(configId) } returns
                localAsset(configId, LocalImageGenerationModelRegistry.SD15_QNN_SHA256)
            coEvery { verifyModelPackCompleteness(ModelPackTestFixtures.PACK_ID) } returns
                Result.success(
                    ModelPackCompleteness(
                        packId = ModelPackTestFixtures.PACK_ID,
                        installedCount = 0,
                        totalCount = 1,
                        isComplete = false,
                        members = emptyList(),
                    ),
                )

            assertFailsWith<IllegalArgumentException> {
                useCase(ModelType.IMAGE_GENERATION, localConfigId = configId, apiConfigId = null)
            }
        }

    @Test
    fun `IMAGE_GENERATION local assign blocked when on-device feature disabled`() =
        runTest {
            every { gpuCapabilityPort.isLocalImageGenerationSupported() } returns false
            val configId = LocalModelConfigurationId(ModelPackTestFixtures.SD15_CONFIG_ID)
            coEvery { localModelRepository.getAssetByConfigId(configId) } returns
                localAsset(configId, LocalImageGenerationModelRegistry.SD15_QNN_SHA256)

            val error =
                assertFailsWith<IllegalArgumentException> {
                    useCase(ModelType.IMAGE_GENERATION, localConfigId = configId, apiConfigId = null)
                }

            assertEquals(LOCAL_IMAGE_GENERATION_UNSUPPORTED_MESSAGE, error.message)
            assertNull(repository.getDefault(ModelType.IMAGE_GENERATION)?.localConfigId)
        }

    @Test
    fun `IMAGE_GENERATION slot rejects ordinary local chat model`() =
        runTest {
            val configId = LocalModelConfigurationId("chat-config")
            coEvery { localModelRepository.getAssetByConfigId(configId) } returns
                localAsset(configId, "ordinary-chat-model-sha")

            val error =
                assertFailsWith<IllegalArgumentException> {
                    useCase(ModelType.IMAGE_GENERATION, localConfigId = configId, apiConfigId = null)
                }

            assertEquals("Image generation slot requires a supported local diffusion model.", error.message)
        }

    @Test
    fun `SCN-USECASE-VISION-001 Vision Default Requires Multimodal Preset`() =
        runTest {
            val configId = ApiModelConfigurationId("config-1")
            val credId = ApiCredentialsId("cred-1")
            coEvery { apiModelRepository.getConfigurationById(configId) } returns
                ApiModelConfiguration(
                    id = configId,
                    apiCredentialsId = credId,
                    modelId = "gpt-4o",
                    isMultimodal = false,
                    displayName = "Text Only",
                )
            coEvery { apiModelRepository.getCredentialsById(credId) } returns
                ApiCredentials(
                    id = credId,
                    displayName = "OpenAI",
                    provider = ApiProvider.OPENAI,
                    credentialAlias = "openai",
                )

            val error =
                assertFailsWith<IllegalArgumentException> {
                    useCase(ModelType.VISION, localConfigId = null, apiConfigId = configId)
                }

            assertTrue(error.message!!.contains("multimodal", ignoreCase = true))
        }

    @Test
    fun `set VISION slot rejects local model assignments`() =
        runTest {
            val error =
                assertFailsWith<IllegalArgumentException> {
                    useCase(ModelType.VISION, localConfigId = LocalModelConfigurationId("vision-local"), apiConfigId = null)
                }

            assertEquals("Vision slot is API-only.", error.message)
        }

    @Test
    fun `SCN-USECASE-CHAT-001 MAIN slot rejects provider that does not support chat`() =
        runTest {
            val configId = ApiModelConfigurationId("config-fal")
            val credId = ApiCredentialsId("cred-fal")
            coEvery { apiModelRepository.getConfigurationById(configId) } returns
                ApiModelConfiguration(
                    id = configId,
                    apiCredentialsId = credId,
                    modelId = "fal-ai/flux",
                    displayName = "FAL Flux",
                )
            coEvery { apiModelRepository.getCredentialsById(credId) } returns
                ApiCredentials(
                    id = credId,
                    displayName = "FAL",
                    provider = ApiProvider.FAL,
                    credentialAlias = "fal-alias",
                )

            val error =
                assertFailsWith<IllegalArgumentException> {
                    useCase(ModelType.MAIN, localConfigId = null, apiConfigId = configId)
                }

            assertTrue(error.message!!.contains("does not support chat", ignoreCase = true))
        }

    @Test
    fun `SCN-USECASE-CHAT-002 THINKING slot rejects provider that does not support chat`() =
        runTest {
            val configId = ApiModelConfigurationId("config-fal-2")
            val credId = ApiCredentialsId("cred-fal-2")
            coEvery { apiModelRepository.getConfigurationById(configId) } returns
                ApiModelConfiguration(
                    id = configId,
                    apiCredentialsId = credId,
                    modelId = "fal-ai/flux",
                    displayName = "FAL Flux",
                )
            coEvery { apiModelRepository.getCredentialsById(credId) } returns
                ApiCredentials(
                    id = credId,
                    displayName = "FAL",
                    provider = ApiProvider.FAL,
                    credentialAlias = "fal-alias-2",
                )

            val error =
                assertFailsWith<IllegalArgumentException> {
                    useCase(ModelType.THINKING, localConfigId = null, apiConfigId = configId)
                }

            assertTrue(error.message!!.contains("does not support chat", ignoreCase = true))
        }

    @Test
    fun `MAIN slot accepts provider that supports chat`() =
        runTest {
            val configId = ApiModelConfigurationId("config-anthropic")
            val credId = ApiCredentialsId("cred-anthropic")
            coEvery { apiModelRepository.getConfigurationById(configId) } returns
                ApiModelConfiguration(
                    id = configId,
                    apiCredentialsId = credId,
                    modelId = "claude-opus-4-8",
                    displayName = "Claude",
                )
            coEvery { apiModelRepository.getCredentialsById(credId) } returns
                ApiCredentials(
                    id = credId,
                    displayName = "Anthropic",
                    provider = ApiProvider.ANTHROPIC,
                    credentialAlias = "anthropic-alias",
                )

            useCase(ModelType.MAIN, localConfigId = null, apiConfigId = configId)

            val assignment = repository.getDefault(ModelType.MAIN)
            assertEquals(configId, assignment?.apiConfigId)
        }

    @Test
    fun `VISION slot rejects provider that does not support chat`() =
        runTest {
            val configId = ApiModelConfigurationId("config-fal-vision")
            val credId = ApiCredentialsId("cred-fal-vision")
            coEvery { apiModelRepository.getConfigurationById(configId) } returns
                ApiModelConfiguration(
                    id = configId,
                    apiCredentialsId = credId,
                    modelId = "fal-ai/flux-pro",
                    isMultimodal = true,
                    displayName = "FAL Flux",
                )
            coEvery { apiModelRepository.getCredentialsById(credId) } returns
                ApiCredentials(
                    id = credId,
                    displayName = "FAL",
                    provider = ApiProvider.FAL,
                    credentialAlias = "fal-vision",
                )

            val error =
                assertFailsWith<IllegalArgumentException> {
                    useCase(ModelType.VISION, localConfigId = null, apiConfigId = configId)
                }

            assertTrue(error.message!!.contains("does not support vision", ignoreCase = true))
        }

    private fun localAsset(
        configId: LocalModelConfigurationId,
        sha256: String,
    ): LocalModelAsset =
        LocalModelAsset(
            metadata =
                LocalModelMetadata(
                    id = LocalModelId("local-model"),
                    displayName = "Local Model",
                    huggingFaceModelName = "local/model",
                    remoteFileName = "model.gguf",
                    localFileName = "model.gguf",
                    sha256 = sha256,
                    sizeInBytes = 100L,
                    modelFileFormat = ModelFileFormat.GGUF,
                ),
            configurations =
                listOf(
                    LocalModelConfiguration(
                        id = configId,
                        localModelId = LocalModelId("local-model"),
                        displayName = "Default",
                        maxTokens = 512,
                        contextWindow = 4096,
                        temperature = 0.7,
                        topP = 0.95,
                        topK = 40,
                        repetitionPenalty = 1.0,
                        systemPrompt = "",
                    ),
                ),
        )

    @Test
    fun `SET-003 direct Gen2 SDXL assignment throws exact IllegalArgumentException`() =
        runTest {
            val configId = LocalModelConfigurationId("sdxl-local-config")
            coEvery { localModelRepository.getAssetByConfigId(configId) } returns
                localAsset(configId, "5250591bc45e58490c2fd3c0e0e7d4d166ba1aa6f443d5ebdb962c95a9e99f83")

            every { capabilityPort.getCapabilities() } returns
                com.browntowndev.enclave.domain.model.media.LocalImageGenerationCapabilities(
                    npuAvailable = com.browntowndev.enclave.domain.model.media.LocalImageGenTriState.YES,
                    sd15NpuSupported = com.browntowndev.enclave.domain.model.media.LocalImageGenTriState.YES,
                    sd15CpuSupported = com.browntowndev.enclave.domain.model.media.LocalImageGenTriState.NO,
                    sdxlNpuSupported = com.browntowndev.enclave.domain.model.media.LocalImageGenTriState.NO,
                    recommendedBackend = com.browntowndev.enclave.domain.model.media.LocalImageGenerationBackendKind.QNN_NPU,
                    recommendedModelId = com.browntowndev.enclave.domain.model.media.LocalImageGenerationModelId.SD15_QNN,
                    diagnostics = emptyList(),
                    disabledReasonsByModelId = mapOf(
                        com.browntowndev.enclave.domain.model.media.LocalImageGenerationModelId.SDXL_QNN to "Requires Snapdragon 8 Gen 3+ with QNN."
                    )
                )

            val error =
                assertFailsWith<IllegalArgumentException> {
                    useCase(ModelType.IMAGE_GENERATION, localConfigId = configId, apiConfigId = null)
                }

            assertEquals("Requires Snapdragon 8 Gen 3+ with QNN.", error.message)
        }

    @Test
    fun `SET-004 direct Gen3 SDXL assignment succeeds when pack complete`() =
        runTest {
            val configId = LocalModelConfigurationId("sdxl-local-config")
            coEvery { localModelRepository.getAssetByConfigId(configId) } returns
                localAsset(configId, "5250591bc45e58490c2fd3c0e0e7d4d166ba1aa6f443d5ebdb962c95a9e99f83")

            every { capabilityPort.getCapabilities() } returns
                com.browntowndev.enclave.domain.model.media.LocalImageGenerationCapabilities(
                    npuAvailable = com.browntowndev.enclave.domain.model.media.LocalImageGenTriState.YES,
                    sd15NpuSupported = com.browntowndev.enclave.domain.model.media.LocalImageGenTriState.YES,
                    sd15CpuSupported = com.browntowndev.enclave.domain.model.media.LocalImageGenTriState.NO,
                    sdxlNpuSupported = com.browntowndev.enclave.domain.model.media.LocalImageGenTriState.YES,
                    recommendedBackend = com.browntowndev.enclave.domain.model.media.LocalImageGenerationBackendKind.QNN_NPU,
                    recommendedModelId = com.browntowndev.enclave.domain.model.media.LocalImageGenerationModelId.SDXL_QNN,
                    diagnostics = emptyList(),
                )

            coEvery { verifyModelPackCompleteness("sdxl-t2i") } returns
                Result.success(
                    ModelPackCompleteness(
                        packId = "sdxl-t2i",
                        installedCount = 1,
                        totalCount = 1,
                        isComplete = true,
                        members = emptyList(),
                    ),
                )

            useCase(ModelType.IMAGE_GENERATION, localConfigId = configId, apiConfigId = null)

            assertEquals(configId, repository.getDefault(ModelType.IMAGE_GENERATION)?.localConfigId)
            verify { capabilityPort.getCapabilities() }
        }

    @Test
    fun `SET-005 incomplete SDXL assignment throws existing pack incomplete message`() =
        runTest {
            val configId = LocalModelConfigurationId("sdxl-local-config")
            coEvery { localModelRepository.getAssetByConfigId(configId) } returns
                localAsset(configId, "5250591bc45e58490c2fd3c0e0e7d4d166ba1aa6f443d5ebdb962c95a9e99f83")

            every { capabilityPort.getCapabilities() } returns
                com.browntowndev.enclave.domain.model.media.LocalImageGenerationCapabilities(
                    npuAvailable = com.browntowndev.enclave.domain.model.media.LocalImageGenTriState.YES,
                    sd15NpuSupported = com.browntowndev.enclave.domain.model.media.LocalImageGenTriState.YES,
                    sd15CpuSupported = com.browntowndev.enclave.domain.model.media.LocalImageGenTriState.NO,
                    sdxlNpuSupported = com.browntowndev.enclave.domain.model.media.LocalImageGenTriState.YES,
                    recommendedBackend = com.browntowndev.enclave.domain.model.media.LocalImageGenerationBackendKind.QNN_NPU,
                    recommendedModelId = com.browntowndev.enclave.domain.model.media.LocalImageGenerationModelId.SDXL_QNN,
                    diagnostics = emptyList(),
                )

            coEvery { verifyModelPackCompleteness("sdxl-t2i") } returns
                Result.success(
                    ModelPackCompleteness(
                        packId = "sdxl-t2i",
                        installedCount = 0,
                        totalCount = 1,
                        isComplete = false,
                        members = emptyList(),
                    ),
                )

            val error =
                assertFailsWith<IllegalArgumentException> {
                    useCase(ModelType.IMAGE_GENERATION, localConfigId = configId, apiConfigId = null)
                }

            assertEquals(
                "Local image pack is not fully downloaded. Download all pack files from Local AI Models before assigning.",
                error.message,
            )
        }

    @Test
    fun `SET-006 local image edit assignment succeeds when vae_encoder is present`() =
        runTest {
            val configId = LocalModelConfigurationId("sdxl-local-config")
            val modelDir = kotlin.io.path.createTempDirectory().toFile()
            java.io.File(modelDir, "vae_encoder.bin").writeText("encoder")
            coEvery { localModelRepository.getAssetByConfigId(configId) } returns
                localAsset(configId, "5250591bc45e58490c2fd3c0e0e7d4d166ba1aa6f443d5ebdb962c95a9e99f83")
            every { capabilityPort.getCapabilities() } returns
                com.browntowndev.enclave.domain.model.media.LocalImageGenerationCapabilities(
                    npuAvailable = com.browntowndev.enclave.domain.model.media.LocalImageGenTriState.YES,
                    sd15NpuSupported = com.browntowndev.enclave.domain.model.media.LocalImageGenTriState.YES,
                    sd15CpuSupported = com.browntowndev.enclave.domain.model.media.LocalImageGenTriState.NO,
                    sdxlNpuSupported = com.browntowndev.enclave.domain.model.media.LocalImageGenTriState.YES,
                    recommendedBackend = com.browntowndev.enclave.domain.model.media.LocalImageGenerationBackendKind.QNN_NPU,
                    recommendedModelId = com.browntowndev.enclave.domain.model.media.LocalImageGenerationModelId.SDXL_QNN,
                    diagnostics = emptyList(),
                )
            coEvery { verifyModelPackCompleteness("sdxl-t2i") } returns
                Result.success(
                    ModelPackCompleteness(
                        packId = "sdxl-t2i",
                        installedCount = 1,
                        totalCount = 1,
                        isComplete = true,
                        members = emptyList(),
                    ),
                )
            coEvery { localStableDiffusionTargetResolver.resolveLaunchDir(configId) } returns modelDir.absolutePath

            useCase(ModelType.IMAGE_EDIT, localConfigId = configId, apiConfigId = null)

            assertEquals(configId, repository.getDefault(ModelType.IMAGE_EDIT)?.localConfigId)
        }

    @Test
    fun `SET-007 local image edit assignment fails when vae_encoder is missing`() =
        runTest {
            val configId = LocalModelConfigurationId("sdxl-local-config")
            val modelDir = kotlin.io.path.createTempDirectory().toFile()
            coEvery { localModelRepository.getAssetByConfigId(configId) } returns
                localAsset(configId, "5250591bc45e58490c2fd3c0e0e7d4d166ba1aa6f443d5ebdb962c95a9e99f83")
            every { capabilityPort.getCapabilities() } returns
                com.browntowndev.enclave.domain.model.media.LocalImageGenerationCapabilities(
                    npuAvailable = com.browntowndev.enclave.domain.model.media.LocalImageGenTriState.YES,
                    sd15NpuSupported = com.browntowndev.enclave.domain.model.media.LocalImageGenTriState.YES,
                    sd15CpuSupported = com.browntowndev.enclave.domain.model.media.LocalImageGenTriState.NO,
                    sdxlNpuSupported = com.browntowndev.enclave.domain.model.media.LocalImageGenTriState.YES,
                    recommendedBackend = com.browntowndev.enclave.domain.model.media.LocalImageGenerationBackendKind.QNN_NPU,
                    recommendedModelId = com.browntowndev.enclave.domain.model.media.LocalImageGenerationModelId.SDXL_QNN,
                    diagnostics = emptyList(),
                )
            coEvery { verifyModelPackCompleteness("sdxl-t2i") } returns
                Result.success(
                    ModelPackCompleteness(
                        packId = "sdxl-t2i",
                        installedCount = 1,
                        totalCount = 1,
                        isComplete = true,
                        members = emptyList(),
                    ),
                )
            coEvery { localStableDiffusionTargetResolver.resolveLaunchDir(configId) } returns modelDir.absolutePath

            val error =
                assertFailsWith<IllegalArgumentException> {
                    useCase(ModelType.IMAGE_EDIT, localConfigId = configId, apiConfigId = null)
                }

            assertEquals(SetDefaultModelUseCaseImpl.IMG2IMG_ENCODER_MISSING_MESSAGE, error.message)
        }

    @Test
    fun `SET-008 direct Gen2 Juggernaut assignment throws exact IllegalArgumentException`() =
        runTest {
            val configId = LocalModelConfigurationId(ModelPackTestFixtures.JUGGERNAUT_CONFIG_ID)
            coEvery { localModelRepository.getAssetByConfigId(configId) } returns
                localAsset(configId, ModelPackTestFixtures.JUGGERNAUT_DIFFUSION_SHA)

            every { capabilityPort.getCapabilities() } returns
                com.browntowndev.enclave.domain.model.media.LocalImageGenerationCapabilities(
                    npuAvailable = com.browntowndev.enclave.domain.model.media.LocalImageGenTriState.YES,
                    sd15NpuSupported = com.browntowndev.enclave.domain.model.media.LocalImageGenTriState.YES,
                    sd15CpuSupported = com.browntowndev.enclave.domain.model.media.LocalImageGenTriState.NO,
                    sdxlNpuSupported = com.browntowndev.enclave.domain.model.media.LocalImageGenTriState.NO,
                    juggernautXlNpuSupported = com.browntowndev.enclave.domain.model.media.LocalImageGenTriState.NO,
                    recommendedBackend = com.browntowndev.enclave.domain.model.media.LocalImageGenerationBackendKind.QNN_NPU,
                    recommendedModelId = com.browntowndev.enclave.domain.model.media.LocalImageGenerationModelId.SD15_QNN,
                    diagnostics = emptyList(),
                    disabledReasonsByModelId =
                        mapOf(
                            com.browntowndev.enclave.domain.model.media.LocalImageGenerationModelId.JUGGERNAUT_XL_QNN to
                                "Requires Snapdragon 8 Gen 3+ with QNN.",
                        ),
                )

            val error =
                assertFailsWith<IllegalArgumentException> {
                    useCase(ModelType.IMAGE_EDIT, localConfigId = configId, apiConfigId = null)
                }

            assertEquals("Requires Snapdragon 8 Gen 3+ with QNN.", error.message)
        }

    @Test
    fun `SET-009 direct Gen3 Juggernaut IMAGE_EDIT assignment succeeds when pack complete`() =
        runTest {
            val configId = LocalModelConfigurationId(ModelPackTestFixtures.JUGGERNAUT_CONFIG_ID)
            val modelDir = kotlin.io.path.createTempDirectory().toFile()
            java.io.File(modelDir, "vae_encoder.bin").writeText("encoder")
            coEvery { localModelRepository.getAssetByConfigId(configId) } returns
                localAsset(configId, ModelPackTestFixtures.JUGGERNAUT_DIFFUSION_SHA)

            every { capabilityPort.getCapabilities() } returns
                com.browntowndev.enclave.domain.model.media.LocalImageGenerationCapabilities(
                    npuAvailable = com.browntowndev.enclave.domain.model.media.LocalImageGenTriState.YES,
                    sd15NpuSupported = com.browntowndev.enclave.domain.model.media.LocalImageGenTriState.YES,
                    sd15CpuSupported = com.browntowndev.enclave.domain.model.media.LocalImageGenTriState.NO,
                    sdxlNpuSupported = com.browntowndev.enclave.domain.model.media.LocalImageGenTriState.YES,
                    juggernautXlNpuSupported = com.browntowndev.enclave.domain.model.media.LocalImageGenTriState.YES,
                    recommendedBackend = com.browntowndev.enclave.domain.model.media.LocalImageGenerationBackendKind.QNN_NPU,
                    recommendedModelId = com.browntowndev.enclave.domain.model.media.LocalImageGenerationModelId.SD15_QNN,
                    diagnostics = emptyList(),
                )

            coEvery { verifyModelPackCompleteness(ModelPackTestFixtures.JUGGERNAUT_PACK_ID) } returns
                Result.success(
                    ModelPackCompleteness(
                        packId = ModelPackTestFixtures.JUGGERNAUT_PACK_ID,
                        installedCount = 2,
                        totalCount = 2,
                        isComplete = true,
                        members = emptyList(),
                    ),
                )
            coEvery { localStableDiffusionTargetResolver.resolveLaunchDir(configId) } returns modelDir.absolutePath

            useCase(ModelType.IMAGE_EDIT, localConfigId = configId, apiConfigId = null)

            assertEquals(configId, repository.getDefault(ModelType.IMAGE_EDIT)?.localConfigId)
        }

    @Test
    fun `SET-010 Juggernaut IMAGE_EDIT assignment without vae_encoder throws encoder message`() =
        runTest {
            val configId = LocalModelConfigurationId(ModelPackTestFixtures.JUGGERNAUT_CONFIG_ID)
            val modelDir = kotlin.io.path.createTempDirectory().toFile()
            coEvery { localModelRepository.getAssetByConfigId(configId) } returns
                localAsset(configId, ModelPackTestFixtures.JUGGERNAUT_DIFFUSION_SHA)

            every { capabilityPort.getCapabilities() } returns
                com.browntowndev.enclave.domain.model.media.LocalImageGenerationCapabilities(
                    npuAvailable = com.browntowndev.enclave.domain.model.media.LocalImageGenTriState.YES,
                    sd15NpuSupported = com.browntowndev.enclave.domain.model.media.LocalImageGenTriState.YES,
                    sd15CpuSupported = com.browntowndev.enclave.domain.model.media.LocalImageGenTriState.NO,
                    sdxlNpuSupported = com.browntowndev.enclave.domain.model.media.LocalImageGenTriState.YES,
                    juggernautXlNpuSupported = com.browntowndev.enclave.domain.model.media.LocalImageGenTriState.YES,
                    recommendedBackend = com.browntowndev.enclave.domain.model.media.LocalImageGenerationBackendKind.QNN_NPU,
                    recommendedModelId = com.browntowndev.enclave.domain.model.media.LocalImageGenerationModelId.SD15_QNN,
                    diagnostics = emptyList(),
                )

            coEvery { verifyModelPackCompleteness(ModelPackTestFixtures.JUGGERNAUT_PACK_ID) } returns
                Result.success(
                    ModelPackCompleteness(
                        packId = ModelPackTestFixtures.JUGGERNAUT_PACK_ID,
                        installedCount = 2,
                        totalCount = 2,
                        isComplete = true,
                        members = emptyList(),
                    ),
                )
            coEvery { localStableDiffusionTargetResolver.resolveLaunchDir(configId) } returns modelDir.absolutePath

            val error =
                assertFailsWith<IllegalArgumentException> {
                    useCase(ModelType.IMAGE_EDIT, localConfigId = configId, apiConfigId = null)
                }

            assertEquals(SetDefaultModelUseCaseImpl.IMG2IMG_ENCODER_MISSING_MESSAGE, error.message)
        }
}
