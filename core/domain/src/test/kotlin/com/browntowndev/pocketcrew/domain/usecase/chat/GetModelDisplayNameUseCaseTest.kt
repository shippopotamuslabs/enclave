package com.browntowndev.pocketcrew.domain.usecase.chat

import com.browntowndev.pocketcrew.domain.model.config.ModelConfiguration
import com.browntowndev.pocketcrew.domain.model.inference.ModelFileFormat
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.port.repository.ModelRegistryPort
import io.mockk.every
import io.mockk.mockk
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
    fun getModelDisplayName_returnsDisplayNameForFastMode() {
        // Given
        val modelMetadata = ModelConfiguration.Metadata(
            huggingFaceModelName = "Qwen/Qwen3-8B",
            remoteFileName = "qwen3-8b-q4_k_m.gguf",
            localFileName = "qwen3-8b-q4_k_m.gguf",
            displayName = "Qwen 3 8B",
            sha256 = "abc123",
            sizeInBytes = 5000000000,
            modelFileFormat = ModelFileFormat.GGUF
        )
        val modelConfiguration = ModelConfiguration(
            modelType = ModelType.FAST,
            metadata = modelMetadata,
            tunings = ModelConfiguration.Tunings(
                temperature = 0.7,
                topK = 40,
                topP = 0.9,
                repetitionPenalty = 1.1,
                maxTokens = 2048,
                contextWindow = 32768
            ),
            persona = ModelConfiguration.Persona(
                systemPrompt = "You are a helpful assistant."
            )
        )
        every { modelRegistry.getRegisteredModelSync(ModelType.FAST) } returns modelConfiguration

        // When
        val result = getModelDisplayNameUseCase(ModelType.FAST)

        // Then
        assertEquals("Qwen 3 8B", result)
    }

    @Test
    fun getModelDisplayName_returnsEmptyStringWhenNoModelRegistered() {
        // Given
        every { modelRegistry.getRegisteredModelSync(ModelType.THINKING) } returns null

        // When
        val result = getModelDisplayNameUseCase(ModelType.THINKING)

        // Then
        assertEquals("", result)
    }
}
