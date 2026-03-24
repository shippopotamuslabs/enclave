package com.browntowndev.pocketcrew.core.data.repository

import com.browntowndev.pocketcrew.core.testing.FakeModelRegistry
import com.browntowndev.pocketcrew.domain.model.config.ModelConfiguration
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

    private fun createMainModelConfig() = ModelConfiguration(
        modelType = ModelType.MAIN,
        metadata = ModelConfiguration.Metadata(
            huggingFaceModelName = "model/main",
            remoteFileName = "main.bin",
            localFileName = "main.bin",
            displayName = "Main Model",
            sha256 = "abc123",
            sizeInBytes = 1024,
            modelFileFormat = ModelFileFormat.LITERTLM
        ),
        tunings = ModelConfiguration.Tunings(
            temperature = 0.0,
            topK = 40,
            topP = 0.95,
            maxTokens = 2048,
            repetitionPenalty = 1.1,
            contextWindow = 2048
        ),
        persona = ModelConfiguration.Persona(systemPrompt = "You are a helpful assistant.")
    )

    private fun createVisionModelConfig() = ModelConfiguration(
        modelType = ModelType.VISION,
        metadata = ModelConfiguration.Metadata(
            huggingFaceModelName = "model/vision",
            remoteFileName = "vision.bin",
            localFileName = "vision.bin",
            displayName = "Vision Model",
            sha256 = "vision123",
            sizeInBytes = 1024,
            modelFileFormat = ModelFileFormat.LITERTLM
        ),
        tunings = ModelConfiguration.Tunings(
            temperature = 0.0,
            topK = 40,
            topP = 0.95,
            maxTokens = 2048,
            repetitionPenalty = 1.1,
            contextWindow = 2048
        ),
        persona = ModelConfiguration.Persona(systemPrompt = "You are a vision assistant.")
    )

    private fun createFastModelConfig() = ModelConfiguration(
        modelType = ModelType.FAST,
        metadata = ModelConfiguration.Metadata(
            huggingFaceModelName = "model/fast",
            remoteFileName = "fast.bin",
            localFileName = "fast.bin",
            displayName = "Fast Model",
            sha256 = "fast123",
            sizeInBytes = 512,
            modelFileFormat = ModelFileFormat.LITERTLM
        ),
        tunings = ModelConfiguration.Tunings(
            temperature = 0.0,
            topK = 40,
            topP = 0.95,
            maxTokens = 1024,
            repetitionPenalty = 1.1,
            contextWindow = 2048
        ),
        persona = ModelConfiguration.Persona(systemPrompt = "")
    )

    @Test
    fun getRegisteredModel_returnsNull_whenNotRegistered() = runTest {
        // When - querying for a model that was never registered
        val result = fakeRegistry.getRegisteredModel(ModelType.MAIN)

        // Then - should return null
        assertNull(result)
    }

    @Test
    fun setRegisteredModel_persistsModel_andCanBeRetrieved() = runTest {
        // Given
        val mainConfig = createMainModelConfig()

        // When
        fakeRegistry.setRegisteredModel(mainConfig)

        // Then - verify the model can be retrieved with correct properties
        val retrieved = fakeRegistry.getRegisteredModel(ModelType.MAIN)
        assertEquals(ModelType.MAIN, retrieved?.modelType)
        assertEquals("Main Model", retrieved?.metadata?.displayName)
        assertEquals(ModelFileFormat.LITERTLM, retrieved?.metadata?.modelFileFormat)
    }

    @Test
    fun observeRegisteredModels_emitsUpdatedMap_whenModelsAdded() = runTest {
        // Given - start with empty registry
        val initialFlow = fakeRegistry.observeRegisteredModels().first()
        assertEquals(0, initialFlow.size)

        // When - register models
        fakeRegistry.setRegisteredModel(createMainModelConfig())
        fakeRegistry.setRegisteredModel(createVisionModelConfig())

        // Then - verify the flow emits updated map
        val updatedFlow = fakeRegistry.observeRegisteredModels().first()
        assertEquals(2, updatedFlow.size)
        assertEquals("Main Model", updatedFlow[ModelType.MAIN])
        assertEquals("Vision Model", updatedFlow[ModelType.VISION])
    }

    @Test
    fun clearAll_removesAllModels() = runTest {
        // Given - register multiple models
        fakeRegistry.setRegisteredModel(createMainModelConfig())
        fakeRegistry.setRegisteredModel(createVisionModelConfig())

        // When
        fakeRegistry.clearAll()

        // Then - verify registry is empty
        assertEquals(0, fakeRegistry.getRegisteredModels().size)
        assertNull(fakeRegistry.getRegisteredModel(ModelType.MAIN))
        assertNull(fakeRegistry.getRegisteredModel(ModelType.VISION))
    }

    @Test
    fun setRegisteredModel_withMarkExistingAsOld_marksPreviousAsOld() = runTest {
        // Given - register initial MAIN model
        val initialMain = createMainModelConfig()
        fakeRegistry.setRegisteredModel(initialMain)

        // When - register new MAIN model with markExistingAsOld
        val updatedMain = createMainModelConfig().copy(
            metadata = createMainModelConfig().metadata.copy(
                displayName = "Updated Main Model",
                sha256 = "new123"
            )
        )
        fakeRegistry.setRegisteredModel(updatedMain, markExistingAsOld = true)

        // Then - verify new model is registered and status is tracked
        val retrieved = fakeRegistry.getRegisteredModel(ModelType.MAIN)
        assertEquals("Updated Main Model", retrieved?.metadata?.displayName)
        assertEquals("new123", retrieved?.metadata?.sha256)
    }

    @Test
    fun getRegisteredModels_returnsAllRegisteredModels() = runTest {
        // Given
        fakeRegistry.setRegisteredModel(createMainModelConfig())
        fakeRegistry.setRegisteredModel(createVisionModelConfig())
        fakeRegistry.setRegisteredModel(createFastModelConfig())

        // When
        val allModels = fakeRegistry.getRegisteredModels()

        // Then
        assertEquals(3, allModels.size)
        assertEquals(
            setOf(ModelType.MAIN, ModelType.VISION, ModelType.FAST),
            allModels.map { it.modelType }.toSet()
        )
    }

    @Test
    fun getRegisteredModel_syncVersion_returnsSameAsSuspend() = runTest {
        // Given
        fakeRegistry.setRegisteredModel(createMainModelConfig())

        // When - both sync and suspend versions
        val syncResult = fakeRegistry.getRegisteredModelSync(ModelType.MAIN)
        val suspendResult = fakeRegistry.getRegisteredModel(ModelType.MAIN)

        // Then - both should return the same model
        assertEquals(syncResult?.metadata?.displayName, suspendResult?.metadata?.displayName)
    }

    @Test
    fun getRegisteredModelsSync_returnsSameAsSuspend() = runTest {
        // Given
        fakeRegistry.setRegisteredModel(createMainModelConfig())
        fakeRegistry.setRegisteredModel(createVisionModelConfig())

        // When
        val syncResult = fakeRegistry.getRegisteredModelsSync()
        val suspendResult = fakeRegistry.getRegisteredModels()

        // Then
        assertEquals(syncResult.size, suspendResult.size)
    }
}
