package com.browntowndev.pocketcrew.data.repository

import com.browntowndev.pocketcrew.domain.model.ModelConfiguration
import com.browntowndev.pocketcrew.domain.model.ModelFileFormat
import com.browntowndev.pocketcrew.domain.model.ModelType
import com.browntowndev.pocketcrew.domain.port.repository.ModelRegistryPort
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ModelRegistryTest {

    private lateinit var mockRegistry: ModelRegistryPort

    @BeforeEach
    fun setup() {
        mockRegistry = mockk()
    }

    @Test
    fun getRegisteredModel_returnsModel_whenRegistered() = runTest {
        // Given
        val registeredModel = ModelConfiguration(
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
                contextWindow = 2048
            ),
            persona = ModelConfiguration.Persona(systemPrompt = "You are a helpful assistant.")
        )

        coEvery { mockRegistry.getRegisteredModel(ModelType.MAIN) } returns registeredModel

        // When
        val result = mockRegistry.getRegisteredModel(ModelType.MAIN)

        // Then
        assertEquals(ModelType.MAIN, result?.modelType)
        assertEquals("Main Model", result?.metadata?.displayName)
        assertEquals(ModelFileFormat.LITERTLM, result?.metadata?.modelFileFormat)
    }

    @Test
    fun getRegisteredModel_returnsNull_whenNotRegistered() = runTest {
        // Given
        coEvery { mockRegistry.getRegisteredModel(ModelType.MAIN) } returns null

        // When
        val result = mockRegistry.getRegisteredModel(ModelType.MAIN)

        // Then
        assertNull(result)
    }

    @Test
    fun setRegisteredModel_callsRepository() = runTest {
        // Given
        coEvery { mockRegistry.setRegisteredModel(any()) } returns Unit

        val config = ModelConfiguration(
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
                contextWindow = 2048
            ),
            persona = ModelConfiguration.Persona(systemPrompt = "You are a helpful assistant.")
        )

        // When
        mockRegistry.setRegisteredModel(config)

        // Then
        coVerify {
            mockRegistry.setRegisteredModel(config)
        }
    }

    @Test
    fun observeRegisteredModels_emitsModels() = runTest {
        // Given
        val modelsMap = mapOf(
            ModelType.MAIN to "main.litertlm",
            ModelType.VISION to "vision.litertlm"
        )

        coEvery { mockRegistry.observeRegisteredModels() } returns flowOf(modelsMap)

        // When
        val result = mockRegistry.observeRegisteredModels()

        // Then - collect from the flow to get the actual value
        val collectedValue = result.toList()
        assertEquals(listOf(modelsMap), collectedValue)
    }

    @Test
    fun clearAll_clearsRegistry() = runTest {
        // Given
        coEvery { mockRegistry.clearAll() } returns Unit

        // When
        mockRegistry.clearAll()

        // Then
        coVerify { mockRegistry.clearAll() }
    }

    @Test
    fun getRegisteredModel_handlesAllModelTypes() = runTest {
        // Given - test VISION
        val visionModel = ModelConfiguration(
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
                contextWindow = 2048
            ),
            persona = ModelConfiguration.Persona(systemPrompt = "You are a vision assistant.")
        )

        coEvery { mockRegistry.getRegisteredModel(ModelType.VISION) } returns visionModel

        // When
        val result = mockRegistry.getRegisteredModel(ModelType.VISION)

        // Then
        assertEquals(ModelType.VISION, result?.modelType)
    }

    @Test
    fun setRegisteredModel_handlesAllModelTypes() = runTest {
        // Given - test FAST
        coEvery { mockRegistry.setRegisteredModel(any()) } returns Unit

        val config = ModelConfiguration(
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
                contextWindow = 2048
            ),
            persona = ModelConfiguration.Persona(systemPrompt = "")
        )

        // When
        mockRegistry.setRegisteredModel(config)

        // Then
        coVerify {
            mockRegistry.setRegisteredModel(config)
        }
    }

    @Test
    fun getRegisteredModel_handlesDraftModel() = runTest {
        // Given
        val draftModel = ModelConfiguration(
            modelType = ModelType.DRAFT_ONE,
            metadata = ModelConfiguration.Metadata(
                huggingFaceModelName = "model/draft",
                remoteFileName = "draft.bin",
                localFileName = "draft.bin",
                displayName = "Draft Model",
                sha256 = "draft123",
                sizeInBytes = 512,
                modelFileFormat = ModelFileFormat.TASK
            ),
            tunings = ModelConfiguration.Tunings(
                temperature = 0.0,
                topK = 40,
                topP = 0.95,
                maxTokens = 512,
                contextWindow = 2048
            ),
            persona = ModelConfiguration.Persona(systemPrompt = "You are a draft assistant.")
        )

        coEvery { mockRegistry.getRegisteredModel(ModelType.DRAFT_ONE) } returns draftModel

        // When
        val result = mockRegistry.getRegisteredModel(ModelType.DRAFT_ONE)

        // Then
        assertEquals(ModelType.DRAFT_ONE, result?.modelType)
        assertEquals(ModelFileFormat.TASK, result?.metadata?.modelFileFormat)
    }
}
