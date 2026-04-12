package com.browntowndev.pocketcrew.domain.usecase.download

import com.browntowndev.pocketcrew.domain.model.config.LocalModelAsset
import com.browntowndev.pocketcrew.domain.model.config.LocalModelId
import com.browntowndev.pocketcrew.domain.model.config.LocalModelMetadata
import com.browntowndev.pocketcrew.domain.model.download.DownloadModelsResult
import com.browntowndev.pocketcrew.domain.model.download.DownloadSource
import com.browntowndev.pocketcrew.domain.model.download.ModelScanResult
import com.browntowndev.pocketcrew.domain.model.inference.ModelFileFormat
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.port.download.ModelDownloadOrchestratorPort
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.repository.ActiveModelProviderPort
import com.browntowndev.pocketcrew.domain.port.repository.LocalModelRepositoryPort
import com.browntowndev.pocketcrew.domain.port.repository.ModelConfigFetcherPort
import com.browntowndev.pocketcrew.domain.usecase.modelconfig.SyncLocalModelRegistryUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class InitializeModelsUseCaseTest {

    private lateinit var modelConfigFetcher: ModelConfigFetcherPort
    private lateinit var activeModelProvider: ActiveModelProviderPort
    private lateinit var syncLocalModelRegistryUseCase: SyncLocalModelRegistryUseCase
    private lateinit var localModelRepository: LocalModelRepositoryPort
    private lateinit var modelDownloadOrchestrator: ModelDownloadOrchestratorPort
    private lateinit var checkModelsUseCase: CheckModelsUseCase
    private lateinit var logPort: LoggingPort
    private lateinit var useCase: InitializeModelsUseCase

    @BeforeEach
    fun setup() {
        modelConfigFetcher = mockk()
        activeModelProvider = mockk()
        syncLocalModelRegistryUseCase = mockk(relaxed = true)
        localModelRepository = mockk()
        modelDownloadOrchestrator = mockk(relaxed = true)
        checkModelsUseCase = mockk()
        logPort = mockk(relaxed = true)

        useCase = InitializeModelsUseCase(
            modelConfigFetcher,
            activeModelProvider,
            syncLocalModelRegistryUseCase,
            localModelRepository,
            modelDownloadOrchestrator,
            checkModelsUseCase,
            logPort
        )
    }

    @Test
    fun invoke_reconcilesSoftDeletedModelSource() = runTest {
        // Given
        val sha = "sha256-hash"
        val fileName = "model.gguf"
        
        // Remote config says R2
        val remoteAsset = createMockAsset(sha, fileName, source = DownloadSource.CLOUDFLARE_R2)
        val remoteConfigs = mapOf(ModelType.MAIN to remoteAsset)
        coEvery { modelConfigFetcher.fetchRemoteConfig() } returns Result.success(remoteConfigs)

        // Local DB has soft-deleted asset with default HF source
        val softDeletedAsset = createMockAsset(sha, fileName, source = DownloadSource.HUGGING_FACE)
        coEvery { localModelRepository.getSoftDeletedModels() } returns listOf(softDeletedAsset)

        // CheckModelsUseCase returns empty results for simplicity
        val emptyResult = DownloadModelsResult(
            allModels = remoteConfigs,
            modelsToDownload = emptyList(),
            scanResult = ModelScanResult(emptyList(), emptyMap(), true),
            availableToRedownload = emptyList()
        )
        coEvery { checkModelsUseCase(any()) } returns emptyResult

        // When
        val result = useCase()

        // Then
        val reconciledAsset = result.availableToRedownload.find { it.metadata.sha256 == sha }
        assertEquals(DownloadSource.CLOUDFLARE_R2, reconciledAsset?.metadata?.source)
    }

    private fun createMockAsset(
        sha: String,
        fileName: String,
        source: DownloadSource
    ): LocalModelAsset {
        return LocalModelAsset(
            metadata = LocalModelMetadata(
                id = LocalModelId("1"),
                huggingFaceModelName = "user/repo",
                remoteFileName = fileName,
                localFileName = fileName,
                sha256 = sha,
                sizeInBytes = 1000L,
                modelFileFormat = ModelFileFormat.GGUF,
                source = source
            ),
            configurations = emptyList()
        )
    }
}
