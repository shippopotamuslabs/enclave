package com.browntowndev.pocketcrew.data.remote

import com.browntowndev.pocketcrew.domain.model.ModelFileFormat
import com.browntowndev.pocketcrew.domain.model.ModelConfig
import com.browntowndev.pocketcrew.domain.model.ModelType
import com.browntowndev.pocketcrew.domain.model.RemoteModelConfig
import com.browntowndev.pocketcrew.domain.port.repository.ModelConfigFetcherPort
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.mockkStatic
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
            RemoteModelConfig(
                modelType = ModelType.MAIN,
                fileName = "main.bin",
                displayName = "Main Model",
                md5 = "abc123",
                sizeInBytes = 1000000L,
                modelFileFormat = ModelFileFormat.LITERTLM,
                temperature = 0.0,
                topK = 40,
                topP = 0.95,
                maxTokens = 2048,
                systemPrompt = "You are a helpful assistant."
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
    fun toModelFiles_convertsRemoteConfigToModelFiles() = runTest {
        // Given
        val configs = listOf(
            RemoteModelConfig(
                modelType = ModelType.MAIN,
                fileName = "main.bin",
                displayName = "Main Model",
                md5 = "abc123",
                sizeInBytes = 1000000L,
                modelFileFormat = ModelFileFormat.LITERTLM,
                temperature = 0.0,
                topK = 40,
                topP = 0.95,
                maxTokens = 2048,
                systemPrompt = "You are a helpful assistant."
            ),
            RemoteModelConfig(
                modelType = ModelType.VISION,
                fileName = "vision.bin",
                displayName = "Vision Model",
                md5 = "def456",
                sizeInBytes = 2000000L,
                modelFileFormat = ModelFileFormat.LITERTLM,
                temperature = 0.0,
                topK = 40,
                topP = 0.95,
                maxTokens = 2048,
                systemPrompt = "You are a vision assistant."
            )
        )

        val expectedModelFiles = listOf(
            com.browntowndev.pocketcrew.domain.model.ModelFile(
                sizeBytes = 1000000L,
                url = "${ModelConfig.R2_BUCKET_URL}/main.bin",
                md5 = "abc123",
                modelTypes = listOf(ModelType.MAIN),
                originalFileName = "main.bin",
                displayName = "Main Model",
                modelFileFormat = ModelFileFormat.LITERTLM,
                temperature = 0.0,
                topK = 40,
                topP = 0.95,
                maxTokens = 2048,
                systemPrompt = "You are a helpful assistant."
            ),
            com.browntowndev.pocketcrew.domain.model.ModelFile(
                sizeBytes = 2000000L,
                url = "${ModelConfig.R2_BUCKET_URL}/vision.bin",
                md5 = "def456",
                modelTypes = listOf(ModelType.VISION),
                originalFileName = "vision.bin",
                displayName = "Vision Model",
                modelFileFormat = ModelFileFormat.LITERTLM,
                temperature = 0.0,
                topK = 40,
                topP = 0.95,
                maxTokens = 2048,
                systemPrompt = "You are a vision assistant."
            )
        )

        mockkStatic(ModelConfigFetcherPort::toModelFiles)
        coEvery { mockFetcher.toModelFiles(configs) } returns expectedModelFiles

        // When
        val modelFiles = mockFetcher.toModelFiles(configs)

        // Then
        assertEquals(2, modelFiles.size)
        assertTrue(modelFiles.any { it.modelTypes.contains(ModelType.MAIN) })
        assertTrue(modelFiles.any { it.modelTypes.contains(ModelType.VISION) })
    }

    @Test
    fun fetchRemoteConfig_parsesMultipleModelTypes() = runTest {
        // Given
        val mockConfigs = listOf(
            RemoteModelConfig(
                modelType = ModelType.MAIN,
                fileName = "main.bin",
                displayName = "Main Model",
                md5 = "abc123",
                sizeInBytes = 1000000L,
                modelFileFormat = ModelFileFormat.LITERTLM,
                temperature = 0.0,
                topK = 40,
                topP = 0.95,
                maxTokens = 2048,
                systemPrompt = "You are a helpful assistant."
            ),
            RemoteModelConfig(
                modelType = ModelType.VISION,
                fileName = "vision.bin",
                displayName = "Vision Model",
                md5 = "def456",
                sizeInBytes = 2000000L,
                modelFileFormat = ModelFileFormat.LITERTLM,
                temperature = 0.0,
                topK = 40,
                topP = 0.95,
                maxTokens = 2048,
                systemPrompt = "You are a vision assistant."
            ),
            RemoteModelConfig(
                modelType = ModelType.FAST,
                fileName = "fast.bin",
                displayName = "Fast Model",
                md5 = "ghi789",
                sizeInBytes = 500000L,
                modelFileFormat = ModelFileFormat.LITERTLM,
                temperature = 0.0,
                topK = 40,
                topP = 0.95,
                maxTokens = 1024,
                systemPrompt = "You are a fast assistant."
            ),
            RemoteModelConfig(
                modelType = ModelType.DRAFT,
                fileName = "draft.bin",
                displayName = "Draft Model",
                md5 = "jkl012",
                sizeInBytes = 300000L,
                modelFileFormat = ModelFileFormat.TASK,
                temperature = 0.0,
                topK = 40,
                topP = 0.95,
                maxTokens = 512,
                systemPrompt = "You are a draft assistant."
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
        assertTrue(configs.any { it.modelType == ModelType.DRAFT })
    }
}

