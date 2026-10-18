package com.browntowndev.enclave.domain.usecase.settings

import com.browntowndev.enclave.core.testing.FakeApiModelRepository
import com.browntowndev.enclave.core.testing.FakeDefaultModelRepository
import com.browntowndev.enclave.core.testing.FakeLocalImageGenerationCapability
import com.browntowndev.enclave.core.testing.FakeLocalModelRepository
import com.browntowndev.enclave.core.testing.FakeMediaProviderRepository
import com.browntowndev.enclave.core.testing.FakeTtsProviderRepository
import com.browntowndev.enclave.domain.model.config.ApiCredentials
import com.browntowndev.enclave.domain.model.config.ApiCredentialsId
import com.browntowndev.enclave.domain.model.config.ApiModelAsset
import com.browntowndev.enclave.domain.model.config.ApiModelConfiguration
import com.browntowndev.enclave.domain.model.config.ApiModelConfigurationId
import com.browntowndev.enclave.domain.model.config.DefaultModelAssignment
import com.browntowndev.enclave.domain.model.config.LiveVoiceProviderId
import com.browntowndev.enclave.domain.model.config.LocalModelAsset
import com.browntowndev.enclave.domain.model.config.LocalModelConfiguration
import com.browntowndev.enclave.domain.model.config.LocalModelConfigurationId
import com.browntowndev.enclave.domain.model.config.LocalModelId
import com.browntowndev.enclave.domain.model.config.LocalModelMetadata
import com.browntowndev.enclave.domain.model.config.MediaCapability
import com.browntowndev.enclave.domain.model.config.MediaProviderAsset
import com.browntowndev.enclave.domain.model.config.MediaProviderId
import com.browntowndev.enclave.domain.model.config.TtsProviderAsset
import com.browntowndev.enclave.domain.model.config.TtsProviderId
import com.browntowndev.enclave.domain.model.download.ModelPackTestFixtures
import com.browntowndev.enclave.domain.model.inference.ApiProvider
import com.browntowndev.enclave.domain.model.inference.ModelFileFormat
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.model.media.LocalImageGenTriState
import com.browntowndev.enclave.domain.usecase.byok.GetApiModelAssetsUseCaseImpl
import com.browntowndev.enclave.domain.usecase.byok.GetDefaultModelsUseCaseImpl
import com.browntowndev.enclave.domain.usecase.byok.SetDefaultModelUseCase
import com.browntowndev.enclave.domain.usecase.media.GetMediaProvidersUseCase
import com.browntowndev.enclave.domain.usecase.modelconfig.GetLocalModelAssetsUseCaseImpl
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** Recording [SetDefaultModelUseCase] fake: captures every assignment call verbatim. */
private class RecordingSetDefaultModelUseCase : SetDefaultModelUseCase {
    data class Call(
        val modelType: ModelType,
        val localConfigId: LocalModelConfigurationId?,
        val apiConfigId: ApiModelConfigurationId?,
        val ttsProviderId: TtsProviderId?,
        val mediaProviderId: MediaProviderId?,
        val liveVoiceProviderId: LiveVoiceProviderId?,
    )

    val calls = mutableListOf<Call>()

    override suspend fun invoke(
        modelType: ModelType,
        localConfigId: LocalModelConfigurationId?,
        apiConfigId: ApiModelConfigurationId?,
        ttsProviderId: TtsProviderId?,
        mediaProviderId: MediaProviderId?,
        liveVoiceProviderId: LiveVoiceProviderId?,
    ) {
        calls += Call(modelType, localConfigId, apiConfigId, ttsProviderId, mediaProviderId, liveVoiceProviderId)
    }
}

class AutoAssignLocalModelsUseCaseTest {
    private val capabilityPort = FakeLocalImageGenerationCapability()
    private val setDefaultModelUseCase = RecordingSetDefaultModelUseCase()

    @BeforeEach
    fun setupCapabilities() {
        capabilityPort.capabilities =
            com.browntowndev.enclave.domain.model.media.LocalImageGenerationCapabilities(
                npuAvailable = LocalImageGenTriState.YES,
                sd15NpuSupported = LocalImageGenTriState.YES,
                sd15CpuSupported = LocalImageGenTriState.NO,
                sdxlNpuSupported = LocalImageGenTriState.YES,
                juggernautXlNpuSupported = LocalImageGenTriState.YES,
                recommendedBackend =
                    com.browntowndev.enclave.domain.model.media.LocalImageGenerationBackendKind.QNN_NPU,
                recommendedModelId =
                    com.browntowndev.enclave.domain.model.media.LocalImageGenerationModelId.SD15_QNN,
                diagnostics = emptyList(),
            )
    }

    private fun buildUseCase(
        localAssets: List<LocalModelAsset> = emptyList(),
        apiAssets: List<ApiModelAsset> = emptyList(),
        ttsProviders: List<TtsProviderAsset> = emptyList(),
        mediaProviders: List<MediaProviderAsset> = emptyList(),
        existingAssignments: List<DefaultModelAssignment> = emptyList(),
    ): AutoAssignLocalModelsUseCase {
        val localModelRepository = FakeLocalModelRepository()
        localModelRepository.setAssets(localAssets)
        val defaultModelRepository = FakeDefaultModelRepository()
        existingAssignments.forEach { defaultModelRepository.seed(it) }
        val apiModelRepository = FakeApiModelRepository()
        apiModelRepository.seedCredentials(apiAssets.map { it.credentials })
        apiModelRepository.seedConfigurations(apiAssets.flatMap { it.configurations })
        return AutoAssignLocalModelsUseCase(
            GetLocalModelAssetsUseCaseImpl(localModelRepository),
            GetDefaultModelsUseCaseImpl(defaultModelRepository),
            setDefaultModelUseCase,
            GetApiModelAssetsUseCaseImpl(apiModelRepository),
            GetTtsProvidersUseCase(FakeTtsProviderRepository(ttsProviders)),
            GetMediaProvidersUseCase(FakeMediaProviderRepository(mediaProviders)),
            capabilityPort,
        )
    }

    private fun assertAssigned(
        modelType: ModelType,
        localConfigId: LocalModelConfigurationId? = null,
        apiConfigId: ApiModelConfigurationId? = null,
        ttsProviderId: TtsProviderId? = null,
        mediaProviderId: MediaProviderId? = null,
    ) {
        val expected =
            RecordingSetDefaultModelUseCase.Call(
                modelType = modelType,
                localConfigId = localConfigId,
                apiConfigId = apiConfigId,
                ttsProviderId = ttsProviderId,
                mediaProviderId = mediaProviderId,
                liveVoiceProviderId = null,
            )
        assertTrue(
            expected in setDefaultModelUseCase.calls,
            "Expected setDefaultModel call $expected; recorded: ${setDefaultModelUseCase.calls}",
        )
    }

    private fun assertNotAssigned(modelType: ModelType) {
        assertTrue(
            setDefaultModelUseCase.calls.none { it.modelType == modelType },
            "Expected no setDefaultModel call for $modelType; recorded: ${setDefaultModelUseCase.calls}",
        )
    }

    private val localConfigId = LocalModelConfigurationId("local-config-1")
    private val localAsset =
        LocalModelAsset(
            metadata =
                LocalModelMetadata(
                    id = LocalModelId("local-1"),
                    huggingFaceModelName = "local",
                    remoteFileName = "remote",
                    localFileName = "local",
                    sha256 = "sha",
                    sizeInBytes = 100,
                    modelFileFormat = ModelFileFormat.GGUF,
                ),
            configurations =
                listOf(
                    LocalModelConfiguration(
                        id = localConfigId,
                        localModelId = LocalModelId("local-1"),
                        displayName = "Local",
                        maxTokens = 100,
                        contextWindow = 100,
                        temperature = 0.7,
                        topP = 0.9,
                        topK = 40,
                        repetitionPenalty = 1.1,
                        systemPrompt = "",
                    ),
                ),
        )

    private val apiCredsId = ApiCredentialsId("api-creds-1")
    private val apiConfigId = ApiModelConfigurationId("api-config-1")
    private val multimodalCredsId = ApiCredentialsId("api-creds-mm")
    private val multimodalConfigId = ApiModelConfigurationId("api-config-mm")

    private val apiAsset =
        ApiModelAsset(
            credentials =
                ApiCredentials(
                    id = apiCredsId,
                    displayName = "API",
                    provider = ApiProvider.OPENAI,
                    credentialAlias = "alias",
                ),
            configurations =
                listOf(
                    ApiModelConfiguration(
                        id = apiConfigId,
                        apiCredentialsId = apiCredsId,
                        modelId = "gpt-4o",
                        displayName = "API cfg",
                    ),
                ),
        )

    private val multimodalAsset =
        ApiModelAsset(
            credentials =
                ApiCredentials(
                    id = multimodalCredsId,
                    displayName = "API MM",
                    provider = ApiProvider.OPENAI,
                    credentialAlias = "alias-mm",
                ),
            configurations =
                listOf(
                    ApiModelConfiguration(
                        id = multimodalConfigId,
                        apiCredentialsId = multimodalCredsId,
                        modelId = "gpt-4o",
                        isMultimodal = true,
                        displayName = "API MM cfg",
                    ),
                ),
        )

    private val ttsProvider =
        TtsProviderAsset(
            id = TtsProviderId("tts-1"),
            displayName = "TTS",
            provider = ApiProvider.OPENAI,
            voiceName = "v",
            credentialAlias = "alias-tts",
        )

    private fun mediaProvider(
        id: String,
        capability: MediaCapability,
    ) = MediaProviderAsset(
        id = MediaProviderId(id),
        displayName = "Media",
        provider = ApiProvider.OPENAI,
        capability = capability,
        credentialAlias = "alias-media",
    )

    @Test
    fun `text slots without assignment get first local config`() =
        runTest {
            val useCase = buildUseCase(localAssets = listOf(localAsset))

            useCase()

            val textSlots =
                ModelType.entries.filter { it.isTextModel && it != ModelType.VISION }
            textSlots.forEach { slot ->
                assertAssigned(modelType = slot, localConfigId = localConfigId)
            }
        }

    @Test
    fun `text slots fall back to API config when no local exists`() =
        runTest {
            val useCase = buildUseCase(apiAssets = listOf(apiAsset))

            useCase()

            val textSlots =
                ModelType.entries.filter { it.isTextModel && it != ModelType.VISION }
            textSlots.forEach { slot ->
                assertAssigned(modelType = slot, apiConfigId = apiConfigId)
            }
        }

    @Test
    fun `SCN-USECASE-AUTO-001 Auto Assign Vision Uses Multimodal Preset`() =
        runTest {
            val useCase = buildUseCase(apiAssets = listOf(apiAsset, multimodalAsset))

            useCase()

            assertAssigned(modelType = ModelType.VISION, apiConfigId = multimodalConfigId)
        }

    @Test
    fun `vision slot not assigned when no multimodal API available`() =
        runTest {
            val useCase =
                buildUseCase(
                    localAssets = listOf(localAsset),
                    apiAssets = listOf(apiAsset),
                )

            useCase()

            assertNotAssigned(ModelType.VISION)
        }

    @Test
    fun `tts slot assigned to first tts provider`() =
        runTest {
            val useCase = buildUseCase(ttsProviders = listOf(ttsProvider))

            useCase()

            assertAssigned(modelType = ModelType.TTS, ttsProviderId = ttsProvider.id)
        }

    @Test
    fun `media slots assigned by capability`() =
        runTest {
            val image = mediaProvider("media-img", MediaCapability.IMAGE)
            val video = mediaProvider("media-vid", MediaCapability.VIDEO)
            val music = mediaProvider("media-mus", MediaCapability.MUSIC)
            val useCase = buildUseCase(mediaProviders = listOf(image, video, music))

            useCase()

            assertAssigned(modelType = ModelType.IMAGE_GENERATION, mediaProviderId = image.id)
            assertAssigned(modelType = ModelType.IMAGE_EDIT, mediaProviderId = image.id)
            assertAssigned(modelType = ModelType.VIDEO_GENERATION, mediaProviderId = video.id)
            assertAssigned(modelType = ModelType.IMAGE_TO_VIDEO, mediaProviderId = video.id)
            assertAssigned(modelType = ModelType.MUSIC_GENERATION, mediaProviderId = music.id)
        }

    @Test
    fun `image generation slot prefers local SD config when present`() =
        runTest {
            val bonsaiAsset = ModelPackTestFixtures.diffusionAsset()
            val bonsaiConfigId = LocalModelConfigurationId(ModelPackTestFixtures.SD15_CONFIG_ID)
            val image = mediaProvider("media-img", MediaCapability.IMAGE)
            val useCase =
                buildUseCase(
                    localAssets = listOf(bonsaiAsset),
                    mediaProviders = listOf(image),
                )

            useCase()

            assertAssigned(modelType = ModelType.IMAGE_GENERATION, localConfigId = bonsaiConfigId)
            assertAssigned(modelType = ModelType.IMAGE_EDIT, mediaProviderId = image.id)
        }

    @Test
    fun `image generation slot falls back to media provider when no local image config`() =
        runTest {
            val image = mediaProvider("media-img", MediaCapability.IMAGE)
            val useCase =
                buildUseCase(
                    localAssets = listOf(localAsset),
                    mediaProviders = listOf(image),
                )

            useCase()

            assertAssigned(modelType = ModelType.IMAGE_GENERATION, mediaProviderId = image.id)
        }

    @Test
    fun `valid existing assignment preserved`() =
        runTest {
            val existing =
                DefaultModelAssignment(
                    modelType = ModelType.MAIN,
                    localConfigId = localConfigId,
                )
            val useCase =
                buildUseCase(
                    localAssets = listOf(localAsset),
                    existingAssignments = listOf(existing),
                )

            useCase()

            assertNotAssigned(ModelType.MAIN)
        }

    @Test
    fun `dangling existing assignment reassigned to fallback`() =
        runTest {
            val danglingLocal = LocalModelConfigurationId("missing")
            val existing =
                DefaultModelAssignment(
                    modelType = ModelType.MAIN,
                    localConfigId = danglingLocal,
                )
            val useCase =
                buildUseCase(
                    localAssets = listOf(localAsset),
                    existingAssignments = listOf(existing),
                )

            useCase()

            assertAssigned(modelType = ModelType.MAIN, localConfigId = localConfigId)
        }

    @Test
    fun `SET-011 assigns juggernaut-local-config to IMAGE_EDIT when installed`() =
        runTest {
            val juggernautConfigId = LocalModelConfigurationId(ModelPackTestFixtures.JUGGERNAUT_CONFIG_ID)
            val juggernautAsset = ModelPackTestFixtures.juggernautDiffusionAsset()
            val useCase = buildUseCase(localAssets = listOf(juggernautAsset))

            useCase()

            assertAssigned(modelType = ModelType.IMAGE_EDIT, localConfigId = juggernautConfigId)
        }

    @Test
    fun `does nothing when no models or providers exist`() =
        runTest {
            val useCase = buildUseCase()

            useCase()

            assertTrue(setDefaultModelUseCase.calls.isEmpty())
        }
}
