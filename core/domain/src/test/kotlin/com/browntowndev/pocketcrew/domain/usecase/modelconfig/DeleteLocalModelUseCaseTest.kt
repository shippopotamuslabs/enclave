package com.browntowndev.pocketcrew.domain.usecase.modelconfig

import com.browntowndev.pocketcrew.domain.model.config.ApiCredentials
import com.browntowndev.pocketcrew.domain.model.config.DefaultModelAssignment
import com.browntowndev.pocketcrew.domain.model.config.LocalModelAsset
import com.browntowndev.pocketcrew.domain.model.config.LocalModelConfiguration
import com.browntowndev.pocketcrew.domain.model.config.LocalModelMetadata
import com.browntowndev.pocketcrew.domain.model.inference.ApiProvider
import com.browntowndev.pocketcrew.domain.model.inference.ModelFileFormat
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.port.download.ModelFileScannerPort
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.repository.ApiModelRepositoryPort
import com.browntowndev.pocketcrew.domain.port.repository.DefaultModelRepositoryPort
import com.browntowndev.pocketcrew.domain.port.repository.LocalModelConfigurationsRepositoryPort
import com.browntowndev.pocketcrew.domain.port.repository.ModelRegistryPort
import com.browntowndev.pocketcrew.testing.createFakeLocalModelAsset
import com.browntowndev.pocketcrew.testing.createFakeLocalModelConfiguration
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DeleteLocalModelUseCaseTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var mockModelRegistry: ModelRegistryPort
    private lateinit var mockDefaultModelRepository: DefaultModelRepositoryPort
    private lateinit var mockModelFileScanner: ModelFileScannerPort
    private lateinit var mockLocalModelConfigurationsRepository: LocalModelConfigurationsRepositoryPort
    private lateinit var mockApiModelRepository: ApiModelRepositoryPort
    private lateinit var mockLoggingPort: LoggingPort

    private lateinit var useCase: DeleteLocalModelUseCase

    @BeforeEach
    fun setup() {
        MockKAnnotations.init(this)

        Dispatchers.setMain(testDispatcher)

        mockModelRegistry = mockk(relaxed = true)
        mockDefaultModelRepository = mockk(relaxed = true)
        mockModelFileScanner = mockk(relaxed = true)
        mockLocalModelConfigurationsRepository = mockk(relaxed = true)
        mockApiModelRepository = mockk(relaxed = true)
        mockLoggingPort = mockk(relaxed = true)

        useCase = DeleteLocalModelUseCase(
            modelRegistry = mockModelRegistry,
            defaultModelRepository = mockDefaultModelRepository,
            modelFileScanner = mockModelFileScanner,
            localModelConfigurationsRepository = mockLocalModelConfigurationsRepository,
            apiModelRepository = mockApiModelRepository,
            loggingPort = mockLoggingPort
        )
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
        Dispatchers.resetMain()
    }

    private fun createActiveLocalModel(id: Long, sha256: String = "sha$id") = createFakeLocalModelAsset(
        id = id,
        sha256 = sha256,
        configurations = listOf(createFakeLocalModelConfiguration(id = id, localModelId = id))
    )

    // ============================================================
    // HAPPY PATH SCENARIOS
    // ============================================================

    /**
     * Scenario: Delete model that is NOT a default (no reassignment needed)
     * Given: LocalModelEntity(id=42) with LocalModelConfigurationEntity(id=99) and isSystemPreset=true
     * And: NO DefaultModelEntity points to config 99
     * When: User deletes model 42
     * Then: Physical file is deleted
     * And: Config 99 is hard-deleted
     * And: LocalModelEntity(42) is preserved (soft-delete)
     * And: Model appears in "Available for Download"
     */
    @Test
    fun `delete model NOT a default - no reassignment needed`() = runTest {
        // Given
        val modelId = 42L

        // Mock: config 99 is NOT a default (no DefaultModelEntity points to it)
        coEvery { mockDefaultModelRepository.getDefault(ModelType.FAST) } returns null
        coEvery { mockDefaultModelRepository.getDefault(ModelType.MAIN) } returns null
        coEvery { mockDefaultModelRepository.getDefault(ModelType.DRAFT_ONE) } returns null
        coEvery { mockDefaultModelRepository.getDefault(ModelType.VISION) } returns null

        // When
        val result = useCase(modelId)

        // Then
        assertTrue(result.isSuccess)
        // Verify file deletion was called
        coVerify { mockModelFileScanner.deleteModelFile(modelId) }
        // Verify config was hard-deleted
        coVerify { mockLocalModelConfigurationsRepository.deleteAllForAsset(modelId) }
        // Verify LocalModelEntity was preserved (NOT hard-deleted)
        coVerify(exactly = 0) { mockModelRegistry.deleteLocalModelMetadata(modelId) }
    }

    /**
     * Scenario: Delete model that IS a default (reassignment required)
     * Given: LocalModelEntity(id=42) with configs 99 (isSystemPreset=true) and 100 (isSystemPreset=false)
     * And: DefaultModelEntity(FAST, localConfigId=99) exists
     * When: User initiates delete on model 42
     * Then: ReassignDefaultModelDialog is shown with configs from OTHER models AND API configs
     * And: User selects replacementConfigId=77 (from a DIFFERENT model)
     * Then: DefaultModelEntity(FAST) is UPDATED to point to config 77
     * And: Physical file for model 42 is deleted
     * And: Configs 99 and 100 are ALL hard-deleted
     * And: LocalModelEntity(42) is preserved (soft-delete)
     */
    @Test
    fun `delete model IS a default - reassigns to another local config`() = runTest {
        // Given
        val modelId = 42L
        val oldConfigId = 99L
        val replacementConfigId = 77L

        // Mock: FAST is pointing to config 99 on model 42
        coEvery { mockDefaultModelRepository.setDefault(ModelType.FAST, replacementConfigId, null) } returns Unit
        coEvery { mockDefaultModelRepository.observeDefaults() } returns flowOf(
            listOf(DefaultModelAssignment(ModelType.FAST, localConfigId = oldConfigId, apiConfigId = null))
        )
        coEvery { mockLocalModelConfigurationsRepository.getAllForAsset(modelId) } returns listOf(
            createFakeLocalModelConfiguration(id = oldConfigId, localModelId = modelId)
        )

        // When: user reassigns to config 77 from a DIFFERENT model
        val result = useCase(modelId, replacementLocalConfigId = replacementConfigId)

        // Then
        assertTrue(result.isSuccess)
        // DefaultModelEntity(FAST) should be updated to point to replacement config
        coVerify { mockDefaultModelRepository.setDefault(ModelType.FAST, replacementConfigId, null) }
        // File deleted
        coVerify { mockModelFileScanner.deleteModelFile(modelId) }
        // ALL configs hard-deleted
        coVerify { mockLocalModelConfigurationsRepository.deleteAllForAsset(modelId) }
        // LocalModelEntity preserved
        coVerify(exactly = 0) { mockModelRegistry.deleteLocalModelMetadata(modelId) }
    }

    /**
     * Scenario: Reassignment to API model is valid
     * Given: LocalModelEntity(id=42) has config 99 as default for FAST
     * And: ApiModelConfigurationEntity(id=200) exists for an API provider
     * And: User has no other local models
     * When: User reassigns FAST to API config 200
     * Then: DefaultModelEntity(FAST) is UPDATED with apiConfigId=200
     * And: DefaultModelEntity(FAST).localConfigId is null
     */
    @Test
    fun `delete model - reassign to API config`() = runTest {
        // Given
        val modelId = 42L
        val apiConfigId = 200L
        val configId = 99L

        coEvery { mockDefaultModelRepository.setDefault(ModelType.FAST, null, apiConfigId) } returns Unit
        coEvery { mockDefaultModelRepository.observeDefaults() } returns flowOf(
            listOf(DefaultModelAssignment(ModelType.FAST, localConfigId = configId, apiConfigId = null))
        )
        coEvery { mockLocalModelConfigurationsRepository.getAllForAsset(modelId) } returns listOf(
            createFakeLocalModelConfiguration(id = configId, localModelId = modelId)
        )

        // When
        val result = useCase(modelId, replacementApiConfigId = apiConfigId)

        // Then
        assertTrue(result.isSuccess)
        coVerify { mockDefaultModelRepository.setDefault(ModelType.FAST, null, apiConfigId) }
    }

    // ============================================================
    // isLastModel UNIT TESTS
    // ============================================================

    /**
     * Unit test: isLastModel returns true when only one local model exists and no API models
     */
    @Test
    fun `isLastModel returns true when only one local model and no API models`() = runTest {
        // Given: exactly one local model, zero API models
        coEvery { mockModelRegistry.getRegisteredAssets() } returns listOf(
            createActiveLocalModel(id = 42L)
        )
        coEvery { mockApiModelRepository.getAllCredentials() } returns emptyList()

        // When/Then
        assertTrue(useCase.isLastModel(42L))
    }

    /**
     * Unit test: isLastModel returns false when multiple local models exist
     */
    @Test
    fun `isLastModel returns false when multiple local models exist`() = runTest {
        // Given: two local models, zero API models
        coEvery { mockModelRegistry.getRegisteredAssets() } returns listOf(
            createActiveLocalModel(id = 42L),
            createActiveLocalModel(id = 43L)
        )
        coEvery { mockApiModelRepository.getAllCredentials() } returns emptyList()

        // When/Then
        assertFalse(useCase.isLastModel(42L))
    }

    /**
     * Unit test: isLastModel returns false when API models exist even with one local model
     */
    @Test
    fun `isLastModel returns false when API models exist even with one local model`() = runTest {
        // Given: one local model, one API model
        coEvery { mockModelRegistry.getRegisteredAssets() } returns listOf(
            createActiveLocalModel(id = 42L)
        )
        coEvery { mockApiModelRepository.getAllCredentials() } returns listOf(
            ApiCredentials(id = 1L, displayName = "OpenAI", provider = ApiProvider.OPENAI, modelId = "gpt-4", credentialAlias = "my-key")
        )

        // When/Then
        assertFalse(useCase.isLastModel(42L))
    }

    /**
     * Unit test: isLastModel returns false when no local models (API-only)
     */
    @Test
    fun `isLastModel returns false when no local models exist`() = runTest {
        // Given: zero local models
        coEvery { mockModelRegistry.getRegisteredAssets() } returns emptyList()
        coEvery { mockApiModelRepository.getAllCredentials() } returns emptyList()

        // When/Then
        assertFalse(useCase.isLastModel(999L))
    }

    // ============================================================
    // ERROR PATH & EDGE CASE SCENARIOS
    // ============================================================

    /**
     * Scenario: Cannot delete last model when only one exists
     * Given: One LocalModelEntity exists with one config
     * And: Zero API models configured
     * When: User attempts to delete the model
     * Then: showCannotDeleteLastModelAlert = true
     * And: AlertDialog shown: "You must have at least one local or API model..."
     * And: No deletion occurs
     */
    @Test
    fun `cannot delete last model - only one local model exists`() = runTest {
        // Given: one local model, no API models (isLastModel = true)
        val modelId = 42L
        coEvery { mockModelRegistry.getRegisteredAssets() } returns listOf(
            createActiveLocalModel(id = modelId)
        )
        coEvery { mockApiModelRepository.getAllCredentials() } returns emptyList()

        // When: user attempts delete
        val result = useCase(modelId)

        // Then: should fail with IllegalStateException
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
        // No deletion should occur
        coVerify(exactly = 0) { mockModelFileScanner.deleteModelFile(any()) }
    }

    /**
     * Scenario: Reassignment must pick config from DIFFERENT model (or API model)
     * Given: User is deleting model 42 with config 99 as default for FAST
     * When: ReassignDefaultModelDialog is shown
     * Then: Only configs from OTHER models are shown as options
     * And: Configs from model 42 are NOT shown
     * And: API model configurations are shown as options
     */
    @Test
    fun `getModelTypesNeedingReassignment returns model types that need reassignment`() = runTest {
        // Given: model 42 has config 99 which is the default for FAST
        val modelId = 42L

        coEvery { mockDefaultModelRepository.observeDefaults() } returns flowOf(
            listOf(DefaultModelAssignment(ModelType.FAST, localConfigId = 99L, apiConfigId = null))
        )
        coEvery { mockLocalModelConfigurationsRepository.getAllForAsset(modelId) } returns listOf(
            createFakeLocalModelConfiguration(id = 99L, localModelId = modelId),
            createFakeLocalModelConfiguration(id = 100L, localModelId = modelId)
        )

        // When
        val needingReassignment = useCase.getModelTypesNeedingReassignment(modelId)

        // Then: FAST should need reassignment (config 99 is a default)
        assertTrue(needingReassignment.contains(ModelType.FAST))
    }

    /**
     * Negative case: config that is NOT pointed to by any DefaultModelEntity should NOT need reassignment
     */
    @Test
    fun `getModelTypesNeedingReassignment excludes configs not pointed to by any default`() = runTest {
        // Given: model 42 has configs 99 and 100, but only config 99 is a default
        val modelId = 42L

        coEvery { mockDefaultModelRepository.observeDefaults() } returns flowOf(
            listOf(DefaultModelAssignment(ModelType.FAST, localConfigId = 99L, apiConfigId = null))
            // Note: config 100 is NOT a default
        )
        coEvery { mockLocalModelConfigurationsRepository.getAllForAsset(modelId) } returns listOf(
            createFakeLocalModelConfiguration(id = 99L, localModelId = modelId),
            createFakeLocalModelConfiguration(id = 100L, localModelId = modelId)
        )

        // When
        val needingReassignment = useCase.getModelTypesNeedingReassignment(modelId)

        // Then: only FAST should need reassignment
        // MAIN, DRAFT_ONE, VISION should NOT need reassignment
        assertTrue(needingReassignment.size == 1)
        assertTrue(needingReassignment.contains(ModelType.FAST))
        assertFalse(needingReassignment.contains(ModelType.MAIN))
        assertFalse(needingReassignment.contains(ModelType.DRAFT_ONE))
        assertFalse(needingReassignment.contains(ModelType.VISION))
    }

    /**
     * Scenario: Multiple configs — reassignment blocks deletion of all
     * Given: LocalModelEntity(id=42) with configs 99, 100, 101
     * And: DefaultModelEntity(FAST, localConfigId=99) exists
     * When: User deletes model 42 after reassigning to config 77 from model 99
     * Then: Configs 99, 100, 101 are ALL hard-deleted
     * And: LocalModelEntity(42) is preserved
     */
    @Test
    fun `delete with reassignment - all configs are hard-deleted`() = runTest {
        // Given
        val modelId = 42L
        val replacementConfigId = 77L

        coEvery { mockDefaultModelRepository.setDefault(ModelType.FAST, replacementConfigId, null) } returns Unit
        coEvery { mockDefaultModelRepository.getDefault(ModelType.FAST) } returns DefaultModelAssignment(
            modelType = ModelType.FAST,
            localConfigId = replacementConfigId,
            apiConfigId = null
        )

        // When
        val result = useCase(modelId, replacementLocalConfigId = replacementConfigId)

        // Then
        assertTrue(result.isSuccess)
        // All configs deleted for this model (including the ones that weren't defaults)
        coVerify { mockLocalModelConfigurationsRepository.deleteAllForAsset(modelId) }
    }

    // ============================================================
    // MUTATION DEFENSE
    // ============================================================

    /**
     * Risk #1: Hard-deleting LocalModelEntity instead of soft-deleting
     * Defense: LocalModelEntity is preserved after delete
     */
    @Test
    fun `mutation defense - LocalModelEntity is preserved (soft-delete) not hard-deleted`() = runTest {
        // Given
        val modelId = 42L

        // When
        useCase(modelId)

        // Then: deleteLocalModelMetadata should NOT be called (hard delete)
        coVerify(exactly = 0) { mockModelRegistry.deleteLocalModelMetadata(modelId) }
    }

    /**
     * Risk #2: Not hard-deleting all configs
     * Defense: All configs for the model are deleted
     */
    @Test
    fun `mutation defense - all configs are hard-deleted`() = runTest {
        // Given
        val modelId = 42L

        // When
        useCase(modelId)

        // Then: deleteAllForAsset should be called (hard delete all configs)
        coVerify { mockLocalModelConfigurationsRepository.deleteAllForAsset(modelId) }
    }

    /**
     * Risk #5: Reassignment updates wrong DefaultModelEntity
     * Defense: Only the DefaultModelEntity pointing to the deleted model's config is updated
     */
    @Test
    fun `mutation defense - reassignment updates correct DefaultModelEntity`() = runTest {
        // Given: model 42 has config 99 as default for FAST, model 43 has config 77 as default for MAIN
        val modelId = 42L
        val replacementConfigId = 77L

        coEvery { mockDefaultModelRepository.setDefault(ModelType.FAST, replacementConfigId, null) } returns Unit
        coEvery { mockDefaultModelRepository.observeDefaults() } returns flowOf(
            listOf(
                DefaultModelAssignment(ModelType.FAST, localConfigId = 99L, apiConfigId = null),
                DefaultModelAssignment(ModelType.MAIN, localConfigId = 77L, apiConfigId = null)
            )
        )
        coEvery { mockLocalModelConfigurationsRepository.getAllForAsset(modelId) } returns listOf(
            createFakeLocalModelConfiguration(id = 99L, localModelId = modelId)
        )

        // When
        useCase(modelId, replacementLocalConfigId = replacementConfigId)

        // Then: only FAST should be updated (the one whose config is being deleted)
        coVerify { mockDefaultModelRepository.setDefault(ModelType.FAST, replacementConfigId, null) }
        // MAIN should NOT be updated (it's unrelated)
        coVerify(exactly = 0) { mockDefaultModelRepository.setDefault(ModelType.MAIN, any(), any()) }
    }
}
