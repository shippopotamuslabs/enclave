package com.browntowndev.enclave.domain.usecase.download

import com.browntowndev.enclave.domain.model.config.LocalModelAsset
import com.browntowndev.enclave.domain.model.config.LocalModelConfiguration
import com.browntowndev.enclave.domain.model.config.LocalModelConfigurationId
import com.browntowndev.enclave.domain.model.config.LocalModelId
import com.browntowndev.enclave.domain.model.config.LocalModelMetadata
import com.browntowndev.enclave.domain.model.config.UtilityType
import com.browntowndev.enclave.domain.model.download.DownloadModelsResult
import com.browntowndev.enclave.domain.model.download.DownloadSource
import com.browntowndev.enclave.domain.model.download.ModelScanResult
import com.browntowndev.enclave.domain.model.inference.ModelFileFormat
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.port.download.ModelDownloadOrchestratorPort
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import com.browntowndev.enclave.domain.port.repository.ActiveModelProviderPort
import com.browntowndev.enclave.domain.port.repository.LocalModelRepositoryPort
import com.browntowndev.enclave.domain.port.repository.ModelConfigFetcherPort
import com.browntowndev.enclave.domain.port.repository.UtilityModelRepositoryPort
import com.browntowndev.enclave.domain.usecase.FakeUtilityModelRepository
import com.browntowndev.enclave.domain.usecase.modelconfig.SyncLocalModelRegistryUseCase
import com.browntowndev.enclave.domain.usecase.modelconfig.SyncUtilityModelRegistryUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class InitializeModelsUseCaseTest {
    private lateinit var modelConfigFetcher: ModelConfigFetcherPort
    private lateinit var activeModelProvider: ActiveModelProviderPort
    private lateinit var syncLocalModelRegistryUseCase: SyncLocalModelRegistryUseCase
    private lateinit var localModelRepository: LocalModelRepositoryPort
    private lateinit var modelDownloadOrchestrator: ModelDownloadOrchestratorPort
    private lateinit var checkModelsUseCase: CheckModelsUseCase
    private lateinit var syncUtilityModelRegistryUseCase: SyncUtilityModelRegistryUseCase
    private lateinit var utilityModelRepository: UtilityModelRepositoryPort
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
        utilityModelRepository = FakeUtilityModelRepository()
        logPort = mockk(relaxed = true)
        syncUtilityModelRegistryUseCase =
            SyncUtilityModelRegistryUseCase(utilityModelRepository, logPort)

        useCase =
            InitializeModelsUseCase(
                modelConfigFetcher,
                activeModelProvider,
                syncLocalModelRegistryUseCase,
                localModelRepository,
                modelDownloadOrchestrator,
                checkModelsUseCase,
                syncUtilityModelRegistryUseCase,
                logPort,
            )
    }

    @Test
    fun invoke_reconcilesSoftDeletedModelSource() =
        runTest {
            // Given
            val sha = "sha256-hash"
            val fileName = "model.gguf"

            // Remote config says R2
            val remoteAsset = createMockAsset(sha, fileName, source = DownloadSource.CLOUDFLARE_R2)
            val remoteAssets = listOf(remoteAsset)
            coEvery { modelConfigFetcher.fetchRemoteConfig() } returns Result.success(remoteAssets)

            // Local DB has soft-deleted asset with default HF source
            val softDeletedAsset = createMockAsset(sha, fileName, source = DownloadSource.HUGGING_FACE)
            coEvery { localModelRepository.getSoftDeletedModels() } returns listOf(softDeletedAsset)

            // CheckModelsUseCase returns empty results for simplicity
            // Since the asset is soft-deleted, it will be filtered out of slot assignments
            val emptyResult =
                DownloadModelsResult(
                    allModels = emptyMap(),
                    modelsToDownload = emptyList(),
                    scanResult = ModelScanResult(emptyList(), emptyMap(), true),
                    availableToRedownload = emptyList(),
                )
            coEvery { checkModelsUseCase(any(), any()) } returns emptyResult

            // When
            val result = useCase()

            // Then
            val reconciledAsset = result.availableToRedownload.find { it.metadata.sha256 == sha }
            assertEquals(DownloadSource.CLOUDFLARE_R2, reconciledAsset?.metadata?.source)
        }

    @Test
    fun invoke_acceptsUtilityAssetsWithoutCreatingSlotAssignments() =
        runTest {
            val assignedAsset =
                createMockAsset(
                    sha = "assigned-sha",
                    fileName = "assigned.gguf",
                    source = DownloadSource.HUGGING_FACE,
                )
            val utilityAsset =
                createMockAsset(
                    sha = "whisper-sha",
                    fileName = "ggml-base.en.bin",
                    source = DownloadSource.HUGGING_FACE,
                ).copy(
                    metadata =
                        LocalModelMetadata(
                            id = LocalModelId("utility"),
                            huggingFaceModelName = "ggerganov/whisper.cpp",
                            remoteFileName = "ggml-base.en.bin",
                            localFileName = "ggml-base.en.bin",
                            sha256 = "whisper-sha",
                            sizeInBytes = 147_964_211L,
                            modelFileFormat = ModelFileFormat.BIN,
                            source = DownloadSource.HUGGING_FACE,
                            utilityType = UtilityType.WHISPER,
                        ),
                    configurations = emptyList(),
                )
            coEvery { modelConfigFetcher.fetchRemoteConfig() } returns Result.success(listOf(assignedAsset, utilityAsset))
            coEvery { localModelRepository.getSoftDeletedModels() } returns emptyList()

            val resultWithUtility =
                DownloadModelsResult(
                    allModels = mapOf(ModelType.MAIN to assignedAsset),
                    utilityAssets = listOf(utilityAsset),
                    modelsToDownload = listOf(utilityAsset),
                    scanResult = ModelScanResult(listOf(utilityAsset), emptyMap(), false),
                )
            coEvery {
                checkModelsUseCase(
                    expectedModels = mapOf(ModelType.MAIN to assignedAsset),
                    utilityAssets = listOf(utilityAsset),
                )
            } returns resultWithUtility

            val result = useCase()

            assertEquals(listOf(utilityAsset), result.utilityAssets)
            coVerify(exactly = 0) {
                syncLocalModelRegistryUseCase(any(), utilityAsset)
            }
        }

    @Test
    @DisplayName("SCN-INIT-001")
    fun scnInit001_persistsValidOnDiskUtility() =
        runTest {
            val utilityAsset =
                createMockAsset(
                    sha = "whisper-sha",
                    fileName = "ggml-base.en.bin",
                    source = DownloadSource.HUGGING_FACE,
                ).copy(
                    metadata =
                        LocalModelMetadata(
                            id = LocalModelId("utility"),
                            huggingFaceModelName = "ggerganov/whisper.cpp",
                            remoteFileName = "ggml-base.en.bin",
                            localFileName = "ggml-base.en.bin",
                            sha256 = "whisper-sha",
                            sizeInBytes = 4L,
                            modelFileFormat = ModelFileFormat.BIN,
                            source = DownloadSource.HUGGING_FACE,
                            utilityType = UtilityType.WHISPER,
                        ),
                    configurations = emptyList(),
                )
            coEvery { modelConfigFetcher.fetchRemoteConfig() } returns Result.success(listOf(utilityAsset))
            coEvery { localModelRepository.getSoftDeletedModels() } returns emptyList()
            coEvery {
                checkModelsUseCase(
                    expectedModels = emptyMap(),
                    utilityAssets = listOf(utilityAsset),
                )
            } returns
                DownloadModelsResult(
                    allModels = emptyMap(),
                    utilityAssets = listOf(utilityAsset),
                    modelsToDownload = emptyList(),
                    scanResult = ModelScanResult(emptyList(), emptyMap(), true),
                )

            useCase()

            assertEquals(
                "whisper-sha",
                utilityModelRepository.getByUtilityType(UtilityType.WHISPER)?.sha256,
            )
            coVerify(exactly = 0) { syncLocalModelRegistryUseCase(any(), any()) }
        }

    @Test
    @DisplayName("SCN-INIT-002")
    fun scnInit002_skipsUtilityStillInModelsToDownload() =
        runTest {
            val utilityAsset =
                createMockAsset(
                    sha = "missing-sha",
                    fileName = "ggml-base.en.bin",
                    source = DownloadSource.HUGGING_FACE,
                ).copy(
                    metadata =
                        LocalModelMetadata(
                            id = LocalModelId("utility"),
                            huggingFaceModelName = "ggerganov/whisper.cpp",
                            remoteFileName = "ggml-base.en.bin",
                            localFileName = "ggml-base.en.bin",
                            sha256 = "missing-sha",
                            sizeInBytes = 4L,
                            modelFileFormat = ModelFileFormat.BIN,
                            source = DownloadSource.HUGGING_FACE,
                            utilityType = UtilityType.WHISPER,
                        ),
                    configurations = emptyList(),
                )
            coEvery { modelConfigFetcher.fetchRemoteConfig() } returns Result.success(listOf(utilityAsset))
            coEvery { localModelRepository.getSoftDeletedModels() } returns emptyList()
            coEvery {
                checkModelsUseCase(
                    expectedModels = emptyMap(),
                    utilityAssets = listOf(utilityAsset),
                )
            } returns
                DownloadModelsResult(
                    allModels = emptyMap(),
                    utilityAssets = listOf(utilityAsset),
                    modelsToDownload = listOf(utilityAsset),
                    scanResult = ModelScanResult(listOf(utilityAsset), emptyMap(), false),
                )

            useCase()

            assertNull(utilityModelRepository.getByUtilityType(UtilityType.WHISPER))
        }

    private fun createMockAsset(
        sha: String,
        fileName: String,
        source: DownloadSource,
    ): LocalModelAsset =
        LocalModelAsset(
            metadata =
                LocalModelMetadata(
                    id = LocalModelId("1"),
                    huggingFaceModelName = "user/repo",
                    remoteFileName = fileName,
                    localFileName = fileName,
                    sha256 = sha,
                    sizeInBytes = 1000L,
                    modelFileFormat = ModelFileFormat.GGUF,
                    source = source,
                ),
            configurations =
                listOf(
                    LocalModelConfiguration(
                        id = LocalModelConfigurationId("config-1"),
                        localModelId = LocalModelId("1"),
                        displayName = "Test Config",
                        maxTokens = 2048,
                        contextWindow = 4096,
                        temperature = 0.7,
                        topP = 0.95,
                        topK = 40,
                        repetitionPenalty = 1.1,
                        systemPrompt = "You are helpful.",
                        defaultAssignments = listOf(ModelType.MAIN),
                    ),
                ),
        )
}
