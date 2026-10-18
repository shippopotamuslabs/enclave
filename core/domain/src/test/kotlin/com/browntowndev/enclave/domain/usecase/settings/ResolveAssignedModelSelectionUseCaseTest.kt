package com.browntowndev.enclave.domain.usecase.settings

import com.browntowndev.enclave.core.testing.FakeApiModelRepository
import com.browntowndev.enclave.core.testing.FakeDefaultModelRepository
import com.browntowndev.enclave.core.testing.FakeLiveVoiceProviderRepository
import com.browntowndev.enclave.core.testing.FakeLocalModelRepository
import com.browntowndev.enclave.core.testing.FakeTtsProviderRepository
import com.browntowndev.enclave.domain.model.config.ApiCredentials
import com.browntowndev.enclave.domain.model.config.ApiCredentialsId
import com.browntowndev.enclave.domain.model.config.ApiModelAsset
import com.browntowndev.enclave.domain.model.config.ApiModelConfiguration
import com.browntowndev.enclave.domain.model.config.ApiModelConfigurationId
import com.browntowndev.enclave.domain.model.config.DefaultModelAssignment
import com.browntowndev.enclave.domain.model.config.LiveVoiceProviderAsset
import com.browntowndev.enclave.domain.model.config.LiveVoiceProviderId
import com.browntowndev.enclave.domain.model.config.LocalModelAsset
import com.browntowndev.enclave.domain.model.config.LocalModelConfiguration
import com.browntowndev.enclave.domain.model.config.LocalModelConfigurationId
import com.browntowndev.enclave.domain.model.config.LocalModelId
import com.browntowndev.enclave.domain.model.config.LocalModelMetadata
import com.browntowndev.enclave.domain.model.config.TtsProviderAsset
import com.browntowndev.enclave.domain.model.config.TtsProviderId
import com.browntowndev.enclave.domain.model.inference.ApiProvider
import com.browntowndev.enclave.domain.model.inference.ModelFileFormat
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.usecase.byok.GetApiModelAssetsUseCaseImpl
import com.browntowndev.enclave.domain.usecase.byok.GetDefaultModelsUseCaseImpl
import com.browntowndev.enclave.domain.usecase.modelconfig.GetLocalModelAssetsUseCaseImpl
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class ResolveAssignedModelSelectionUseCaseTest {
    private fun buildUseCase(
        defaultModelRepository: FakeDefaultModelRepository = FakeDefaultModelRepository(),
        localModelRepository: FakeLocalModelRepository = FakeLocalModelRepository(),
        apiModelRepository: FakeApiModelRepository = FakeApiModelRepository(),
        ttsProviderRepository: FakeTtsProviderRepository = FakeTtsProviderRepository(),
        liveVoiceProviderRepository: FakeLiveVoiceProviderRepository = FakeLiveVoiceProviderRepository(),
    ): ResolveAssignedModelSelectionUseCase =
        ResolveAssignedModelSelectionUseCase(
            GetDefaultModelsUseCaseImpl(defaultModelRepository),
            GetLocalModelAssetsUseCaseImpl(localModelRepository),
            GetApiModelAssetsUseCaseImpl(apiModelRepository),
            GetTtsProvidersUseCase(ttsProviderRepository),
            GetLiveVoiceProvidersUseCase(liveVoiceProviderRepository),
        )

    @Test
    fun `returns null if assignment not found for model type`() =
        runTest {
            val useCase = buildUseCase()

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
            val defaultModelRepository = FakeDefaultModelRepository()
            defaultModelRepository.seed(assignment)
            val useCase = buildUseCase(defaultModelRepository = defaultModelRepository)

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
                )
            val localAsset =
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
                    configurations = listOf(localConfig),
                )

            val defaultModelRepository = FakeDefaultModelRepository()
            defaultModelRepository.seed(assignment)
            val localModelRepository = FakeLocalModelRepository()
            localModelRepository.setAssets(listOf(localAsset))
            val useCase =
                buildUseCase(
                    defaultModelRepository = defaultModelRepository,
                    localModelRepository = localModelRepository,
                )

            val result = useCase(ModelType.MAIN)

            assertEquals(ResolvedAssignedModelSelection(localAsset = localAsset, localConfig = localConfig), result)
        }

    @Test
    fun `returns api asset and config if apiConfigId matches an existing asset`() =
        runTest {
            val apiConfigId = ApiModelConfigurationId("api-config-1")
            val credentialsId = ApiCredentialsId("api-creds-1")
            val assignment =
                DefaultModelAssignment(
                    modelType = ModelType.MAIN,
                    localConfigId = null,
                    apiConfigId = apiConfigId,
                )
            val apiConfig =
                ApiModelConfiguration(
                    id = apiConfigId,
                    apiCredentialsId = credentialsId,
                    modelId = "gpt-4o",
                    displayName = "API cfg",
                )
            val apiAsset =
                ApiModelAsset(
                    credentials =
                        ApiCredentials(
                            id = credentialsId,
                            displayName = "API",
                            provider = ApiProvider.OPENAI,
                            credentialAlias = "alias",
                        ),
                    configurations = listOf(apiConfig),
                )

            val defaultModelRepository = FakeDefaultModelRepository()
            defaultModelRepository.seed(assignment)
            val apiModelRepository = FakeApiModelRepository()
            apiModelRepository.seedCredentials(listOf(apiAsset.credentials))
            apiModelRepository.seedConfigurations(listOf(apiConfig))
            val useCase =
                buildUseCase(
                    defaultModelRepository = defaultModelRepository,
                    apiModelRepository = apiModelRepository,
                )

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

            val defaultModelRepository = FakeDefaultModelRepository()
            defaultModelRepository.seed(assignment)
            val useCase =
                buildUseCase(
                    defaultModelRepository = defaultModelRepository,
                    ttsProviderRepository = FakeTtsProviderRepository(listOf(ttsAsset)),
                )

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

            val defaultModelRepository = FakeDefaultModelRepository()
            defaultModelRepository.seed(assignment)
            val useCase =
                buildUseCase(
                    defaultModelRepository = defaultModelRepository,
                    liveVoiceProviderRepository = FakeLiveVoiceProviderRepository(listOf(liveVoiceAsset)),
                )

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

            val defaultModelRepository = FakeDefaultModelRepository()
            defaultModelRepository.seed(assignment)
            val liveVoiceProviderRepository = FakeLiveVoiceProviderRepository()
            val useCase =
                buildUseCase(
                    defaultModelRepository = defaultModelRepository,
                    liveVoiceProviderRepository = liveVoiceProviderRepository,
                )

            val result = useCase(ModelType.LIVE_VOICE)

            assertNull(result)
            assertEquals(1, liveVoiceProviderRepository.getLiveVoiceProvidersCallCount)
        }
}
