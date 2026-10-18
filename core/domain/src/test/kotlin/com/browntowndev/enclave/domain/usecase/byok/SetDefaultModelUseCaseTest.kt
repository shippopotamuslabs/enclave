package com.browntowndev.enclave.domain.usecase.byok

import com.browntowndev.enclave.core.testing.FakeApiModelRepository
import com.browntowndev.enclave.core.testing.FakeGpuCapability
import com.browntowndev.enclave.core.testing.FakeLocalImageGenerationCapability
import com.browntowndev.enclave.core.testing.FakeLocalModelRepository
import com.browntowndev.enclave.core.testing.FakeModelConfigFetcher
import com.browntowndev.enclave.core.testing.FakeModelPackVerifierPort
import com.browntowndev.enclave.core.testing.FakeUtilityModelFile
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
import com.browntowndev.enclave.domain.model.media.LocalImageGenTriState
import com.browntowndev.enclave.domain.model.media.LocalImageGenerationBackendKind
import com.browntowndev.enclave.domain.model.media.LocalImageGenerationCapabilities
import com.browntowndev.enclave.domain.model.media.LocalImageGenerationModelId
import com.browntowndev.enclave.domain.model.media.LocalImageGenerationModelRegistry
import com.browntowndev.enclave.domain.port.media.LOCAL_IMAGE_GENERATION_UNSUPPORTED_MESSAGE
import com.browntowndev.enclave.domain.usecase.FakeDefaultModelRepository
import com.browntowndev.enclave.domain.usecase.download.ResolveModelCatalogUseCase
import com.browntowndev.enclave.domain.usecase.download.VerifyModelPackCompletenessUseCase
import com.browntowndev.enclave.domain.usecase.media.LocalStableDiffusionTargetResolver
import com.browntowndev.enclave.domain.usecase.media.SelectLocalImageGenerationBackendUseCase
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
    private lateinit var apiModelRepository: FakeApiModelRepository
    private lateinit var localModelRepository: FakeLocalModelRepository
    private lateinit var modelPackVerifier: FakeModelPackVerifierPort
    private lateinit var gpuCapabilityPort: FakeGpuCapability
    private lateinit var capabilityPort: FakeLocalImageGenerationCapability
    private lateinit var localStableDiffusionTargetResolver: FakeLocalStableDiffusionTargetResolver
    private lateinit var useCase: SetDefaultModelUseCase

    @BeforeEach
    fun setUp() {
        repository = FakeDefaultModelRepository()
        apiModelRepository = FakeApiModelRepository()
        localModelRepository = FakeLocalModelRepository()
        modelPackVerifier = FakeModelPackVerifierPort()
        gpuCapabilityPort = FakeGpuCapability()
        capabilityPort = FakeLocalImageGenerationCapability()
        localStableDiffusionTargetResolver = FakeLocalStableDiffusionTargetResolver()
        val modelConfigFetcher =
            FakeModelConfigFetcher().apply {
                persistedCatalogResult = Result.success(ModelPackTestFixtures.parsedCatalog())
            }
        val resolveModelCatalog = ResolveModelCatalogUseCase(modelConfigFetcher)
        gpuCapabilityPort.localImageGenerationSupported = true
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
        useCase =
            SetDefaultModelUseCaseImpl(
                repository,
                apiModelRepository,
                localModelRepository,
                VerifyModelPackCompletenessUseCase(modelPackVerifier, resolveModelCatalog),
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
            localModelRepository.seedAssetForConfig(
                configId,
                localAsset(configId, LocalImageGenerationModelRegistry.SD15_QNN_SHA256),
            )
            modelPackVerifier.completenessByPackId[ModelPackTestFixtures.PACK_ID] =
                ModelPackCompleteness(
                    packId = ModelPackTestFixtures.PACK_ID,
                    installedCount = 1,
                    totalCount = 1,
                    isComplete = true,
                    members = emptyList(),
                )

            useCase(ModelType.IMAGE_GENERATION, localConfigId = configId, apiConfigId = null)

            assertEquals(configId, repository.getDefault(ModelType.IMAGE_GENERATION)?.localConfigId)
        }

    @Test
    @DisplayName("MPK-016 Partial assign blocked with exact message")
    fun mpk016_partialAssignBlockedWithExactMessage() =
        runTest {
            val configId = LocalModelConfigurationId(ModelPackTestFixtures.SD15_CONFIG_ID)
            localModelRepository.seedAssetForConfig(
                configId,
                localAsset(configId, LocalImageGenerationModelRegistry.SD15_QNN_SHA256),
            )
            modelPackVerifier.completenessByPackId[ModelPackTestFixtures.PACK_ID] =
                ModelPackCompleteness(
                    packId = ModelPackTestFixtures.PACK_ID,
                    installedCount = 0,
                    totalCount = 1,
                    isComplete = false,
                    members = emptyList(),
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
            localModelRepository.seedAssetForConfig(
                configId,
                localAsset(configId, LocalImageGenerationModelRegistry.SD15_QNN_SHA256),
            )
            modelPackVerifier.completenessByPackId[ModelPackTestFixtures.PACK_ID] =
                ModelPackCompleteness(
                    packId = ModelPackTestFixtures.PACK_ID,
                    installedCount = 0,
                    totalCount = 1,
                    isComplete = false,
                    members = emptyList(),
                )

            assertFailsWith<IllegalArgumentException> {
                useCase(ModelType.IMAGE_GENERATION, localConfigId = configId, apiConfigId = null)
            }
        }

    @Test
    fun `IMAGE_GENERATION local assign blocked when on-device feature disabled`() =
        runTest {
            gpuCapabilityPort.localImageGenerationSupported = false
            val configId = LocalModelConfigurationId(ModelPackTestFixtures.SD15_CONFIG_ID)
            localModelRepository.seedAssetForConfig(
                configId,
                localAsset(configId, LocalImageGenerationModelRegistry.SD15_QNN_SHA256),
            )

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
            localModelRepository.seedAssetForConfig(
                configId,
                localAsset(configId, "ordinary-chat-model-sha"),
            )

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
            apiModelRepository.seedConfigurations(
                listOf(
                    ApiModelConfiguration(
                        id = configId,
                        apiCredentialsId = credId,
                        modelId = "gpt-4o",
                        isMultimodal = false,
                        displayName = "Text Only",
                    ),
                ),
            )
            apiModelRepository.seedCredentials(
                listOf(
                    ApiCredentials(
                        id = credId,
                        displayName = "OpenAI",
                        provider = ApiProvider.OPENAI,
                        credentialAlias = "openai",
                    ),
                ),
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
            apiModelRepository.seedConfigurations(
                listOf(
                    ApiModelConfiguration(
                        id = configId,
                        apiCredentialsId = credId,
                        modelId = "fal-ai/flux",
                        displayName = "FAL Flux",
                    ),
                ),
            )
            apiModelRepository.seedCredentials(
                listOf(
                    ApiCredentials(
                        id = credId,
                        displayName = "FAL",
                        provider = ApiProvider.FAL,
                        credentialAlias = "fal-alias",
                    ),
                ),
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
            apiModelRepository.seedConfigurations(
                listOf(
                    ApiModelConfiguration(
                        id = configId,
                        apiCredentialsId = credId,
                        modelId = "fal-ai/flux",
                        displayName = "FAL Flux",
                    ),
                ),
            )
            apiModelRepository.seedCredentials(
                listOf(
                    ApiCredentials(
                        id = credId,
                        displayName = "FAL",
                        provider = ApiProvider.FAL,
                        credentialAlias = "fal-alias-2",
                    ),
                ),
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
            apiModelRepository.seedConfigurations(
                listOf(
                    ApiModelConfiguration(
                        id = configId,
                        apiCredentialsId = credId,
                        modelId = "claude-opus-4-8",
                        displayName = "Claude",
                    ),
                ),
            )
            apiModelRepository.seedCredentials(
                listOf(
                    ApiCredentials(
                        id = credId,
                        displayName = "Anthropic",
                        provider = ApiProvider.ANTHROPIC,
                        credentialAlias = "anthropic-alias",
                    ),
                ),
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
            apiModelRepository.seedConfigurations(
                listOf(
                    ApiModelConfiguration(
                        id = configId,
                        apiCredentialsId = credId,
                        modelId = "fal-ai/flux-pro",
                        isMultimodal = true,
                        displayName = "FAL Flux",
                    ),
                ),
            )
            apiModelRepository.seedCredentials(
                listOf(
                    ApiCredentials(
                        id = credId,
                        displayName = "FAL",
                        provider = ApiProvider.FAL,
                        credentialAlias = "fal-vision",
                    ),
                ),
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
            localModelRepository.seedAssetForConfig(
                configId,
                localAsset(configId, "5250591bc45e58490c2fd3c0e0e7d4d166ba1aa6f443d5ebdb962c95a9e99f83"),
            )

            capabilityPort.capabilities =
                LocalImageGenerationCapabilities(
                    npuAvailable = LocalImageGenTriState.YES,
                    sd15NpuSupported = LocalImageGenTriState.YES,
                    sd15CpuSupported = LocalImageGenTriState.NO,
                    sdxlNpuSupported = LocalImageGenTriState.NO,
                    recommendedBackend = LocalImageGenerationBackendKind.QNN_NPU,
                    recommendedModelId = LocalImageGenerationModelId.SD15_QNN,
                    diagnostics = emptyList(),
                    disabledReasonsByModelId = mapOf(
                        LocalImageGenerationModelId.SDXL_QNN to "Requires Snapdragon 8 Gen 3+ with QNN."
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
            localModelRepository.seedAssetForConfig(
                configId,
                localAsset(configId, "5250591bc45e58490c2fd3c0e0e7d4d166ba1aa6f443d5ebdb962c95a9e99f83"),
            )

            capabilityPort.capabilities =
                LocalImageGenerationCapabilities(
                    npuAvailable = LocalImageGenTriState.YES,
                    sd15NpuSupported = LocalImageGenTriState.YES,
                    sd15CpuSupported = LocalImageGenTriState.NO,
                    sdxlNpuSupported = LocalImageGenTriState.YES,
                    recommendedBackend = LocalImageGenerationBackendKind.QNN_NPU,
                    recommendedModelId = LocalImageGenerationModelId.SDXL_QNN,
                    diagnostics = emptyList(),
                )

            modelPackVerifier.completenessByPackId["sdxl-t2i"] =
                ModelPackCompleteness(
                    packId = "sdxl-t2i",
                    installedCount = 1,
                    totalCount = 1,
                    isComplete = true,
                    members = emptyList(),
                )

            useCase(ModelType.IMAGE_GENERATION, localConfigId = configId, apiConfigId = null)

            assertEquals(configId, repository.getDefault(ModelType.IMAGE_GENERATION)?.localConfigId)
            assertTrue(capabilityPort.getCapabilitiesCallCount >= 1)
        }

    @Test
    fun `SET-005 incomplete SDXL assignment throws existing pack incomplete message`() =
        runTest {
            val configId = LocalModelConfigurationId("sdxl-local-config")
            localModelRepository.seedAssetForConfig(
                configId,
                localAsset(configId, "5250591bc45e58490c2fd3c0e0e7d4d166ba1aa6f443d5ebdb962c95a9e99f83"),
            )

            capabilityPort.capabilities =
                LocalImageGenerationCapabilities(
                    npuAvailable = LocalImageGenTriState.YES,
                    sd15NpuSupported = LocalImageGenTriState.YES,
                    sd15CpuSupported = LocalImageGenTriState.NO,
                    sdxlNpuSupported = LocalImageGenTriState.YES,
                    recommendedBackend = LocalImageGenerationBackendKind.QNN_NPU,
                    recommendedModelId = LocalImageGenerationModelId.SDXL_QNN,
                    diagnostics = emptyList(),
                )

            modelPackVerifier.completenessByPackId["sdxl-t2i"] =
                ModelPackCompleteness(
                    packId = "sdxl-t2i",
                    installedCount = 0,
                    totalCount = 1,
                    isComplete = false,
                    members = emptyList(),
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
            localModelRepository.seedAssetForConfig(
                configId,
                localAsset(configId, "5250591bc45e58490c2fd3c0e0e7d4d166ba1aa6f443d5ebdb962c95a9e99f83"),
            )
            capabilityPort.capabilities =
                LocalImageGenerationCapabilities(
                    npuAvailable = LocalImageGenTriState.YES,
                    sd15NpuSupported = LocalImageGenTriState.YES,
                    sd15CpuSupported = LocalImageGenTriState.NO,
                    sdxlNpuSupported = LocalImageGenTriState.YES,
                    recommendedBackend = LocalImageGenerationBackendKind.QNN_NPU,
                    recommendedModelId = LocalImageGenerationModelId.SDXL_QNN,
                    diagnostics = emptyList(),
                )
            modelPackVerifier.completenessByPackId["sdxl-t2i"] =
                ModelPackCompleteness(
                    packId = "sdxl-t2i",
                    installedCount = 1,
                    totalCount = 1,
                    isComplete = true,
                    members = emptyList(),
                )
            localStableDiffusionTargetResolver.launchDir = modelDir.absolutePath

            useCase(ModelType.IMAGE_EDIT, localConfigId = configId, apiConfigId = null)

            assertEquals(configId, repository.getDefault(ModelType.IMAGE_EDIT)?.localConfigId)
        }

    @Test
    fun `SET-007 local image edit assignment fails when vae_encoder is missing`() =
        runTest {
            val configId = LocalModelConfigurationId("sdxl-local-config")
            val modelDir = kotlin.io.path.createTempDirectory().toFile()
            localModelRepository.seedAssetForConfig(
                configId,
                localAsset(configId, "5250591bc45e58490c2fd3c0e0e7d4d166ba1aa6f443d5ebdb962c95a9e99f83"),
            )
            capabilityPort.capabilities =
                LocalImageGenerationCapabilities(
                    npuAvailable = LocalImageGenTriState.YES,
                    sd15NpuSupported = LocalImageGenTriState.YES,
                    sd15CpuSupported = LocalImageGenTriState.NO,
                    sdxlNpuSupported = LocalImageGenTriState.YES,
                    recommendedBackend = LocalImageGenerationBackendKind.QNN_NPU,
                    recommendedModelId = LocalImageGenerationModelId.SDXL_QNN,
                    diagnostics = emptyList(),
                )
            modelPackVerifier.completenessByPackId["sdxl-t2i"] =
                ModelPackCompleteness(
                    packId = "sdxl-t2i",
                    installedCount = 1,
                    totalCount = 1,
                    isComplete = true,
                    members = emptyList(),
                )
            localStableDiffusionTargetResolver.launchDir = modelDir.absolutePath

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
            localModelRepository.seedAssetForConfig(
                configId,
                localAsset(configId, ModelPackTestFixtures.JUGGERNAUT_DIFFUSION_SHA),
            )

            capabilityPort.capabilities =
                LocalImageGenerationCapabilities(
                    npuAvailable = LocalImageGenTriState.YES,
                    sd15NpuSupported = LocalImageGenTriState.YES,
                    sd15CpuSupported = LocalImageGenTriState.NO,
                    sdxlNpuSupported = LocalImageGenTriState.NO,
                    juggernautXlNpuSupported = LocalImageGenTriState.NO,
                    recommendedBackend = LocalImageGenerationBackendKind.QNN_NPU,
                    recommendedModelId = LocalImageGenerationModelId.SD15_QNN,
                    diagnostics = emptyList(),
                    disabledReasonsByModelId =
                        mapOf(
                            LocalImageGenerationModelId.JUGGERNAUT_XL_QNN to
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
            localModelRepository.seedAssetForConfig(
                configId,
                localAsset(configId, ModelPackTestFixtures.JUGGERNAUT_DIFFUSION_SHA),
            )

            capabilityPort.capabilities =
                LocalImageGenerationCapabilities(
                    npuAvailable = LocalImageGenTriState.YES,
                    sd15NpuSupported = LocalImageGenTriState.YES,
                    sd15CpuSupported = LocalImageGenTriState.NO,
                    sdxlNpuSupported = LocalImageGenTriState.YES,
                    juggernautXlNpuSupported = LocalImageGenTriState.YES,
                    recommendedBackend = LocalImageGenerationBackendKind.QNN_NPU,
                    recommendedModelId = LocalImageGenerationModelId.SD15_QNN,
                    diagnostics = emptyList(),
                )

            modelPackVerifier.completenessByPackId[ModelPackTestFixtures.JUGGERNAUT_PACK_ID] =
                ModelPackCompleteness(
                    packId = ModelPackTestFixtures.JUGGERNAUT_PACK_ID,
                    installedCount = 2,
                    totalCount = 2,
                    isComplete = true,
                    members = emptyList(),
                )
            localStableDiffusionTargetResolver.launchDir = modelDir.absolutePath

            useCase(ModelType.IMAGE_EDIT, localConfigId = configId, apiConfigId = null)

            assertEquals(configId, repository.getDefault(ModelType.IMAGE_EDIT)?.localConfigId)
        }

    @Test
    fun `SET-010 Juggernaut IMAGE_EDIT assignment without vae_encoder throws encoder message`() =
        runTest {
            val configId = LocalModelConfigurationId(ModelPackTestFixtures.JUGGERNAUT_CONFIG_ID)
            val modelDir = kotlin.io.path.createTempDirectory().toFile()
            localModelRepository.seedAssetForConfig(
                configId,
                localAsset(configId, ModelPackTestFixtures.JUGGERNAUT_DIFFUSION_SHA),
            )

            capabilityPort.capabilities =
                LocalImageGenerationCapabilities(
                    npuAvailable = LocalImageGenTriState.YES,
                    sd15NpuSupported = LocalImageGenTriState.YES,
                    sd15CpuSupported = LocalImageGenTriState.NO,
                    sdxlNpuSupported = LocalImageGenTriState.YES,
                    juggernautXlNpuSupported = LocalImageGenTriState.YES,
                    recommendedBackend = LocalImageGenerationBackendKind.QNN_NPU,
                    recommendedModelId = LocalImageGenerationModelId.SD15_QNN,
                    diagnostics = emptyList(),
                )

            modelPackVerifier.completenessByPackId[ModelPackTestFixtures.JUGGERNAUT_PACK_ID] =
                ModelPackCompleteness(
                    packId = ModelPackTestFixtures.JUGGERNAUT_PACK_ID,
                    installedCount = 2,
                    totalCount = 2,
                    isComplete = true,
                    members = emptyList(),
                )
            localStableDiffusionTargetResolver.launchDir = modelDir.absolutePath

            val error =
                assertFailsWith<IllegalArgumentException> {
                    useCase(ModelType.IMAGE_EDIT, localConfigId = configId, apiConfigId = null)
                }

            assertEquals(SetDefaultModelUseCaseImpl.IMG2IMG_ENCODER_MISSING_MESSAGE, error.message)
        }

    /** Returns a preset launch directory instead of resolving one from disk state. */
    private class FakeLocalStableDiffusionTargetResolver :
        LocalStableDiffusionTargetResolver(
            FakeLocalModelRepository(),
            FakeUtilityModelFile(),
            FakeGpuCapability(),
            FakeLocalImageGenerationCapability(),
            SelectLocalImageGenerationBackendUseCase(),
        ) {
        var launchDir: String? = null

        override suspend fun resolveLaunchDir(localConfigId: LocalModelConfigurationId): String =
            launchDir ?: error("launchDir not set")
    }
}
