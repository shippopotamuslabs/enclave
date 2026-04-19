package com.browntowndev.pocketcrew.core.data.remote

import com.browntowndev.pocketcrew.domain.model.config.LocalModelAsset
import com.browntowndev.pocketcrew.domain.model.config.LocalModelConfiguration
import com.browntowndev.pocketcrew.domain.model.config.LocalModelConfigurationId
import com.browntowndev.pocketcrew.domain.model.config.LocalModelId
import com.browntowndev.pocketcrew.domain.model.config.LocalModelMetadata
import com.browntowndev.pocketcrew.domain.model.config.RemoteModelAsset
import com.browntowndev.pocketcrew.domain.model.config.RemoteModelConfiguration
import com.browntowndev.pocketcrew.domain.model.inference.ModelFileFormat
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
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
        val mockAssets = listOf(
            LocalModelAsset(
                metadata = LocalModelMetadata(
                    id = LocalModelId("0"),
                    huggingFaceModelName = "model/main",
                    remoteFileName = "main.bin",
                    localFileName = "main.bin",
                    sha256 = "abc123",
                    sizeInBytes = 1000000L,
                    modelFileFormat = ModelFileFormat.LITERTLM
                ),
                configurations = listOf(
                    LocalModelConfiguration(
                        id = LocalModelConfigurationId("config-main-1"),
                        localModelId = LocalModelId("0"),
                        displayName = "Main Model",
                        maxTokens = 2048,
                        contextWindow = 2048,
                        temperature = 0.0,
                        topP = 0.95,
                        topK = 40,
                        repetitionPenalty = 1.1,
                        systemPrompt = "You are a helpful assistant.",
                        defaultAssignments = listOf(ModelType.MAIN)
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
    fun toLocalModelAssets_convertsRemoteAssetsToLocalModelAssets() = runTest {
        // Given
        val remoteAssets = listOf(
            RemoteModelAsset(
                huggingFaceModelName = "model/main",
                fileName = "main.bin",
                sha256 = "abc123",
                sizeInBytes = 1000000L,
                modelFileFormat = ModelFileFormat.LITERTLM,
                configurations = listOf(
                    RemoteModelConfiguration(
                        configId = LocalModelConfigurationId("config-main-1"),
                        displayName = "Main Model",
                        systemPrompt = "You are a helpful assistant.",
                        temperature = 0.0,
                        topK = 40,
                        topP = 0.95,
                        minP = 0.05,
                        repetitionPenalty = 1.1,
                        maxTokens = 2048,
                        contextWindow = 2048,
                        defaultAssignments = listOf(ModelType.MAIN)
                    )
                )
            ),
            RemoteModelAsset(
                huggingFaceModelName = "model/vision",
                fileName = "vision.bin",
                sha256 = "def456",
                sizeInBytes = 2000000L,
                modelFileFormat = ModelFileFormat.LITERTLM,
                isMultimodal = true,
                configurations = listOf(
                    RemoteModelConfiguration(
                        configId = LocalModelConfigurationId("config-vision-1"),
                        displayName = "Vision Model",
                        systemPrompt = "You are a vision assistant.",
                        temperature = 0.0,
                        topK = 40,
                        topP = 0.95,
                        minP = 0.05,
                        repetitionPenalty = 1.1,
                        maxTokens = 2048,
                        contextWindow = 2048,
                        defaultAssignments = listOf(ModelType.FAST)
                    )
                )
            )
        )

        val expectedModelAssets = listOf(
            LocalModelAsset(
                metadata = LocalModelMetadata(
                    id = LocalModelId(""),
                    huggingFaceModelName = "model/main",
                    remoteFileName = "main.bin",
                    localFileName = "main.bin",
                    sha256 = "abc123",
                    sizeInBytes = 1000000L,
                    modelFileFormat = ModelFileFormat.LITERTLM
                ),
                configurations = listOf(
                    LocalModelConfiguration(
                        id = LocalModelConfigurationId("config-main-1"),
                        localModelId = LocalModelId(""),
                        displayName = "Main Model",
                        maxTokens = 2048,
                        contextWindow = 2048,
                        temperature = 0.0,
                        topP = 0.95,
                        topK = 40,
                        minP = 0.05,
                        repetitionPenalty = 1.1,
                        systemPrompt = "You are a helpful assistant.",
                        defaultAssignments = listOf(ModelType.MAIN)
                    )
                )
            ),
            LocalModelAsset(
                metadata = LocalModelMetadata(
                    id = LocalModelId(""),
                    huggingFaceModelName = "model/vision",
                    remoteFileName = "vision.bin",
                    localFileName = "vision.bin",
                    sha256 = "def456",
                    sizeInBytes = 2000000L,
                    modelFileFormat = ModelFileFormat.LITERTLM,
                    isMultimodal = true
                ),
                configurations = listOf(
                    LocalModelConfiguration(
                        id = LocalModelConfigurationId("config-vision-1"),
                        localModelId = LocalModelId(""),
                        displayName = "Vision Model",
                        maxTokens = 2048,
                        contextWindow = 2048,
                        temperature = 0.0,
                        topP = 0.95,
                        topK = 40,
                        minP = 0.05,
                        repetitionPenalty = 1.1,
                        systemPrompt = "You are a vision assistant.",
                        defaultAssignments = listOf(ModelType.FAST)
                    )
                )
            )
        )

        coEvery { mockFetcher.toLocalModelAssets(remoteAssets) } returns expectedModelAssets

        // When
        val modelAssets = mockFetcher.toLocalModelAssets(remoteAssets)

        // Then
        assertEquals(2, modelAssets.size)
        assertEquals("Main Model", modelAssets[0].configurations.first().displayName)
        assertEquals("Vision Model", modelAssets[1].configurations.first().displayName)
    }

    @Test
    fun fetchRemoteConfig_parsesMultipleAssetsWithSharedFile() = runTest {
        // Given
        val mockAssets = listOf(
            LocalModelAsset(
                metadata = LocalModelMetadata(
                    id = LocalModelId("0"),
                    huggingFaceModelName = "model/main",
                    remoteFileName = "main.bin",
                    localFileName = "main.bin",
                    sha256 = "abc123",
                    sizeInBytes = 1000000L,
                    modelFileFormat = ModelFileFormat.LITERTLM
                ),
                configurations = listOf(
                    LocalModelConfiguration(
                        id = LocalModelConfigurationId("config-main-1"),
                        localModelId = LocalModelId("0"),
                        displayName = "Main Model",
                        maxTokens = 2048,
                        contextWindow = 2048,
                        temperature = 0.0,
                        topP = 0.95,
                        topK = 40,
                        repetitionPenalty = 1.1,
                        systemPrompt = "You are a helpful assistant.",
                        defaultAssignments = listOf(ModelType.MAIN)
                    )
                )
            ),
            LocalModelAsset(
                metadata = LocalModelMetadata(
                    id = LocalModelId("0"),
                    huggingFaceModelName = "model/shared",
                    remoteFileName = "shared.bin",
                    localFileName = "shared.bin",
                    sha256 = "shared123",
                    sizeInBytes = 3000000L,
                    modelFileFormat = ModelFileFormat.LITERTLM,
                    isMultimodal = true
                ),
                configurations = listOf(
                    LocalModelConfiguration(
                        id = LocalModelConfigurationId("config-fast-1"),
                        localModelId = LocalModelId("0"),
                        displayName = "Fast Model",
                        maxTokens = 2048,
                        contextWindow = 8192,
                        temperature = 0.8,
                        topP = 0.9,
                        topK = 50,
                        repetitionPenalty = 1.05,
                        systemPrompt = "Be quick.",
                        defaultAssignments = listOf(ModelType.FAST)
                    ),
                    LocalModelConfiguration(
                        id = LocalModelConfigurationId("config-thinking-1"),
                        localModelId = LocalModelId("0"),
                        displayName = "Thinking Model",
                        maxTokens = 2048,
                        contextWindow = 8192,
                        temperature = 0.05,
                        topP = 0.9,
                        topK = 50,
                        repetitionPenalty = 1.05,
                        systemPrompt = "Think deeply.",
                        thinkingEnabled = true,
                        defaultAssignments = listOf(ModelType.THINKING)
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
        assertEquals(2, assets.size)
        assertEquals(1, assets[0].configurations.size)
        assertEquals(2, assets[1].configurations.size)
        assertTrue(assets[1].metadata.isMultimodal)
    }
}
