package com.browntowndev.pocketcrew.domain.usecase.chat

import com.browntowndev.pocketcrew.domain.model.config.LocalModelAsset
import com.browntowndev.pocketcrew.domain.model.config.LocalModelMetadata
import com.browntowndev.pocketcrew.domain.model.inference.ModelFileFormat
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.port.repository.ModelRegistryPort
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Tests for GetModelDisplayNameUseCase.
 */
class GetModelDisplayNameUseCaseTest {

    private lateinit var modelRegistry: ModelRegistryPort
    private lateinit var getModelDisplayNameUseCase: GetModelDisplayNameUseCase

    @BeforeEach
    fun setUp() {
        modelRegistry = mockk()
        getModelDisplayNameUseCase = GetModelDisplayNameUseCase(modelRegistry)
    }

    @Test
    fun getModelDisplayName_returnsDisplayNameForFastMode() = runTest {
        // Given
        val modelMetadata = LocalModelMetadata(
            huggingFaceModelName = "Qwen/Qwen3-8B",
            remoteFileName = "qwen3-8b-q4_k_m.gguf",
            localFileName = "qwen3-8b-q4_k_m.gguf",
            sha256 = "abc123",
            sizeInBytes = 5000000000,
            modelFileFormat = ModelFileFormat.GGUF
        )
        val localModelAsset = LocalModelAsset(
            metadata = modelMetadata,
            configurations = emptyList()
        )
        coEvery { modelRegistry.getRegisteredAsset(ModelType.FAST) } returns localModelAsset

        // When
        val result = getModelDisplayNameUseCase(ModelType.FAST)

        // Then
        assertEquals("Qwen/Qwen3-8B", result)
    }

    @Test
    fun getModelDisplayName_returnsEmptyStringWhenNoModelRegistered() = runTest {
        // Given
        coEvery { modelRegistry.getRegisteredAsset(ModelType.THINKING) } returns null

        // When
        val result = getModelDisplayNameUseCase(ModelType.THINKING)

        // Then
        assertEquals("", result)
    }
}
