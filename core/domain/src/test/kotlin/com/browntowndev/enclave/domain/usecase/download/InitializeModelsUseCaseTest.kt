package com.browntowndev.enclave.domain.usecase.download

import com.browntowndev.enclave.domain.model.config.ActiveModelConfiguration
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
import com.browntowndev.enclave.domain.port.repository.TrustedDomainRepositoryPort
import com.browntowndev.enclave.domain.usecase.FakeUtilityModelRepository
import com.browntowndev.enclave.domain.usecase.modelconfig.SyncLocalModelRegistryUseCase
import com.browntowndev.enclave.domain.usecase.modelconfig.SyncUtilityModelRegistryUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
    private lateinit var trustedDomainRepository: TrustedDomainRepositoryPort
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
        trustedDomainRepository = mockk(relaxed = true)
        syncUtilityModelRegistryUseCase =
            SyncUtilityModelRegistryUseCase(utilityModelRepository, logPort)

        // Default: no active config for any slot — treated as "include for local download"
        coEvery { activeModelProvider.getActiveConfiguration(any()) } returns null

        useCase =
            InitializeModelsUseCase(
                modelConfigFetcher,
                activeModelProvider,
                syncLocalModelRegistryUseCase,
                localModelRepository,
                modelDownloadOrchestrator,
                checkModelsUseCase,
                syncUtilityModelRegistryUseCase,
                trustedDomainRepository,
                externalScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
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

    // -------- API slot filtering (SCN-INIT-API-*) --------

    @Test
    @DisplayName("SCN-INIT-API-01 — slot with API active config excluded from expectedModels")
    fun `scnInitApi01 slot configured as API is excluded from checkModelsUseCase expectedModels`() =
        runTest {
            val localAsset = createMockAsset("sha-main", "main.gguf", DownloadSource.HUGGING_FACE)
            coEvery { modelConfigFetcher.fetchRemoteConfig() } returns Result.success(listOf(localAsset))
            coEvery { localModelRepository.getSoftDeletedModels() } returns emptyList()

            val apiConfig = mockk<ActiveModelConfiguration> { every { isLocal } returns false }
            coEvery { activeModelProvider.getActiveConfiguration(ModelType.MAIN) } returns apiConfig

            coEvery { checkModelsUseCase(any(), any()) } returns emptyDownloadResult()

            useCase()

            coVerify {
                checkModelsUseCase(expectedModels = emptyMap(), utilityAssets = emptyList())
            }
        }

    @Test
    @DisplayName("SCN-INIT-API-02 — slot with null active config is included in expectedModels")
    fun `scnInitApi02 slot with no active config is included in checkModelsUseCase expectedModels`() =
        runTest {
            val localAsset = createMockAsset("sha-main", "main.gguf", DownloadSource.HUGGING_FACE)
            coEvery { modelConfigFetcher.fetchRemoteConfig() } returns Result.success(listOf(localAsset))
            coEvery { localModelRepository.getSoftDeletedModels() } returns emptyList()
            // activeModelProvider already returns null for all slots from @BeforeEach

            coEvery { checkModelsUseCase(any(), any()) } returns emptyDownloadResult()

            useCase()

            coVerify {
                checkModelsUseCase(
                    expectedModels = mapOf(ModelType.MAIN to localAsset),
                    utilityAssets = emptyList(),
                )
            }
        }

    @Test
    @DisplayName("SCN-INIT-API-03 — slot with local active config is included in expectedModels")
    fun `scnInitApi03 slot configured as local is included in checkModelsUseCase expectedModels`() =
        runTest {
            val localAsset = createMockAsset("sha-main", "main.gguf", DownloadSource.HUGGING_FACE)
            coEvery { modelConfigFetcher.fetchRemoteConfig() } returns Result.success(listOf(localAsset))
            coEvery { localModelRepository.getSoftDeletedModels() } returns emptyList()

            val localConfig = mockk<ActiveModelConfiguration> { every { isLocal } returns true }
            coEvery { activeModelProvider.getActiveConfiguration(ModelType.MAIN) } returns localConfig

            coEvery { checkModelsUseCase(any(), any()) } returns emptyDownloadResult()

            useCase()

            coVerify {
                checkModelsUseCase(
                    expectedModels = mapOf(ModelType.MAIN to localAsset),
                    utilityAssets = emptyList(),
                )
            }
        }

    @Test
    @DisplayName("SCN-INIT-API-04 — mixed slots: API-configured excluded, unconfigured included")
    fun `scnInitApi04 mixed slots only unconfigured slot passed to checkModelsUseCase`() =
        runTest {
            val mainAsset = createMockAsset("sha-main", "main.gguf", DownloadSource.HUGGING_FACE)
            val visionAsset =
                LocalModelAsset(
                    metadata =
                        mainAsset.metadata.copy(
                            id = LocalModelId("vision-id"),
                            sha256 = "sha-vision",
                            localFileName = "vision.gguf",
                            remoteFileName = "vision.gguf",
                        ),
                    configurations =
                        listOf(
                            LocalModelConfiguration(
                                id = LocalModelConfigurationId("config-vision"),
                                localModelId = LocalModelId("vision-id"),
                                displayName = "Vision Config",
                                maxTokens = 2048,
                                contextWindow = 4096,
                                temperature = 0.7,
                                topP = 0.95,
                                topK = 40,
                                repetitionPenalty = 1.1,
                                systemPrompt = "",
                                defaultAssignments = listOf(ModelType.VISION),
                            ),
                        ),
                )

            coEvery { modelConfigFetcher.fetchRemoteConfig() } returns
                Result.success(listOf(mainAsset, visionAsset))
            coEvery { localModelRepository.getSoftDeletedModels() } returns emptyList()

            val apiConfig = mockk<ActiveModelConfiguration> { every { isLocal } returns false }
            coEvery { activeModelProvider.getActiveConfiguration(ModelType.MAIN) } returns apiConfig
            // VISION returns null (from @BeforeEach default) → included

            coEvery { checkModelsUseCase(any(), any()) } returns emptyDownloadResult()

            useCase()

            coVerify {
                checkModelsUseCase(
                    expectedModels = mapOf(ModelType.VISION to visionAsset),
                    utilityAssets = emptyList(),
                )
            }
        }

    @Test
    @DisplayName("SCN-INIT-API-05 — soft-deleted asset with API config excluded via soft-delete path")
    fun `scnInitApi05 soft-deleted asset is excluded regardless of API config`() =
        runTest {
            val localAsset = createMockAsset("sha-main", "main.gguf", DownloadSource.HUGGING_FACE)
            coEvery { modelConfigFetcher.fetchRemoteConfig() } returns Result.success(listOf(localAsset))
            coEvery { localModelRepository.getSoftDeletedModels() } returns listOf(localAsset)

            // API config set, but should never even be reached (soft-delete check is first)
            val apiConfig = mockk<ActiveModelConfiguration> { every { isLocal } returns false }
            coEvery { activeModelProvider.getActiveConfiguration(ModelType.MAIN) } returns apiConfig

            coEvery { checkModelsUseCase(any(), any()) } returns emptyDownloadResult()

            useCase()

            coVerify {
                checkModelsUseCase(expectedModels = emptyMap(), utilityAssets = emptyList())
            }
        }

    private fun emptyDownloadResult() =
        DownloadModelsResult(
            allModels = emptyMap(),
            modelsToDownload = emptyList(),
            scanResult = ModelScanResult(emptyList(), emptyMap(), true),
            availableToRedownload = emptyList(),
        )

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
