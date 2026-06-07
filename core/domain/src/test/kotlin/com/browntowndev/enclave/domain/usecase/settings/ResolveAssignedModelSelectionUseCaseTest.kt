package com.browntowndev.enclave.domain.usecase.settings

import com.browntowndev.enclave.domain.model.config.ApiModelAsset
import com.browntowndev.enclave.domain.model.config.ApiModelConfiguration
import com.browntowndev.enclave.domain.model.config.ApiModelConfigurationId
import com.browntowndev.enclave.domain.model.config.DefaultModelAssignment
import com.browntowndev.enclave.domain.model.config.LocalModelAsset
import com.browntowndev.enclave.domain.model.config.LocalModelConfiguration
import com.browntowndev.enclave.domain.model.config.LocalModelConfigurationId
import com.browntowndev.enclave.domain.model.config.LiveVoiceProviderAsset
import com.browntowndev.enclave.domain.model.config.LiveVoiceProviderId
import com.browntowndev.enclave.domain.model.config.TtsProviderAsset
import com.browntowndev.enclave.domain.model.config.TtsProviderId
import com.browntowndev.enclave.domain.model.inference.ApiProvider
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.usecase.byok.GetApiModelAssetsUseCase
import com.browntowndev.enclave.domain.usecase.byok.GetDefaultModelsUseCase
import com.browntowndev.enclave.domain.usecase.modelconfig.GetLocalModelAssetsUseCase
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class ResolveAssignedModelSelectionUseCaseTest {
    private lateinit var getDefaultModelsUseCase: GetDefaultModelsUseCase
    private lateinit var getLocalModelAssetsUseCase: GetLocalModelAssetsUseCase
    private lateinit var getApiModelAssetsUseCase: GetApiModelAssetsUseCase
    private lateinit var getTtsProvidersUseCase: GetTtsProvidersUseCase
    private lateinit var getLiveVoiceProvidersUseCase: GetLiveVoiceProvidersUseCase
    private lateinit var useCase: ResolveAssignedModelSelectionUseCase

    @BeforeEach
    fun setup() {
        getDefaultModelsUseCase = mockk()
        getLocalModelAssetsUseCase = mockk()
        getApiModelAssetsUseCase = mockk()
        getTtsProvidersUseCase = mockk()
        getLiveVoiceProvidersUseCase = mockk()
        useCase =
            ResolveAssignedModelSelectionUseCase(
                getDefaultModelsUseCase,
                getLocalModelAssetsUseCase,
                getApiModelAssetsUseCase,
                getTtsProvidersUseCase,
                getLiveVoiceProvidersUseCase,
            )
    }

    @Test
    fun `returns null if assignment not found for model type`() =
        runTest {
            every { getDefaultModelsUseCase() } returns flowOf(emptyList())

            val result = useCase(ModelType.MAIN)

            assertNull(result)
        }

    @Test
    fun `returns null if all config ids are null`() =
        runTest {
            val assignment =
                DefaultModelAssignment(
                    modelType = ModelType.MAIN,
                    localConfigId = null,
                    apiConfigId = null,
                    ttsProviderId = null,
                )
            every { getDefaultModelsUseCase() } returns flowOf(listOf(assignment))

            val result = useCase(ModelType.MAIN)

            assertNull(result)
        }

    @Test
    fun `returns local asset and config if localConfigId matches an existing asset`() =
        runTest {
            val localConfigId = LocalModelConfigurationId("local-config-1")
            val assignment =
                DefaultModelAssignment(
                    modelType = ModelType.MAIN,
                    localConfigId = localConfigId,
                    apiConfigId = null,
                )
            val localConfig =
                mockk<LocalModelConfiguration> {
                    every { id } returns localConfigId
                }
            val localAsset =
                mockk<LocalModelAsset> {
                    every { configurations } returns listOf(localConfig)
                }

            every { getDefaultModelsUseCase() } returns flowOf(listOf(assignment))
            every { getLocalModelAssetsUseCase() } returns flowOf(listOf(localAsset))

            val result = useCase(ModelType.MAIN)

            assertEquals(ResolvedAssignedModelSelection(localAsset = localAsset, localConfig = localConfig), result)
        }

    @Test
    fun `returns api asset and config if apiConfigId matches an existing asset`() =
        runTest {
            val apiConfigId = ApiModelConfigurationId("api-config-1")
            val assignment =
                DefaultModelAssignment(
                    modelType = ModelType.MAIN,
                    localConfigId = null,
                    apiConfigId = apiConfigId,
                )
            val apiConfig =
                mockk<ApiModelConfiguration> {
                    every { id } returns apiConfigId
                }
            val apiAsset =
                mockk<ApiModelAsset> {
                    every { configurations } returns listOf(apiConfig)
                }

            every { getDefaultModelsUseCase() } returns flowOf(listOf(assignment))
            every { getApiModelAssetsUseCase() } returns flowOf(listOf(apiAsset))

            val result = useCase(ModelType.MAIN)

            assertEquals(ResolvedAssignedModelSelection(apiAsset = apiAsset, apiConfig = apiConfig), result)
        }

    @Test
    fun `returns tts asset if ttsProviderId matches an existing asset`() =
        runTest {
            val ttsId = TtsProviderId("tts-1")
            val assignment =
                DefaultModelAssignment(
                    modelType = ModelType.TTS,
                    localConfigId = null,
                    apiConfigId = null,
                    ttsProviderId = ttsId,
                )
            val ttsAsset = TtsProviderAsset(ttsId, "OpenAI", ApiProvider.OPENAI, "alloy", null, null, "alias")

            every { getDefaultModelsUseCase() } returns flowOf(listOf(assignment))
            every { getTtsProvidersUseCase() } returns flowOf(listOf(ttsAsset))

            val result = useCase(ModelType.TTS)

            assertEquals(ResolvedAssignedModelSelection(ttsAsset = ttsAsset), result)
        }

    @Test
    @DisplayName("LV-RESOLVE-001")
    fun lvResolve001_resolvesLiveVoiceAsset() =
        runTest {
            val liveVoiceId = LiveVoiceProviderId("lv-1")
            val assignment =
                DefaultModelAssignment(
                    modelType = ModelType.LIVE_VOICE,
                    localConfigId = null,
                    apiConfigId = null,
                    liveVoiceProviderId = liveVoiceId,
                )
            val liveVoiceAsset =
                LiveVoiceProviderAsset(
                    id = liveVoiceId,
                    displayName = "OpenAI RT",
                    provider = ApiProvider.OPENAI,
                    voiceName = "alloy",
                    modelName = null,
                    baseUrl = null,
                    credentialAlias = "alias",
                )

            every { getDefaultModelsUseCase() } returns flowOf(listOf(assignment))
            every { getLiveVoiceProvidersUseCase() } returns flowOf(listOf(liveVoiceAsset))

            val result = useCase(ModelType.LIVE_VOICE)

            assertEquals(ResolvedAssignedModelSelection(liveVoiceAsset = liveVoiceAsset), result)
        }

    @Test
    @DisplayName("LV-RESOLVE-002")
    fun lvResolve002_returnsNullWhenAssetAbsent() =
        runTest {
            val assignment =
                DefaultModelAssignment(
                    modelType = ModelType.LIVE_VOICE,
                    localConfigId = null,
                    apiConfigId = null,
                    liveVoiceProviderId = LiveVoiceProviderId("lv-missing"),
                )

            every { getDefaultModelsUseCase() } returns flowOf(listOf(assignment))
            every { getLiveVoiceProvidersUseCase() } returns flowOf(emptyList())

            val result = useCase(ModelType.LIVE_VOICE)

            assertNull(result)
            io.mockk.verify(exactly = 1) { getLiveVoiceProvidersUseCase() }
        }
}
