package com.browntowndev.enclave.domain.usecase.settings

import com.browntowndev.enclave.domain.model.config.*
import com.browntowndev.enclave.domain.model.inference.ApiProvider
import com.browntowndev.enclave.domain.model.inference.ModelFileFormat
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.usecase.byok.DeleteApiCredentialsUseCase
import com.browntowndev.enclave.domain.usecase.byok.DeleteApiModelConfigurationUseCase
import com.browntowndev.enclave.domain.usecase.byok.GetApiModelAssetsUseCase
import com.browntowndev.enclave.domain.usecase.byok.GetDefaultModelsUseCase
import com.browntowndev.enclave.domain.usecase.modelconfig.DeleteLocalModelUseCase
import com.browntowndev.enclave.domain.usecase.modelconfig.GetLocalModelAssetsUseCase
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PrepareModelDeletionUseCaseTest {
    private val getDefaultModelsUseCase: GetDefaultModelsUseCase = mockk()
    private val getLocalModelAssetsUseCase: GetLocalModelAssetsUseCase = mockk()
    private val getApiModelAssetsUseCase: GetApiModelAssetsUseCase = mockk()
    private val deleteLocalModelUseCase: DeleteLocalModelUseCase = mockk()
    private val deleteApiCredentialsUseCase: DeleteApiCredentialsUseCase = mockk()
    private val deleteApiModelConfigurationUseCase: DeleteApiModelConfigurationUseCase = mockk()

    private lateinit var useCase: PrepareModelDeletionUseCase

    @BeforeEach
    fun setUp() {
        useCase =
            PrepareModelDeletionUseCase(
                getDefaultModelsUseCase,
                getLocalModelAssetsUseCase,
                getApiModelAssetsUseCase,
                deleteLocalModelUseCase,
                deleteApiCredentialsUseCase,
                deleteApiModelConfigurationUseCase,
            )
    }

    @Test
    fun `prepareLocalModelAsset returns BlockedLastModel if it is the last model`() =
        runTest {
            val id = LocalModelId("test-id")
            coEvery { deleteLocalModelUseCase.isLastModel(id) } returns true

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

            coEvery { deleteLocalModelUseCase.isLastModel(id) } returns false
            coEvery { deleteLocalModelUseCase.getModelTypesNeedingReassignment(id) } returns listOf(ModelType.MAIN)

            val localAsset = createLocalModelAsset(id, listOf(configId))
            val otherLocalAsset = createLocalModelAsset(otherId, listOf(otherConfigId))

            coEvery { getLocalModelAssetsUseCase() } returns flowOf(listOf(localAsset, otherLocalAsset))
            coEvery { getApiModelAssetsUseCase() } returns flowOf(emptyList())

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

            coEvery { deleteLocalModelUseCase.isLastModel(id) } returns false
            coEvery { deleteLocalModelUseCase.getModelTypesNeedingReassignment(id) } returns listOf(ModelType.MAIN)

            val localAsset = createLocalModelAsset(id, listOf(configId))
            val otherLocalAsset =
                createLocalModelAsset(
                    otherId,
                    listOf(otherConfigId),
                    displayName = "Qwen 3.5 2B",
                )

            coEvery { getLocalModelAssetsUseCase() } returns flowOf(listOf(localAsset, otherLocalAsset))
            coEvery { getApiModelAssetsUseCase() } returns flowOf(emptyList())

            val result = useCase(ModelDeletionTarget.LocalModelAsset(id))

            assertTrue(result is PreparedModelDeletion.Ready)
            assertEquals("Qwen 3.5 2B", result.reassignmentCandidates[0].assetDisplayName)
        }

    @Test
    fun `prepareApiProvider returns Ready with reassignment if used as default`() =
        runTest {
            val id = ApiCredentialsId("api-id")
            val otherConfigId = LocalModelConfigurationId("other-config-id")

            coEvery { deleteApiCredentialsUseCase.isLastModel(id) } returns false
            coEvery { deleteApiCredentialsUseCase.getModelTypesNeedingReassignment(id) } returns listOf(ModelType.MAIN)

            coEvery { getLocalModelAssetsUseCase() } returns
                flowOf(listOf(createLocalModelAsset(LocalModelId("other"), listOf(otherConfigId))))
            coEvery { getApiModelAssetsUseCase() } returns flowOf(emptyList())

            val result = useCase(ModelDeletionTarget.ApiProvider(id))

            assertTrue(result is PreparedModelDeletion.Ready)
            assertEquals(listOf(ModelType.MAIN), result.modelTypesNeedingReassignment)
            assertEquals(1, result.reassignmentCandidates.size)
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
