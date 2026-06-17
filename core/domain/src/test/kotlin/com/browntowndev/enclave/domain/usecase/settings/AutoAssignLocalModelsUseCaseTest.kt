package com.browntowndev.enclave.domain.usecase.settings

import com.browntowndev.enclave.domain.model.config.ApiCredentials
import com.browntowndev.enclave.domain.model.config.ApiCredentialsId
import com.browntowndev.enclave.domain.model.config.ApiModelAsset
import com.browntowndev.enclave.domain.model.config.ApiModelConfiguration
import com.browntowndev.enclave.domain.model.config.ApiModelConfigurationId
import com.browntowndev.enclave.domain.model.config.DefaultModelAssignment
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
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.model.media.LocalImageGenTriState
import com.browntowndev.enclave.domain.port.media.LocalImageGenerationCapabilityPort
import com.browntowndev.enclave.domain.usecase.byok.GetApiModelAssetsUseCase
import com.browntowndev.enclave.domain.usecase.byok.GetDefaultModelsUseCase
import com.browntowndev.enclave.domain.usecase.byok.SetDefaultModelUseCase
import com.browntowndev.enclave.domain.usecase.media.GetMediaProvidersUseCase
import com.browntowndev.enclave.domain.usecase.modelconfig.GetLocalModelAssetsUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class AutoAssignLocalModelsUseCaseTest {
    private val getLocalModelAssetsUseCase = mockk<GetLocalModelAssetsUseCase>()
    private val getDefaultModelsUseCase = mockk<GetDefaultModelsUseCase>()
    private val setDefaultModelUseCase = mockk<SetDefaultModelUseCase>(relaxed = true)
    private val getApiModelAssetsUseCase = mockk<GetApiModelAssetsUseCase>()
    private val getTtsProvidersUseCase = mockk<GetTtsProvidersUseCase>()
    private val getMediaProvidersUseCase = mockk<GetMediaProvidersUseCase>()
    private val capabilityPort = mockk<LocalImageGenerationCapabilityPort>()

    private val useCase =
        AutoAssignLocalModelsUseCase(
            getLocalModelAssetsUseCase,
            getDefaultModelsUseCase,
            setDefaultModelUseCase,
            getApiModelAssetsUseCase,
            getTtsProvidersUseCase,
            getMediaProvidersUseCase,
            capabilityPort,
        )

    @BeforeEach
    fun setupCapabilities() {
        every { capabilityPort.getCapabilities() } returns
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

    private val localConfigId = LocalModelConfigurationId("local-config-1")
    private val localAsset =
        LocalModelAsset(
            metadata = mockk<LocalModelMetadata>(),
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
            coEvery { getDefaultModelsUseCase() } returns flowOf(emptyList())
            coEvery { getLocalModelAssetsUseCase() } returns flowOf(listOf(localAsset))
            coEvery { getApiModelAssetsUseCase() } returns flowOf(emptyList())
            coEvery { getTtsProvidersUseCase() } returns flowOf(emptyList())
            coEvery { getMediaProvidersUseCase() } returns flowOf(emptyList())

            useCase()

            val textSlots =
                ModelType.entries.filter { it.isTextModel && it != ModelType.VISION }
            textSlots.forEach { slot ->
                coVerify {
                    setDefaultModelUseCase(
                        modelType = slot,
                        localConfigId = localConfigId,
                        apiConfigId = null,
                        ttsProviderId = null,
                        mediaProviderId = null,
                    )
                }
            }
        }

    @Test
    fun `text slots fall back to API config when no local exists`() =
        runTest {
            coEvery { getDefaultModelsUseCase() } returns flowOf(emptyList())
            coEvery { getLocalModelAssetsUseCase() } returns flowOf(emptyList())
            coEvery { getApiModelAssetsUseCase() } returns flowOf(listOf(apiAsset))
            coEvery { getTtsProvidersUseCase() } returns flowOf(emptyList())
            coEvery { getMediaProvidersUseCase() } returns flowOf(emptyList())

            useCase()

            val textSlots =
                ModelType.entries.filter { it.isTextModel && it != ModelType.VISION }
            textSlots.forEach { slot ->
                coVerify {
                    setDefaultModelUseCase(
                        modelType = slot,
                        localConfigId = null,
                        apiConfigId = apiConfigId,
                        ttsProviderId = null,
                        mediaProviderId = null,
                    )
                }
            }
        }

    @Test
    fun `SCN-USECASE-AUTO-001 Auto Assign Vision Uses Multimodal Preset`() =
        runTest {
            coEvery { getDefaultModelsUseCase() } returns flowOf(emptyList())
            coEvery { getLocalModelAssetsUseCase() } returns flowOf(emptyList())
            coEvery { getApiModelAssetsUseCase() } returns flowOf(listOf(apiAsset, multimodalAsset))
            coEvery { getTtsProvidersUseCase() } returns flowOf(emptyList())
            coEvery { getMediaProvidersUseCase() } returns flowOf(emptyList())

            useCase()

            coVerify {
                setDefaultModelUseCase(
                    modelType = ModelType.VISION,
                    localConfigId = null,
                    apiConfigId = multimodalConfigId,
                    ttsProviderId = null,
                    mediaProviderId = null,
                )
            }
        }

    @Test
    fun `vision slot not assigned when no multimodal API available`() =
        runTest {
            coEvery { getDefaultModelsUseCase() } returns flowOf(emptyList())
            coEvery { getLocalModelAssetsUseCase() } returns flowOf(listOf(localAsset))
            coEvery { getApiModelAssetsUseCase() } returns flowOf(listOf(apiAsset))
            coEvery { getTtsProvidersUseCase() } returns flowOf(emptyList())
            coEvery { getMediaProvidersUseCase() } returns flowOf(emptyList())

            useCase()

            coVerify(exactly = 0) {
                setDefaultModelUseCase(modelType = ModelType.VISION, any(), any(), any(), any())
            }
        }

    @Test
    fun `tts slot assigned to first tts provider`() =
        runTest {
            coEvery { getDefaultModelsUseCase() } returns flowOf(emptyList())
            coEvery { getLocalModelAssetsUseCase() } returns flowOf(emptyList())
            coEvery { getApiModelAssetsUseCase() } returns flowOf(emptyList())
            coEvery { getTtsProvidersUseCase() } returns flowOf(listOf(ttsProvider))
            coEvery { getMediaProvidersUseCase() } returns flowOf(emptyList())

            useCase()

            coVerify {
                setDefaultModelUseCase(
                    modelType = ModelType.TTS,
                    localConfigId = null,
                    apiConfigId = null,
                    ttsProviderId = ttsProvider.id,
                    mediaProviderId = null,
                )
            }
        }

    @Test
    fun `media slots assigned by capability`() =
        runTest {
            val image = mediaProvider("media-img", MediaCapability.IMAGE)
            val video = mediaProvider("media-vid", MediaCapability.VIDEO)
            val music = mediaProvider("media-mus", MediaCapability.MUSIC)
            coEvery { getDefaultModelsUseCase() } returns flowOf(emptyList())
            coEvery { getLocalModelAssetsUseCase() } returns flowOf(emptyList())
            coEvery { getApiModelAssetsUseCase() } returns flowOf(emptyList())
            coEvery { getTtsProvidersUseCase() } returns flowOf(emptyList())
            coEvery { getMediaProvidersUseCase() } returns flowOf(listOf(image, video, music))

            useCase()

            coVerify {
                setDefaultModelUseCase(ModelType.IMAGE_GENERATION, null, null, null, image.id)
                setDefaultModelUseCase(ModelType.IMAGE_EDIT, null, null, null, image.id)
                setDefaultModelUseCase(ModelType.VIDEO_GENERATION, null, null, null, video.id)
                setDefaultModelUseCase(ModelType.IMAGE_TO_VIDEO, null, null, null, video.id)
                setDefaultModelUseCase(ModelType.MUSIC_GENERATION, null, null, null, music.id)
            }
        }

    @Test
    fun `image generation slot prefers local SD config when present`() =
        runTest {
            val bonsaiAsset = ModelPackTestFixtures.diffusionAsset()
            val bonsaiConfigId = LocalModelConfigurationId(ModelPackTestFixtures.SD15_CONFIG_ID)
            val image = mediaProvider("media-img", MediaCapability.IMAGE)
            coEvery { getDefaultModelsUseCase() } returns flowOf(emptyList())
            coEvery { getLocalModelAssetsUseCase() } returns flowOf(listOf(bonsaiAsset))
            coEvery { getApiModelAssetsUseCase() } returns flowOf(emptyList())
            coEvery { getTtsProvidersUseCase() } returns flowOf(emptyList())
            coEvery { getMediaProvidersUseCase() } returns flowOf(listOf(image))

            useCase()

            coVerify {
                setDefaultModelUseCase(
                    modelType = ModelType.IMAGE_GENERATION,
                    localConfigId = bonsaiConfigId,
                    apiConfigId = null,
                    ttsProviderId = null,
                    mediaProviderId = null,
                )
            }
            coVerify {
                setDefaultModelUseCase(ModelType.IMAGE_EDIT, null, null, null, image.id)
            }
        }

    @Test
    fun `image generation slot falls back to media provider when no local image config`() =
        runTest {
            val image = mediaProvider("media-img", MediaCapability.IMAGE)
            coEvery { getDefaultModelsUseCase() } returns flowOf(emptyList())
            coEvery { getLocalModelAssetsUseCase() } returns flowOf(listOf(localAsset))
            coEvery { getApiModelAssetsUseCase() } returns flowOf(emptyList())
            coEvery { getTtsProvidersUseCase() } returns flowOf(emptyList())
            coEvery { getMediaProvidersUseCase() } returns flowOf(listOf(image))

            useCase()

            coVerify {
                setDefaultModelUseCase(ModelType.IMAGE_GENERATION, null, null, null, image.id)
            }
        }

    @Test
    fun `valid existing assignment preserved`() =
        runTest {
            val existing =
                DefaultModelAssignment(
                    modelType = ModelType.MAIN,
                    localConfigId = localConfigId,
                )
            coEvery { getDefaultModelsUseCase() } returns flowOf(listOf(existing))
            coEvery { getLocalModelAssetsUseCase() } returns flowOf(listOf(localAsset))
            coEvery { getApiModelAssetsUseCase() } returns flowOf(emptyList())
            coEvery { getTtsProvidersUseCase() } returns flowOf(emptyList())
            coEvery { getMediaProvidersUseCase() } returns flowOf(emptyList())

            useCase()

            coVerify(exactly = 0) {
                setDefaultModelUseCase(modelType = ModelType.MAIN, any(), any(), any(), any())
            }
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
            coEvery { getDefaultModelsUseCase() } returns flowOf(listOf(existing))
            coEvery { getLocalModelAssetsUseCase() } returns flowOf(listOf(localAsset))
            coEvery { getApiModelAssetsUseCase() } returns flowOf(emptyList())
            coEvery { getTtsProvidersUseCase() } returns flowOf(emptyList())
            coEvery { getMediaProvidersUseCase() } returns flowOf(emptyList())

            useCase()

            coVerify {
                setDefaultModelUseCase(
                    modelType = ModelType.MAIN,
                    localConfigId = localConfigId,
                    apiConfigId = null,
                    ttsProviderId = null,
                    mediaProviderId = null,
                )
            }
        }

    @Test
    fun `SET-011 assigns juggernaut-local-config to IMAGE_EDIT when installed`() =
        runTest {
            val juggernautConfigId = LocalModelConfigurationId(ModelPackTestFixtures.JUGGERNAUT_CONFIG_ID)
            val juggernautAsset = ModelPackTestFixtures.juggernautDiffusionAsset()
            coEvery { getDefaultModelsUseCase() } returns flowOf(emptyList())
            coEvery { getLocalModelAssetsUseCase() } returns flowOf(listOf(juggernautAsset))
            coEvery { getApiModelAssetsUseCase() } returns flowOf(emptyList())
            coEvery { getTtsProvidersUseCase() } returns flowOf(emptyList())
            coEvery { getMediaProvidersUseCase() } returns flowOf(emptyList())

            useCase()

            coVerify {
                setDefaultModelUseCase(
                    modelType = ModelType.IMAGE_EDIT,
                    localConfigId = juggernautConfigId,
                    apiConfigId = null,
                    ttsProviderId = null,
                    mediaProviderId = null,
                )
            }
        }

    @Test
    fun `does nothing when no models or providers exist`() =
        runTest {
            coEvery { getDefaultModelsUseCase() } returns flowOf(emptyList())
            coEvery { getLocalModelAssetsUseCase() } returns flowOf(emptyList())
            coEvery { getApiModelAssetsUseCase() } returns flowOf(emptyList())
            coEvery { getTtsProvidersUseCase() } returns flowOf(emptyList())
            coEvery { getMediaProvidersUseCase() } returns flowOf(emptyList())

            useCase()

            coVerify(exactly = 0) { setDefaultModelUseCase(any(), any(), any(), any(), any()) }
        }
}
