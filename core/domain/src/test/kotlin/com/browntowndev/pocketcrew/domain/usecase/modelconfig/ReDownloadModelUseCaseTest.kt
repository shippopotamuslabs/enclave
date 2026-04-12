package com.browntowndev.pocketcrew.domain.usecase.modelconfig

import com.browntowndev.pocketcrew.domain.model.config.LocalModelAsset
import com.browntowndev.pocketcrew.domain.model.config.LocalModelConfiguration
import com.browntowndev.pocketcrew.domain.model.config.LocalModelId
import com.browntowndev.pocketcrew.domain.model.config.LocalModelMetadata
import com.browntowndev.pocketcrew.domain.model.inference.ModelFileFormat
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.port.download.DownloadWorkSchedulerPort
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.repository.LocalModelRepositoryPort
import com.browntowndev.pocketcrew.domain.port.repository.ModelConfigFetcherPort
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ReDownloadModelUseCaseTest {
    private val localModelRepository = mockk<LocalModelRepositoryPort>()
    private val modelConfigFetcher = mockk<ModelConfigFetcherPort>()
    private val downloadWorkScheduler = mockk<DownloadWorkSchedulerPort>(relaxed = true)
    private val loggingPort = mockk<LoggingPort>(relaxed = true)
    private lateinit var useCase: ReDownloadModelUseCase

    @BeforeEach
    fun setup() {
        useCase = ReDownloadModelUseCase(
            localModelRepository,
            modelConfigFetcher,
            downloadWorkScheduler,
            loggingPort
        )
    }

    @Test
    fun `invoke with non-existent model returns failure with IllegalStateException`() = runTest {
        val modelId = LocalModelId("test")
        coEvery { localModelRepository.getAssetById(modelId) } returns null

        val result = useCase(modelId)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
        assertTrue(result.exceptionOrNull()?.message?.contains("not found") == true)
    }

    @Test
    fun `invoke when remote config fetch fails returns failure`() = runTest {
        val modelId = LocalModelId("test")
        val asset = createSoftDeletedAsset(modelId, "sha123")
        coEvery { localModelRepository.getAssetById(modelId) } returns asset
        coEvery { modelConfigFetcher.fetchRemoteConfig() } returns Result.failure(Exception("Network error"))

        val result = useCase(modelId)

        assertTrue(result.isFailure)
        assertEquals("Network error", result.exceptionOrNull()?.message)
    }

    @Test
    fun `invoke when no SHA match found returns failure`() = runTest {
        val modelId = LocalModelId("test")
        val asset = createSoftDeletedAsset(modelId, "sha123")
        coEvery { localModelRepository.getAssetById(modelId) } returns asset
        coEvery { modelConfigFetcher.fetchRemoteConfig() } returns Result.success(emptyMap())

        val result = useCase(modelId)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("no longer exists") == true)
    }

    @Test
    fun `invoke on success restores presets with system flag and schedules download with UNASSIGNED type`() = runTest {
        val modelId = LocalModelId("test")
        val sha = "sha123"
        val softDeletedAsset = createSoftDeletedAsset(modelId, sha)
        
        // Use real instances because copy() is final and cannot be mocked easily
        val remoteConfig1 = LocalModelConfiguration(
            id = com.browntowndev.pocketcrew.domain.model.config.LocalModelConfigurationId("id1"),
            localModelId = LocalModelId("other"),
            displayName = "Config 1",
            maxTokens = 100,
            contextWindow = 1000,
            temperature = 0.7,
            topP = 0.9,
            topK = 40,
            repetitionPenalty = 1.1,
            systemPrompt = "System 1",
            isSystemPreset = false
        )
        val remoteConfig2 = LocalModelConfiguration(
            id = com.browntowndev.pocketcrew.domain.model.config.LocalModelConfigurationId("id2"),
            localModelId = LocalModelId("other"),
            displayName = "Config 2",
            maxTokens = 200,
            contextWindow = 2000,
            temperature = 0.8,
            topP = 0.95,
            topK = 50,
            repetitionPenalty = 1.2,
            systemPrompt = "System 2",
            isSystemPreset = false
        )
        
        val remoteMatch = mockk<LocalModelAsset>()
        val remoteConfigs = listOf(remoteConfig1, remoteConfig2)
        
        // These are the instances we expect to be passed to restoreSoftDeletedModel
        // (after use case calls .copy(localModelId = modelId, isSystemPreset = true))
        val expectedRestoredConfigs = remoteConfigs.map { 
            it.copy(localModelId = modelId, isSystemPreset = true) 
        }

        coEvery { remoteMatch.metadata.sha256 } returns sha
        coEvery { remoteMatch.configurations } returns remoteConfigs
        
        coEvery { localModelRepository.getAssetById(modelId) } returns softDeletedAsset
        coEvery { modelConfigFetcher.fetchRemoteConfig() } returns Result.success(mapOf(ModelType.MAIN to remoteMatch))
        coEvery { localModelRepository.restoreSoftDeletedModel(modelId, any()) } returns softDeletedAsset.copy(configurations = expectedRestoredConfigs)

        val result = useCase(modelId)

        assertTrue(result.isSuccess)
        
        // Verify repository restoration with expected flags set
        coVerify { 
            localModelRepository.restoreSoftDeletedModel(
                modelId, 
                match { configs -> 
                    configs.size == 2 && configs.all { it.localModelId == modelId && it.isSystemPreset }
                }
            ) 
        }
        
        // Verify download scheduling
        coVerify { 
            downloadWorkScheduler.scheduleModelDownload(
                modelType = ModelType.UNASSIGNED,
                modelAsset = match { it.metadata.id == modelId && it.configurations.size == 2 }
            )
        }
    }

    private fun createSoftDeletedAsset(id: LocalModelId, sha: String): LocalModelAsset {
        return LocalModelAsset(
            metadata = LocalModelMetadata(
                id = id,
                huggingFaceModelName = "test",
                remoteFileName = "test.gguf",
                localFileName = "test.gguf",
                sha256 = sha,
                sizeInBytes = 100,
                modelFileFormat = ModelFileFormat.GGUF,
                visionCapable = false
            ),
            configurations = emptyList()
        )
    }
}
