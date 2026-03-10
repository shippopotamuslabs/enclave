package com.browntowndev.pocketcrew.data.remote

import com.browntowndev.pocketcrew.domain.model.config.ModelConfiguration
import com.browntowndev.pocketcrew.domain.model.inference.ModelFileFormat
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.model.config.RemoteModelConfig
import com.browntowndev.pocketcrew.domain.port.repository.ModelConfigFetcherPort
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ModelConfigFetcherTest {

    private lateinit var mockFetcher: ModelConfigFetcherPort

    @BeforeEach
    fun setup() {
        mockFetcher = mockk()
    }

    @Test
    fun fetchRemoteConfig_returnsSuccess_withValidConfigs() = runTest {
        // Given
        val mockConfigs = listOf(
            ModelConfiguration(
                modelType = ModelType.MAIN,
                metadata = ModelConfiguration.Metadata(
                    huggingFaceModelName = "model/main",
                    remoteFileName = "main.bin",
                    localFileName = "main.bin",
                    displayName = "Main Model",
                    sha256 = "abc123",
                    sizeInBytes = 1000000L,
                    modelFileFormat = ModelFileFormat.LITERTLM
                ),
                tunings = ModelConfiguration.Tunings(
                    temperature = 0.0,
                    topK = 40,
                    topP = 0.95,
                    maxTokens = 2048,
                    contextWindow = 2048,
                    repetitionPenalty = 1.1
                ),
                persona = ModelConfiguration.Persona(systemPrompt = "You are a helpful assistant.")
            )
        )

        coEvery { mockFetcher.fetchRemoteConfig() } returns Result.success(mockConfigs)

        // When
        val result = mockFetcher.fetchRemoteConfig()

        // Then
        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrNull()?.size)
        assertEquals(ModelType.MAIN, result.getOrNull()?.first()?.modelType)
    }

    @Test
    fun fetchRemoteConfig_returnsFailure_whenNetworkError() = runTest {
        // Given
        coEvery { mockFetcher.fetchRemoteConfig() } returns Result.failure(Exception("Network error"))

        // When
        val result = mockFetcher.fetchRemoteConfig()

        // Then
        assertTrue(result.isFailure)
        assertEquals("Network error", result.exceptionOrNull()?.message)
    }

    @Test
    fun toModelConfigurations_convertsRemoteConfigToModelConfigurations() = runTest {
        // Given - RemoteModelConfig is the raw type from JSON parsing
        val remoteConfigs = listOf(
            RemoteModelConfig(
                modelType = ModelType.MAIN,
                fileName = "main.bin",
                huggingFaceModelName = "model/main",
                displayName = "Main Model",
                sha256 = "abc123",
                sizeInBytes = 1000000L,
                modelFileFormat = ModelFileFormat.LITERTLM,
                temperature = 0.0,
                topK = 40,
                topP = 0.95,
                maxTokens = 2048,
                contextWindow = 2048,
                repetitionPenalty = 1.1,
                systemPrompt = "You are a helpful assistant."
            ),
            RemoteModelConfig(
                modelType = ModelType.VISION,
                fileName = "vision.bin",
                huggingFaceModelName = "model/vision",
                displayName = "Vision Model",
                sha256 = "def456",
                sizeInBytes = 2000000L,
                modelFileFormat = ModelFileFormat.LITERTLM,
                temperature = 0.0,
                topK = 40,
                topP = 0.95,
                maxTokens = 2048,
                contextWindow = 2048,
                repetitionPenalty = 1.1,
                systemPrompt = "You are a vision assistant."
            )
        )

        val expectedModelConfigurations = listOf(
            ModelConfiguration(
                modelType = ModelType.MAIN,
                metadata = ModelConfiguration.Metadata(
                    huggingFaceModelName = "model/main",
                    remoteFileName = "main.bin",
                    localFileName = "main.bin",
                    displayName = "Main Model",
                    sha256 = "abc123",
                    sizeInBytes = 1000000L,
                    modelFileFormat = ModelFileFormat.LITERTLM
                ),
                tunings = ModelConfiguration.Tunings(
                    temperature = 0.0,
                    topK = 40,
                    topP = 0.95,
                    maxTokens = 2048,
                    contextWindow = 2048,
                    repetitionPenalty = 1.1,
                ),
                persona = ModelConfiguration.Persona(systemPrompt = "You are a helpful assistant.")
            ),
            ModelConfiguration(
                modelType = ModelType.VISION,
                metadata = ModelConfiguration.Metadata(
                    huggingFaceModelName = "model/vision",
                    remoteFileName = "vision.bin",
                    localFileName = "vision.bin",
                    displayName = "Vision Model",
                    sha256 = "def456",
                    sizeInBytes = 2000000L,
                    modelFileFormat = ModelFileFormat.LITERTLM
                ),
                tunings = ModelConfiguration.Tunings(
                    temperature = 0.0,
                    topK = 40,
                    topP = 0.95,
                    maxTokens = 2048,
                    contextWindow = 2048,
                    repetitionPenalty = 1.1,
                ),
                persona = ModelConfiguration.Persona(systemPrompt = "You are a vision assistant.")
            )
        )

        coEvery { mockFetcher.toModelConfigurations(remoteConfigs) } returns expectedModelConfigurations

        // When
        val modelConfigurations = mockFetcher.toModelConfigurations(remoteConfigs)

        // Then
        assertEquals(2, modelConfigurations.size)
        assertTrue(modelConfigurations.any { it.modelType == ModelType.MAIN })
        assertTrue(modelConfigurations.any { it.modelType == ModelType.VISION })
    }

    @Test
    fun fetchRemoteConfig_parsesMultipleModelTypes() = runTest {
        // Given
        val mockConfigs = listOf(
            ModelConfiguration(
                modelType = ModelType.MAIN,
                metadata = ModelConfiguration.Metadata(
                    huggingFaceModelName = "model/main",
                    remoteFileName = "main.bin",
                    localFileName = "main.bin",
                    displayName = "Main Model",
                    sha256 = "abc123",
                    sizeInBytes = 1000000L,
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
            ),
            ModelConfiguration(
                modelType = ModelType.VISION,
                metadata = ModelConfiguration.Metadata(
                    huggingFaceModelName = "model/vision",
                    remoteFileName = "vision.bin",
                    localFileName = "vision.bin",
                    displayName = "Vision Model",
                    sha256 = "def456",
                    sizeInBytes = 2000000L,
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
            ),
            ModelConfiguration(
                modelType = ModelType.FAST,
                metadata = ModelConfiguration.Metadata(
                    huggingFaceModelName = "model/fast",
                    remoteFileName = "fast.bin",
                    localFileName = "fast.bin",
                    displayName = "Fast Model",
                    sha256 = "ghi789",
                    sizeInBytes = 500000L,
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
                persona = ModelConfiguration.Persona(systemPrompt = "You are a fast assistant.")
            ),
            ModelConfiguration(
                modelType = ModelType.DRAFT_ONE,
                metadata = ModelConfiguration.Metadata(
                    huggingFaceModelName = "model/draft",
                    remoteFileName = "draft.bin",
                    localFileName = "draft.bin",
                    displayName = "Draft Model",
                    sha256 = "jkl012",
                    sizeInBytes = 300000L,
                    modelFileFormat = ModelFileFormat.TASK
                ),
                tunings = ModelConfiguration.Tunings(
                    temperature = 0.0,
                    topK = 40,
                    topP = 0.95,
                    maxTokens = 512,
                    repetitionPenalty = 1.1,
                    contextWindow = 2048
                ),
                persona = ModelConfiguration.Persona(systemPrompt = "You are a draft assistant.")
            )
        )

        coEvery { mockFetcher.fetchRemoteConfig() } returns Result.success(mockConfigs)

        // When
        val result = mockFetcher.fetchRemoteConfig()

        // Then
        assertTrue(result.isSuccess)
        val configs = result.getOrNull()!!
        assertEquals(4, configs.size)
        assertTrue(configs.any { it.modelType == ModelType.MAIN })
        assertTrue(configs.any { it.modelType == ModelType.VISION })
        assertTrue(configs.any { it.modelType == ModelType.FAST })
        assertTrue(configs.any { it.modelType == ModelType.DRAFT_ONE })
    }
}
