package com.browntowndev.pocketcrew.core.data.repository

import com.browntowndev.pocketcrew.core.testing.FakeModelRegistry
import com.browntowndev.pocketcrew.domain.model.config.LocalModelAsset
import com.browntowndev.pocketcrew.domain.model.config.LocalModelConfiguration
import com.browntowndev.pocketcrew.domain.model.config.LocalModelMetadata
import com.browntowndev.pocketcrew.domain.model.config.ModelStatus
import com.browntowndev.pocketcrew.domain.model.inference.ModelFileFormat
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Tests for ModelRegistry repository using FakeModelRegistry.
 * These tests verify actual behavioral scenarios, not mock interactions.
 */
class ModelRegistryTest {

    private lateinit var fakeRegistry: FakeModelRegistry

    @BeforeEach
    fun setup() {
        fakeRegistry = FakeModelRegistry()
    }

    private fun createMainModelAsset() = LocalModelAsset(
        metadata = LocalModelMetadata(
            huggingFaceModelName = "model/main",
            remoteFileName = "main.bin",
            localFileName = "main.bin",
            sha256 = "abc123",
            sizeInBytes = 1024,
            modelFileFormat = ModelFileFormat.LITERTLM
        ),
        configurations = listOf(
            LocalModelConfiguration(
                localModelId = 0,
                displayName = "Main Model",
                maxTokens = 2048,
                contextWindow = 2048,
                temperature = 0.0,
                topP = 0.95,
                topK = 40,
                repetitionPenalty = 1.1,
                systemPrompt = ""
            )
        )
    )

    private fun createVisionModelAsset() = LocalModelAsset(
        metadata = LocalModelMetadata(
            huggingFaceModelName = "model/vision",
            remoteFileName = "vision.bin",
            localFileName = "vision.bin",
            sha256 = "def456",
            sizeInBytes = 2048,
            modelFileFormat = ModelFileFormat.LITERTLM
        ),
        configurations = listOf(
            LocalModelConfiguration(
                localModelId = 0,
                displayName = "Vision Model",
                maxTokens = 4096,
                contextWindow = 4096,
                temperature = 0.7,
                topP = 0.95,
                topK = 40,
                repetitionPenalty = 1.1,
                systemPrompt = ""
            )
        )
    )

    @Test
    fun getRegisteredAsset_returnsCorrectAsset_whenRegistered() = runTest {
        // Given
        val asset = createMainModelAsset()
        fakeRegistry.setRegisteredModel(ModelType.MAIN, asset)

        // When
        val retrieved = fakeRegistry.getRegisteredAsset(ModelType.MAIN)

        // Then
        assertEquals(asset.metadata.sha256, retrieved?.metadata?.sha256)
    }

    @Test
    fun getRegisteredAsset_returnsNull_whenNotRegistered() = runTest {
        // When
        val result = fakeRegistry.getRegisteredAsset(ModelType.MAIN)

        // Then
        assertNull(result)
    }

    @Test
    fun setRegisteredModel_marksExistingAsOld_whenRequested() = runTest {
        // Given
        val oldAsset = createMainModelAsset()
        fakeRegistry.setRegisteredModel(ModelType.MAIN, oldAsset)

        // When
        val newAsset = createMainModelAsset().copy(
            metadata = oldAsset.metadata.copy(sha256 = "new_sha")
        )
        fakeRegistry.setRegisteredModel(
            modelType = ModelType.MAIN,
            asset = newAsset,
            status = ModelStatus.CURRENT,
            markExistingAsOld = true
        )

        // Then
        assertEquals(ModelStatus.OLD, fakeRegistry.getStatusBySha256(oldAsset.metadata.sha256))
        assertEquals(ModelStatus.CURRENT, fakeRegistry.getStatusBySha256(newAsset.metadata.sha256))
    }

    @Test
    fun clearAll_removesAllRegisteredModels() = runTest {
        // Given
        fakeRegistry.setRegisteredModel(ModelType.MAIN, createMainModelAsset())
        fakeRegistry.setRegisteredModel(ModelType.VISION, createVisionModelAsset())

        // When
        fakeRegistry.clearAll()

        // Then
        assertEquals(0, fakeRegistry.getRegisteredAssets().size)
        assertNull(fakeRegistry.getRegisteredAsset(ModelType.MAIN))
        assertNull(fakeRegistry.getRegisteredAsset(ModelType.VISION))
    }

    @Test
    fun getRegisteredAssets_returnsAllRegisteredAssets() = runTest {
        // Given
        fakeRegistry.setRegisteredModel(ModelType.MAIN, createMainModelAsset())
        fakeRegistry.setRegisteredModel(ModelType.VISION, createVisionModelAsset())

        // When
        val allAssets = fakeRegistry.getRegisteredAssets()

        // Then
        assertEquals(2, allAssets.size)
    }

    // ============================================================
    // SOFT-DELETE SCENARIOS (TDD Red)
    // ============================================================

    /**
     * Risk #3: Creating new LocalModelEntity on re-download instead of reusing
     * Defense: reuseModelForRedownload() reuses the same LocalModelEntity ID
     *
     * TDD Red: This test FAILS against current implementation because
     * reuseModelForRedownload is NotImplementedError in FakeModelRegistry
     * until the feature is implemented.
     */
    @Test
    fun `reuseModelForRedownload reuses same LocalModelEntity ID`() = runTest {
        // Given: a soft-deleted model exists (model with no configs, stored in softDeletedModels)
        val modelId = 42L
        val softDeletedAsset = createMainModelAsset().copy(configurations = emptyList())
        // Manually add to soft-deleted (simulates soft-delete state)
        fakeRegistry.registerSoftDeletedModel(modelId, softDeletedAsset)

        // Given: new remote asset with matching sha256
        val newAsset = createMainModelAsset().copy(
            configurations = listOf(
                LocalModelConfiguration(
                    localModelId = modelId,
                    displayName = "Main Model",
                    maxTokens = 2048,
                    contextWindow = 2048,
                    temperature = 0.0,
                    topP = 0.95,
                    topK = 40,
                    repetitionPenalty = 1.1,
                    systemPrompt = "",
                    isSystemPreset = true
                )
            )
        )

        // When
        val reusedId = fakeRegistry.reuseModelForRedownload(modelId, newAsset)

        // Then: same model ID is returned
        assertEquals(modelId, reusedId)
    }

    /**
     * Risk #4: InitializeModelsUseCase re-downloads soft-deleted models
     * Defense: getSoftDeletedModels() returns only models with no configs
     */
    @Test
    fun `getSoftDeletedModels returns only soft-deleted models with zero configs`() = runTest {
        // Given: an active model with configs
        fakeRegistry.setRegisteredModel(ModelType.FAST, createMainModelAsset())

        // Given: a soft-deleted model (no configs)
        val softDeletedId = 99L
        fakeRegistry.registerSoftDeletedModel(softDeletedId, createMainModelAsset().copy(configurations = emptyList()))

        // When
        val softDeleted = fakeRegistry.getSoftDeletedModels()

        // Then: only the soft-deleted model is returned
        assertEquals(1, softDeleted.size)
        assertEquals(softDeletedId, softDeleted.first().metadata.id)
    }

    /**
     * getSoftDeletedModels returns empty when no models are soft-deleted
     */
    @Test
    fun `getSoftDeletedModels returns empty when no soft-deleted models exist`() = runTest {
        // Given: only active models
        fakeRegistry.setRegisteredModel(ModelType.FAST, createMainModelAsset())
        fakeRegistry.setRegisteredModel(ModelType.VISION, createVisionModelAsset())

        // When
        val softDeleted = fakeRegistry.getSoftDeletedModels()

        // Then: empty
        assertEquals(0, softDeleted.size)
    }
}