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
            displayName = "Main Model",
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
            displayName = "Vision Model",
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

    @Test
    fun getRegisteredAsset_syncVersion_returnsSameAsSuspend() = runTest {
        // Given
        val asset = createMainModelAsset()
        fakeRegistry.setRegisteredModel(ModelType.MAIN, asset)

        // When
        val syncResult = fakeRegistry.getRegisteredAssetSync(ModelType.MAIN)
        val suspendResult = fakeRegistry.getRegisteredAsset(ModelType.MAIN)

        // Then
        assertEquals(syncResult?.metadata?.sha256, suspendResult?.metadata?.sha256)
    }

    @Test
    fun getRegisteredAssetsSync_returnsSameAsSuspend() = runTest {
        // Given
        fakeRegistry.setRegisteredModel(ModelType.MAIN, createMainModelAsset())
        fakeRegistry.setRegisteredModel(ModelType.VISION, createVisionModelAsset())

        // When
        val syncResult = fakeRegistry.getRegisteredAssetsSync()
        val suspendResult = fakeRegistry.getRegisteredAssets()

        // Then
        assertEquals(syncResult.size, suspendResult.size)
    }
}