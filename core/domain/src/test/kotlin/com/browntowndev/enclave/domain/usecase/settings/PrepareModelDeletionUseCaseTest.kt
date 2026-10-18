package com.browntowndev.enclave.domain.usecase.settings

import com.browntowndev.enclave.core.testing.FakeApiModelRepository
import com.browntowndev.enclave.core.testing.FakeDefaultModelRepository
import com.browntowndev.enclave.core.testing.FakeLocalModelRepository
import com.browntowndev.enclave.core.testing.FakeLoggingPort
import com.browntowndev.enclave.core.testing.FakeMediaProviderRepository
import com.browntowndev.enclave.core.testing.FakeModelConfigFetcher
import com.browntowndev.enclave.core.testing.FakeModelFileScanner
import com.browntowndev.enclave.core.testing.FakeTransactionProvider
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
import com.browntowndev.enclave.domain.model.inference.ApiProvider
import com.browntowndev.enclave.domain.model.inference.ModelFileFormat
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.usecase.byok.DeleteApiCredentialsUseCaseImpl
import com.browntowndev.enclave.domain.usecase.byok.DeleteApiModelConfigurationUseCaseImpl
import com.browntowndev.enclave.domain.usecase.byok.GetApiModelAssetsUseCaseImpl
import com.browntowndev.enclave.domain.usecase.byok.GetDefaultModelsUseCaseImpl
import com.browntowndev.enclave.domain.usecase.download.ResolveModelCatalogUseCase
import com.browntowndev.enclave.domain.usecase.media.GetMediaProvidersUseCase
import com.browntowndev.enclave.domain.usecase.modelconfig.DeleteLocalModelUseCase
import com.browntowndev.enclave.domain.usecase.modelconfig.GetLocalModelAssetsUseCaseImpl
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PrepareModelDeletionUseCaseTest {
    private lateinit var localModelRepository: FakeLocalModelRepository
    private lateinit var apiModelRepository: FakeApiModelRepository
    private lateinit var defaultModelRepository: FakeDefaultModelRepository

    private lateinit var useCase: PrepareModelDeletionUseCase

    @BeforeEach
    fun setUp() {
        localModelRepository = FakeLocalModelRepository()
        apiModelRepository = FakeApiModelRepository()
        defaultModelRepository = FakeDefaultModelRepository()
        val transactionProvider = FakeTransactionProvider()
        useCase =
            PrepareModelDeletionUseCase(
                GetDefaultModelsUseCaseImpl(defaultModelRepository),
                GetLocalModelAssetsUseCaseImpl(localModelRepository),
                GetApiModelAssetsUseCaseImpl(apiModelRepository),
                DeleteLocalModelUseCase(
                    localModelRepository,
                    defaultModelRepository,
                    FakeModelFileScanner(),
                    apiModelRepository,
                    FakeLoggingPort(),
                ),
                DeleteApiCredentialsUseCaseImpl(
                    apiModelRepository,
                    localModelRepository,
                    defaultModelRepository,
                    transactionProvider,
                ),
                DeleteApiModelConfigurationUseCaseImpl(
                    apiModelRepository,
                    defaultModelRepository,
                    transactionProvider,
                ),
                ResolveModelCatalogUseCase(FakeModelConfigFetcher()),
                GetMediaProvidersUseCase(FakeMediaProviderRepository()),
            )
    }

    @Test
    fun `prepareLocalModelAsset returns BlockedLastModel if it is the last model`() =
        runTest {
            val id = LocalModelId("test-id")
            // The only registered model is the deletion target and no API credentials
            // exist, so the real DeleteLocalModelUseCase reports it as the last model.
            localModelRepository.setAssets(
                listOf(createLocalModelAsset(id, listOf(LocalModelConfigurationId("config-id")))),
            )

            val result = useCase(ModelDeletionTarget.LocalModelAsset(id))

            assertEquals(PreparedModelDeletion.BlockedLastModel, result)
        }

    @Test
    fun `prepareLocalModelAsset returns Ready with reassignment if used as default`() =
        runTest {
            val id = LocalModelId("test-id")
            val configId = LocalModelConfigurationId("config-id")
            val otherId = LocalModelId("other-id")
            val otherConfigId = LocalModelConfigurationId("other-config-id")

            val localAsset = createLocalModelAsset(id, listOf(configId))
            val otherLocalAsset = createLocalModelAsset(otherId, listOf(otherConfigId))

            localModelRepository.setAssets(listOf(localAsset, otherLocalAsset))
            localModelRepository.seedAssetForConfig(configId, localAsset)
            // MAIN currently points at the target model's config, so it needs reassignment.
            defaultModelRepository.seed(
                DefaultModelAssignment(modelType = ModelType.MAIN, localConfigId = configId),
            )

            val result = useCase(ModelDeletionTarget.LocalModelAsset(id))

            assertTrue(result is PreparedModelDeletion.Ready)
            assertEquals(listOf(ModelType.MAIN), result.modelTypesNeedingReassignment)
            assertEquals(1, result.reassignmentCandidates.size)
            assertEquals(otherConfigId, result.reassignmentCandidates[0].configId)
        }

    @Test
    fun `SCN-DELETION-001 prepare model deletion reassignment uses asset displayName`() =
        runTest {
            val id = LocalModelId("test-id")
            val configId = LocalModelConfigurationId("config-id")
            val otherId = LocalModelId("other-id")
            val otherConfigId = LocalModelConfigurationId("other-config-id")

            val localAsset = createLocalModelAsset(id, listOf(configId))
            val otherLocalAsset =
                createLocalModelAsset(
                    otherId,
                    listOf(otherConfigId),
                    displayName = "Qwen 3.5 2B",
                )

            localModelRepository.setAssets(listOf(localAsset, otherLocalAsset))
            localModelRepository.seedAssetForConfig(configId, localAsset)
            defaultModelRepository.seed(
                DefaultModelAssignment(modelType = ModelType.MAIN, localConfigId = configId),
            )

            val result = useCase(ModelDeletionTarget.LocalModelAsset(id))

            assertTrue(result is PreparedModelDeletion.Ready)
            assertEquals("Qwen 3.5 2B", result.reassignmentCandidates[0].assetDisplayName)
        }

    @Test
    fun `prepareApiProvider returns Ready with reassignment if used as default`() =
        runTest {
            val id = ApiCredentialsId("api-id")
            val apiConfigId = ApiModelConfigurationId("api-config-id")
            val otherConfigId = LocalModelConfigurationId("other-config-id")

            // A local model exists, so the API provider is not the last model; one of
            // its configurations is the MAIN default, so MAIN needs reassignment.
            localModelRepository.setAssets(
                listOf(createLocalModelAsset(LocalModelId("other"), listOf(otherConfigId))),
            )
            apiModelRepository.seedCredentials(
                listOf(
                    ApiCredentials(
                        id = id,
                        displayName = "API",
                        provider = ApiProvider.OPENAI,
                        credentialAlias = "alias",
                    ),
                ),
            )
            apiModelRepository.seedConfigurations(
                listOf(
                    ApiModelConfiguration(
                        id = apiConfigId,
                        apiCredentialsId = id,
                        modelId = "gpt-4o",
                        displayName = "API cfg",
                    ),
                ),
            )
            defaultModelRepository.seed(
                DefaultModelAssignment(modelType = ModelType.MAIN, apiConfigId = apiConfigId),
            )

            val result = useCase(ModelDeletionTarget.ApiProvider(id))

            assertTrue(result is PreparedModelDeletion.Ready)
            assertEquals(listOf(ModelType.MAIN), result.modelTypesNeedingReassignment)
            assertEquals(1, result.reassignmentCandidates.size)
        }

    @Test
    fun `SCN-USECASE-DELETION-001 prepareApiPreset filters reassignment to multimodal presets when vision slot needs reassignment`() =
        runTest {
            val deletedConfigId = ApiModelConfigurationId("deleted")
            val multimodalConfigId = ApiModelConfigurationId("vision")
            val textOnlyConfigId = ApiModelConfigurationId("text")
            val credentialsId = ApiCredentialsId("cred")

            val apiAsset =
                ApiModelAsset(
                    credentials =
                        ApiCredentials(
                            id = credentialsId,
                            displayName = "OpenAI",
                            provider = ApiProvider.OPENAI,
                            credentialAlias = "alias",
                        ),
                    configurations =
                        listOf(
                            ApiModelConfiguration(
                                id = deletedConfigId,
                                apiCredentialsId = credentialsId,
                                modelId = "gpt-4o",
                                isMultimodal = true,
                                displayName = "Deleted",
                            ),
                            ApiModelConfiguration(
                                id = multimodalConfigId,
                                apiCredentialsId = credentialsId,
                                modelId = "gpt-4o-mini",
                                isMultimodal = true,
                                displayName = "Vision OK",
                            ),
                            ApiModelConfiguration(
                                id = textOnlyConfigId,
                                apiCredentialsId = credentialsId,
                                modelId = "gpt-3.5",
                                isMultimodal = false,
                                displayName = "Text only",
                            ),
                        ),
                )

            apiModelRepository.seedCredentials(listOf(apiAsset.credentials))
            apiModelRepository.seedConfigurations(apiAsset.configurations)
            // The VISION default points at the preset being deleted.
            defaultModelRepository.seed(
                DefaultModelAssignment(modelType = ModelType.VISION, apiConfigId = deletedConfigId),
            )

            val result = useCase(ModelDeletionTarget.ApiPreset(deletedConfigId))

            assertTrue(result is PreparedModelDeletion.Ready)
            assertEquals(listOf(ModelType.VISION), result.modelTypesNeedingReassignment)
            assertEquals(1, result.reassignmentCandidates.size)
            assertEquals(multimodalConfigId, result.reassignmentCandidates.single().configId)
        }

    private fun createLocalModelAsset(
        id: LocalModelId,
        configIds: List<LocalModelConfigurationId>,
        displayName: String = "name",
    ): LocalModelAsset =
        LocalModelAsset(
            metadata =
                LocalModelMetadata(
                    id = id,
                    displayName = displayName,
                    huggingFaceModelName = "name",
                    remoteFileName = "remote",
                    localFileName = "local",
                    sha256 = "sha",
                    sizeInBytes = 100,
                    modelFileFormat = ModelFileFormat.GGUF,
                ),
            configurations =
                configIds.map { configId ->
                    LocalModelConfiguration(
                        id = configId,
                        localModelId = id,
                        displayName = "config-name",
                        maxTokens = 100,
                        contextWindow = 100,
                        temperature = 1.0,
                        topP = 1.0,
                        topK = 1,
                        repetitionPenalty = 1.0,
                        systemPrompt = "prompt",
                    )
                },
        )
}
