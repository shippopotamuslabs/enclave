package com.browntowndev.pocketcrew.core.data.remote

import com.browntowndev.pocketcrew.domain.model.config.LocalModelAsset
import com.browntowndev.pocketcrew.domain.model.config.LocalModelConfiguration
import com.browntowndev.pocketcrew.domain.model.config.LocalModelMetadata
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
        val mockAssets = mapOf(
            ModelType.MAIN to LocalModelAsset(
                metadata = LocalModelMetadata(
                    huggingFaceModelName = "model/main",
                    remoteFileName = "main.bin",
                    localFileName = "main.bin",
                    sha256 = "abc123",
                    sizeInBytes = 1000000L,
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
                        systemPrompt = "You are a helpful assistant."
                    )
                )
            )
        )

        coEvery { mockFetcher.fetchRemoteConfig() } returns Result.success(mockAssets)

        // When
        val result = mockFetcher.fetchRemoteConfig()

        // Then
        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrNull()?.size)
        assertTrue(result.getOrNull()?.containsKey(ModelType.MAIN) == true)
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
    fun toLocalModelAssets_convertsRemoteConfigToLocalModelAssets() = runTest {
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

        val expectedModelAssets = mapOf(
            ModelType.MAIN to LocalModelAsset(
                metadata = LocalModelMetadata(
                    huggingFaceModelName = "model/main",
                    remoteFileName = "main.bin",
                    localFileName = "main.bin",
                    sha256 = "abc123",
                    sizeInBytes = 1000000L,
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
                        systemPrompt = "You are a helpful assistant."
                    )
                )
            ),
            ModelType.VISION to LocalModelAsset(
                metadata = LocalModelMetadata(
                    huggingFaceModelName = "model/vision",
                    remoteFileName = "vision.bin",
                    localFileName = "vision.bin",
                    sha256 = "def456",
                    sizeInBytes = 2000000L,
                    modelFileFormat = ModelFileFormat.LITERTLM
                ),
                configurations = listOf(
                    LocalModelConfiguration(
                        localModelId = 0,
                        displayName = "Vision Model",
                        maxTokens = 2048,
                        contextWindow = 2048,
                        temperature = 0.0,
                        topP = 0.95,
                        topK = 40,
                        repetitionPenalty = 1.1,
                        systemPrompt = "You are a vision assistant."
                    )
                )
            )
        )

        coEvery { mockFetcher.toLocalModelAssets(remoteConfigs) } returns expectedModelAssets

        // When
        val modelAssets = mockFetcher.toLocalModelAssets(remoteConfigs)

        // Then
        assertEquals(2, modelAssets.size)
        assertTrue(modelAssets.containsKey(ModelType.MAIN))
        assertTrue(modelAssets.containsKey(ModelType.VISION))
    }

    @Test
    fun fetchRemoteConfig_parsesMultipleModelTypes() = runTest {
        // Given
        val mockAssets = mapOf(
            ModelType.MAIN to LocalModelAsset(
                metadata = LocalModelMetadata(
                    huggingFaceModelName = "model/main",
                    remoteFileName = "main.bin",
                    localFileName = "main.bin",
                    sha256 = "abc123",
                    sizeInBytes = 1000000L,
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
                        systemPrompt = "You are a helpful assistant."
                    )
                )
            ),
            ModelType.VISION to LocalModelAsset(
                metadata = LocalModelMetadata(
                    huggingFaceModelName = "model/vision",
                    remoteFileName = "vision.bin",
                    localFileName = "vision.bin",
                    sha256 = "def456",
                    sizeInBytes = 2000000L,
                    modelFileFormat = ModelFileFormat.LITERTLM
                ),
                configurations = listOf(
                    LocalModelConfiguration(
                        localModelId = 0,
                        displayName = "Vision Model",
                        maxTokens = 2048,
                        contextWindow = 2048,
                        temperature = 0.0,
                        topP = 0.95,
                        topK = 40,
                        repetitionPenalty = 1.1,
                        systemPrompt = "You are a vision assistant."
                    )
                )
            ),
            ModelType.FAST to LocalModelAsset(
                metadata = LocalModelMetadata(
                    huggingFaceModelName = "model/fast",
                    remoteFileName = "fast.bin",
                    localFileName = "fast.bin",
                    sha256 = "ghi789",
                    sizeInBytes = 500000L,
                    modelFileFormat = ModelFileFormat.LITERTLM
                ),
                configurations = listOf(
                    LocalModelConfiguration(
                        localModelId = 0,
                        displayName = "Fast Model",
                        maxTokens = 1024,
                        contextWindow = 2048,
                        temperature = 0.0,
                        topP = 0.95,
                        topK = 40,
                        repetitionPenalty = 1.1,
                        systemPrompt = "You are a fast assistant."
                    )
                )
            ),
            ModelType.DRAFT_ONE to LocalModelAsset(
                metadata = LocalModelMetadata(
                    huggingFaceModelName = "model/draft",
                    remoteFileName = "draft.bin",
                    localFileName = "draft.bin",
                    sha256 = "jkl012",
                    sizeInBytes = 300000L,
                    modelFileFormat = ModelFileFormat.TASK
                ),
                configurations = listOf(
                    LocalModelConfiguration(
                        localModelId = 0,
                        displayName = "Draft Model",
                        maxTokens = 512,
                        contextWindow = 2048,
                        temperature = 0.0,
                        topP = 0.95,
                        topK = 40,
                        repetitionPenalty = 1.1,
                        systemPrompt = "You are a draft assistant."
                    )
                )
            )
        )

        coEvery { mockFetcher.fetchRemoteConfig() } returns Result.success(mockAssets)

        // When
        val result = mockFetcher.fetchRemoteConfig()

        // Then
        assertTrue(result.isSuccess)
        val assets = requireNotNull(result.getOrNull())
        assertEquals(4, assets.size)
        assertTrue(assets.containsKey(ModelType.MAIN))
        assertTrue(assets.containsKey(ModelType.VISION))
        assertTrue(assets.containsKey(ModelType.FAST))
        assertTrue(assets.containsKey(ModelType.DRAFT_ONE))
    }
}
