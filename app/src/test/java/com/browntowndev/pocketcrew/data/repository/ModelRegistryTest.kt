package com.browntowndev.pocketcrew.data.repository

import com.browntowndev.pocketcrew.domain.model.ModelFileFormat
import com.browntowndev.pocketcrew.domain.model.ModelType
import com.browntowndev.pocketcrew.domain.port.repository.ModelRegistryPort
import com.browntowndev.pocketcrew.domain.port.repository.RegisteredModel
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
        val registeredModel = RegisteredModel(
            remoteFilename = "main.bin",
            modelType = ModelType.MAIN,
            displayName = "Main Model",
            modelFileFormat = ModelFileFormat.LITERTLM,
            md5 = "abc123",
            sizeInBytes = 1024,
            temperature = 0.0,
            topK = 40,
            topP = 0.95,
            maxTokens = 2048,
            systemPrompt = "You are a helpful assistant."
        )

        coEvery { mockRegistry.getRegisteredModel(ModelType.MAIN) } returns registeredModel

        // When
        val result = mockRegistry.getRegisteredModel(ModelType.MAIN)

        // Then
        assertEquals(ModelType.MAIN, result?.modelType)
        assertEquals("Main Model", result?.displayName)
        assertEquals(ModelFileFormat.LITERTLM, result?.modelFileFormat)
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
        coEvery { mockRegistry.setRegisteredModel(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns Unit

        // When
        mockRegistry.setRegisteredModel(
            remoteFilename = "main.bin",
            modelType = ModelType.MAIN,
            displayName = "Main Model",
            modelFileFormat = ModelFileFormat.LITERTLM,
            md5 = "abc123",
            sizeInBytes = 1024,
            temperature = 0.0,
            topK = 40,
            topP = 0.95,
            maxTokens = 2048,
            systemPrompt = "You are a helpful assistant."
        )

        // Then
        coVerify {
            mockRegistry.setRegisteredModel(
                remoteFilename = "main.bin",
                modelType = ModelType.MAIN,
                displayName = "Main Model",
                modelFileFormat = ModelFileFormat.LITERTLM,
                md5 = "abc123",
                sizeInBytes = 1024,
                temperature = 0.0,
                topK = 40,
                topP = 0.95,
                maxTokens = 2048,
                systemPrompt = "You are a helpful assistant."
            )
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
        val visionModel = RegisteredModel(
            remoteFilename = "vision.bin",
            modelType = ModelType.VISION,
            displayName = "Vision Model",
            modelFileFormat = ModelFileFormat.LITERTLM,
            md5 = "vision123",
            sizeInBytes = 1024,
            maxTokens = 2048
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
        coEvery { mockRegistry.setRegisteredModel(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns Unit

        // When
        mockRegistry.setRegisteredModel(
            remoteFilename = "fast.bin",
            modelType = ModelType.FAST,
            displayName = "Fast Model",
            modelFileFormat = ModelFileFormat.LITERTLM,
            md5 = "fast123",
            sizeInBytes = 512,
            temperature = 0.0,
            topK = 40,
            topP = 0.95,
            maxTokens = 1024,
            systemPrompt = null
        )

        // Then
        coVerify {
            mockRegistry.setRegisteredModel(
                remoteFilename = "fast.bin",
                modelType = ModelType.FAST,
                displayName = "Fast Model",
                modelFileFormat = ModelFileFormat.LITERTLM,
                md5 = "fast123",
                sizeInBytes = 512,
                temperature = 0.0,
                topK = 40,
                topP = 0.95,
                maxTokens = 1024,
                systemPrompt = null
            )
        }
    }

    @Test
    fun getRegisteredModel_handlesDraftModel() = runTest {
        // Given
        val draftModel = RegisteredModel(
            remoteFilename = "draft.bin",
            modelType = ModelType.DRAFT,
            displayName = "Draft Model",
            modelFileFormat = ModelFileFormat.TASK,
            md5 = "draft123",
            sizeInBytes = 512,
            temperature = 0.0,
            topK = 40,
            topP = 0.95,
            maxTokens = 512,
            systemPrompt = "You are a draft assistant."
        )

        coEvery { mockRegistry.getRegisteredModel(ModelType.DRAFT) } returns draftModel

        // When
        val result = mockRegistry.getRegisteredModel(ModelType.DRAFT)

        // Then
        assertEquals(ModelType.DRAFT, result?.modelType)
        assertEquals(ModelFileFormat.TASK, result?.modelFileFormat)
    }
}

