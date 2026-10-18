package com.browntowndev.enclave.domain.usecase.download

import com.browntowndev.enclave.core.testing.FakeActiveModelProvider
import com.browntowndev.enclave.core.testing.FakeDefaultModelRepository
import com.browntowndev.enclave.core.testing.FakeGpuCapability
import com.browntowndev.enclave.core.testing.FakeLocalImageGenerationCapability
import com.browntowndev.enclave.core.testing.FakeLocalModelRepository
import com.browntowndev.enclave.core.testing.FakeLoggingPort
import com.browntowndev.enclave.core.testing.FakeModelConfigFetcher
import com.browntowndev.enclave.core.testing.FakeModelFileScanner
import com.browntowndev.enclave.core.testing.FakeQnnPackInstaller
import com.browntowndev.enclave.core.testing.FakeTransactionProvider
import com.browntowndev.enclave.core.testing.FakeTrustedDomainRepository
import com.browntowndev.enclave.core.testing.download.FakeModelDownloadOrchestrator
import com.browntowndev.enclave.domain.model.config.ActiveModelConfiguration
import com.browntowndev.enclave.domain.model.config.LocalModelAsset
import com.browntowndev.enclave.domain.model.config.LocalModelConfiguration
import com.browntowndev.enclave.domain.model.config.LocalModelConfigurationId
import com.browntowndev.enclave.domain.model.config.LocalModelId
import com.browntowndev.enclave.domain.model.config.LocalModelMetadata
import com.browntowndev.enclave.domain.model.config.ModelConfigurationId
import com.browntowndev.enclave.domain.model.config.SlotResolvedLocalModel
import com.browntowndev.enclave.domain.model.config.UtilityType
import com.browntowndev.enclave.domain.model.download.DownloadModelsResult
import com.browntowndev.enclave.domain.model.download.DownloadSource
import com.browntowndev.enclave.domain.model.download.ModelPackTestFixtures
import com.browntowndev.enclave.domain.model.download.ModelScanResult
import com.browntowndev.enclave.domain.model.download.ParsedModelCatalog
import com.browntowndev.enclave.domain.model.device.SnapdragonTier
import com.browntowndev.enclave.domain.model.inference.ModelFileFormat
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.port.device.SnapdragonTierPort
import com.browntowndev.enclave.domain.usecase.FakeUtilityModelRepository
import com.browntowndev.enclave.domain.usecase.modelconfig.SyncLocalModelRegistryUseCase
import com.browntowndev.enclave.domain.usecase.modelconfig.SyncUtilityModelRegistryUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/** Recording [CheckModelsUseCase] fake (ENC-28): captures arguments, returns [result]. */
private class FakeCheckModelsUseCase : CheckModelsUseCase(
    FakeModelFileScanner(),
    CheckModelEligibilityUseCase(FakeLoggingPort()),
    FakeLoggingPort(),
) {
    data class Call(
        val expectedModels: Map<ModelType, LocalModelAsset>,
        val utilityAssets: List<LocalModelAsset>,
    )

    val calls = mutableListOf<Call>()

    var result: DownloadModelsResult =
        DownloadModelsResult(
            allModels = emptyMap(),
            modelsToDownload = emptyList(),
            scanResult = ModelScanResult(emptyList(), emptyMap(), true),
            availableToRedownload = emptyList(),
        )

    override suspend fun invoke(
        expectedModels: Map<ModelType, LocalModelAsset>,
        utilityAssets: List<LocalModelAsset>,
    ): DownloadModelsResult {
        calls += Call(expectedModels, utilityAssets)
        return result
    }
}

/** Recording [SyncLocalModelRegistryUseCase] fake (ENC-28): captures calls, performs no registry writes. */
private class FakeSyncLocalModelRegistryUseCase : SyncLocalModelRegistryUseCase(
    FakeLocalModelRepository(),
    FakeDefaultModelRepository(),
    FakeTransactionProvider(),
    FakeLoggingPort(),
) {
    data class Call(
        val modelType: ModelType,
        val asset: LocalModelAsset,
        val assignAsDefault: Boolean,
    )

    val calls = mutableListOf<Call>()

    override suspend fun invoke(
        modelType: ModelType,
        asset: LocalModelAsset,
        assignAsDefault: Boolean,
    ): SlotResolvedLocalModel {
        calls += Call(modelType, asset, assignAsDefault)
        return SlotResolvedLocalModel(
            modelType = modelType,
            asset = asset,
            selectedConfig =
                asset.configurations.firstOrNull()
                    ?: LocalModelConfiguration(
                        localModelId = asset.metadata.id,
                        displayName = "Fake Config",
                        maxTokens = 2048,
                        contextWindow = 2048,
                        temperature = 0.7,
                        topP = 0.9,
                        topK = 40,
                        repetitionPenalty = 1.0,
                        systemPrompt = "",
                    ),
        )
    }
}

class InitializeModelsUseCaseTest {
    private lateinit var modelConfigFetcher: FakeModelConfigFetcher
    private lateinit var activeModelProvider: FakeActiveModelProvider
    private lateinit var syncLocalModelRegistryUseCase: FakeSyncLocalModelRegistryUseCase
    private lateinit var localModelRepository: FakeLocalModelRepository
    private lateinit var modelDownloadOrchestrator: FakeModelDownloadOrchestrator
    private lateinit var checkModelsUseCase: FakeCheckModelsUseCase
    private lateinit var syncUtilityModelRegistryUseCase: SyncUtilityModelRegistryUseCase
    private lateinit var utilityModelRepository: FakeUtilityModelRepository
    private lateinit var logPort: FakeLoggingPort
    private lateinit var trustedDomainRepository: FakeTrustedDomainRepository
    private lateinit var gpuCapabilityPort: FakeGpuCapability
    private lateinit var capabilityPort: FakeLocalImageGenerationCapability
    private lateinit var resolveSd15QnnPackCatalogAssetUseCase: ResolveSd15QnnPackCatalogAssetUseCase
    private lateinit var qnnPackInstaller: FakeQnnPackInstaller
    private lateinit var useCase: InitializeModelsUseCase

    @BeforeEach
    fun setup() {
        modelConfigFetcher = FakeModelConfigFetcher()
        activeModelProvider = FakeActiveModelProvider()
        syncLocalModelRegistryUseCase = FakeSyncLocalModelRegistryUseCase()
        localModelRepository = FakeLocalModelRepository()
        modelDownloadOrchestrator = FakeModelDownloadOrchestrator()
        checkModelsUseCase = FakeCheckModelsUseCase()
        utilityModelRepository = FakeUtilityModelRepository()
        logPort = FakeLoggingPort()
        trustedDomainRepository = FakeTrustedDomainRepository()
        gpuCapabilityPort = FakeGpuCapability()
        capabilityPort = FakeLocalImageGenerationCapability()
        syncUtilityModelRegistryUseCase =
            SyncUtilityModelRegistryUseCase(utilityModelRepository, logPort)

        // Default: no active config for any slot — treated as "include for local download"
        activeModelProvider.configuration = null
        gpuCapabilityPort.localImageGenerationSupported = true
        capabilityPort.capabilities =
            com.browntowndev.enclave.domain.model.media.LocalImageGenerationCapabilities(
                npuAvailable = com.browntowndev.enclave.domain.model.media.LocalImageGenTriState.YES,
                sd15NpuSupported = com.browntowndev.enclave.domain.model.media.LocalImageGenTriState.YES,
                sd15CpuSupported = com.browntowndev.enclave.domain.model.media.LocalImageGenTriState.NO,
                sdxlNpuSupported = com.browntowndev.enclave.domain.model.media.LocalImageGenTriState.NO,
                recommendedBackend = com.browntowndev.enclave.domain.model.media.LocalImageGenerationBackendKind.QNN_NPU,
                recommendedModelId = com.browntowndev.enclave.domain.model.media.LocalImageGenerationModelId.SD15_QNN,
                diagnostics = emptyList(),
            )
        resolveSd15QnnPackCatalogAssetUseCase =
            ResolveSd15QnnPackCatalogAssetUseCase(
                object : SnapdragonTierPort {
                    override fun currentTier(): SnapdragonTier = SnapdragonTier.GEN1
                },
            )
        qnnPackInstaller = FakeQnnPackInstaller()
        qnnPackInstaller.installResult = true

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
                gpuCapabilityPort,
                capabilityPort,
                resolveSd15QnnPackCatalogAssetUseCase,
                qnnPackInstaller,
                externalScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
                logPort = logPort,
            )
    }

    private fun activeConfig(
        isLocal: Boolean,
        id: ModelConfigurationId = LocalModelConfigurationId("config-1"),
    ): ActiveModelConfiguration =
        ActiveModelConfiguration(
            id = id,
            isLocal = isLocal,
            name = "Test Config",
            modelId = "test-model",
            systemPrompt = null,
            temperature = null,
            topK = null,
            topP = null,
            maxTokens = null,
            minP = null,
            repetitionPenalty = null,
            contextWindow = null,
            thinkingEnabled = false,
        )

    @Test
    fun invoke_reconcilesSoftDeletedModelSource() =
        runTest {
            // Given
            val sha = "sha256-hash"
            val fileName = "model.gguf"

            // Remote config says R2
            val remoteAsset = createMockAsset(sha, fileName, source = DownloadSource.CLOUDFLARE_R2)
            val remoteAssets = listOf(remoteAsset)
            modelConfigFetcher.parsedCatalogResult = Result.success(catalogOf(remoteAssets))

            // Local DB has soft-deleted asset with default HF source
            val softDeletedAsset = createMockAsset(sha, fileName, source = DownloadSource.HUGGING_FACE)
            localModelRepository.registerSoftDeletedModel(softDeletedAsset.metadata.id, softDeletedAsset)

            // CheckModelsUseCase returns empty results for simplicity
            // Since the asset is soft-deleted, it will be filtered out of slot assignments
            val emptyResult =
                DownloadModelsResult(
                    allModels = emptyMap(),
                    modelsToDownload = emptyList(),
                    scanResult = ModelScanResult(emptyList(), emptyMap(), true),
                    availableToRedownload = emptyList(),
                )
            checkModelsUseCase.result = emptyResult

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
            modelConfigFetcher.parsedCatalogResult = Result.success(catalogOf(listOf(assignedAsset, utilityAsset)))

            val resultWithUtility =
                DownloadModelsResult(
                    allModels = mapOf(ModelType.MAIN to assignedAsset),
                    utilityAssets = listOf(utilityAsset),
                    modelsToDownload = listOf(utilityAsset),
                    scanResult = ModelScanResult(listOf(utilityAsset), emptyMap(), false),
                )
            checkModelsUseCase.result = resultWithUtility

            val result = useCase()

            assertEquals(listOf(utilityAsset), result.utilityAssets)
            assertTrue(
                checkModelsUseCase.calls.any {
                    it == FakeCheckModelsUseCase.Call(mapOf(ModelType.MAIN to assignedAsset), listOf(utilityAsset))
                },
            )
            assertTrue(syncLocalModelRegistryUseCase.calls.none { it.asset == utilityAsset })
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
            modelConfigFetcher.parsedCatalogResult = Result.success(catalogOf(listOf(utilityAsset)))
            checkModelsUseCase.result =
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
            assertTrue(syncLocalModelRegistryUseCase.calls.isEmpty())
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
            modelConfigFetcher.parsedCatalogResult = Result.success(catalogOf(listOf(utilityAsset)))
            checkModelsUseCase.result =
                DownloadModelsResult(
                    allModels = emptyMap(),
                    utilityAssets = listOf(utilityAsset),
                    modelsToDownload = listOf(utilityAsset),
                    scanResult = ModelScanResult(listOf(utilityAsset), emptyMap(), false),
                )

            useCase()

            assertNull(utilityModelRepository.getByUtilityType(UtilityType.WHISPER))
        }

    @Test
    @DisplayName("SCN-INIT-003")
    fun scnInit003_defersQnnUtilityRegistryWhenPackNotOnDisk() =
        runTest {
            val qnnUtility = ModelPackTestFixtures.sd15QnnPackUtilityAsset()
            modelConfigFetcher.parsedCatalogResult = Result.success(catalogOf(listOf(qnnUtility)))
            qnnPackInstaller.installResult = false
            checkModelsUseCase.result =
                DownloadModelsResult(
                    allModels = emptyMap(),
                    utilityAssets = listOf(qnnUtility),
                    modelsToDownload = emptyList(),
                    scanResult = ModelScanResult(emptyList(), emptyMap(), true),
                )

            useCase()

            assertNull(utilityModelRepository.getByUtilityType(UtilityType.SD15_QNN_PACK))
        }

    // -------- API slot filtering (SCN-INIT-API-*) --------

    @Test
    @DisplayName("SCN-INIT-API-01 — slot with API active config excluded from expectedModels")
    fun `scnInitApi01 slot configured as API is excluded from checkModelsUseCase expectedModels`() =
        runTest {
            val localAsset = createMockAsset("sha-main", "main.gguf", DownloadSource.HUGGING_FACE)
            modelConfigFetcher.parsedCatalogResult = Result.success(catalogOf(listOf(localAsset)))

            val apiConfig = activeConfig(isLocal = false)
            activeModelProvider.configurationsByType[ModelType.MAIN] = apiConfig

            checkModelsUseCase.result = emptyDownloadResult()

            useCase()

            assertTrue(
                checkModelsUseCase.calls.any {
                    it == FakeCheckModelsUseCase.Call(expectedModels = emptyMap(), utilityAssets = emptyList())
                },
            )
        }

    @Test
    @DisplayName("SCN-INIT-API-02 — slot with null active config is included in expectedModels")
    fun `scnInitApi02 slot with no active config is included in checkModelsUseCase expectedModels`() =
        runTest {
            val localAsset = createMockAsset("sha-main", "main.gguf", DownloadSource.HUGGING_FACE)
            modelConfigFetcher.parsedCatalogResult = Result.success(catalogOf(listOf(localAsset)))
            // activeModelProvider already returns null for all slots from @BeforeEach

            checkModelsUseCase.result = emptyDownloadResult()

            useCase()

            assertTrue(
                checkModelsUseCase.calls.any {
                    it ==
                        FakeCheckModelsUseCase.Call(
                            expectedModels = mapOf(ModelType.MAIN to localAsset),
                            utilityAssets = emptyList(),
                        )
                },
            )
        }

    @Test
    @DisplayName("SCN-INIT-API-03 — slot with local active config is included in expectedModels")
    fun `scnInitApi03 slot configured as local is included in checkModelsUseCase expectedModels`() =
        runTest {
            val localAsset = createMockAsset("sha-main", "main.gguf", DownloadSource.HUGGING_FACE)
            modelConfigFetcher.parsedCatalogResult = Result.success(catalogOf(listOf(localAsset)))

            val localConfig = activeConfig(isLocal = true)
            activeModelProvider.configurationsByType[ModelType.MAIN] = localConfig

            checkModelsUseCase.result = emptyDownloadResult()

            useCase()

            assertTrue(
                checkModelsUseCase.calls.any {
                    it ==
                        FakeCheckModelsUseCase.Call(
                            expectedModels = mapOf(ModelType.MAIN to localAsset),
                            utilityAssets = emptyList(),
                        )
                },
            )
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

            modelConfigFetcher.parsedCatalogResult =
                Result.success(catalogOf(listOf(mainAsset, visionAsset)))

            val apiConfig = activeConfig(isLocal = false)
            activeModelProvider.configurationsByType[ModelType.MAIN] = apiConfig
            // VISION returns null (from @BeforeEach default) → included

            checkModelsUseCase.result = emptyDownloadResult()

            useCase()

            assertTrue(
                checkModelsUseCase.calls.any {
                    it ==
                        FakeCheckModelsUseCase.Call(
                            expectedModels = mapOf(ModelType.VISION to visionAsset),
                            utilityAssets = emptyList(),
                        )
                },
            )
        }

    @Test
    @DisplayName("MPK-005 API IMAGE_GENERATION skips entire pack on InitializeModelsUseCase")
    fun mpk005_apiImageGenerationSkipsEntirePack() =
        runTest {
            modelConfigFetcher.parsedCatalogResult =
                Result.success(ModelPackTestFixtures.parsedCatalog())

            val apiConfig = activeConfig(isLocal = false)
            activeModelProvider.configurationsByType[ModelType.IMAGE_GENERATION] = apiConfig

            checkModelsUseCase.result = emptyDownloadResult()

            useCase()

            val call = checkModelsUseCase.calls.last()
            assertEquals(emptyMap<ModelType, LocalModelAsset>(), call.expectedModels)
            assertFalse(call.utilityAssets.any { it.metadata.sha256 in ModelPackTestFixtures.packMemberShas })
        }

    @Test
    @DisplayName("MPK-025 Catalog failure still excludes pack for API IMAGE_GENERATION")
    fun mpk025_catalogFailureStillExcludesPackForApiImageGeneration() =
        runTest {
            val mainAsset = createMockAsset("main-sha", "main.gguf", DownloadSource.CLOUDFLARE_R2)
            val localConfig =
                activeConfig(
                    isLocal = true,
                    id = LocalModelConfigurationId("main-config"),
                )
            modelConfigFetcher.parsedCatalogResult =
                Result.failure(IllegalStateException("offline"))
            localModelRepository.setAssets(
                listOf(
                    ModelPackTestFixtures.diffusionAsset(),
                ),
            )
            activeModelProvider.configurationsByType[ModelType.MAIN] = localConfig
            localModelRepository.seedAssetForConfig(LocalModelConfigurationId("main-config"), mainAsset)
            activeModelProvider.configurationsByType[ModelType.IMAGE_GENERATION] =
                activeConfig(isLocal = false)

            val packHeavy =
                DownloadModelsResult(
                    allModels = mapOf(ModelType.MAIN to mainAsset),
                    utilityAssets = emptyList(),
                    modelsToDownload =
                        listOf(
                            mainAsset,
                            ModelPackTestFixtures.diffusionAsset(),
                        ),
                    scanResult = ModelScanResult(emptyList(), emptyMap(), false),
                )
            checkModelsUseCase.result = packHeavy

            val result = useCase()

            assertEquals(mapOf(ModelType.MAIN to mainAsset), checkModelsUseCase.calls.last().expectedModels)
            assertEquals(1, result.modelsToDownload.size)
            assertEquals(
                mainAsset.metadata.sha256,
                result.modelsToDownload
                    .single()
                    .metadata.sha256,
            )
            assertTrue(result.utilityAssets.isEmpty())
            assertEquals(ModelPackTestFixtures.packMemberShas, result.imageGenerationPackMemberSha256s)
            assertTrue(
                modelDownloadOrchestrator.initializeCalls.any { startupResult ->
                    startupResult.modelsToDownload.map { it.metadata.sha256 } ==
                        listOf(mainAsset.metadata.sha256)
                },
            )
        }

    @Test
    @DisplayName("MPK-012 Whisper utility still downloads when IMAGE_GENERATION is API")
    fun mpk012_whisperStillDownloadsWhenImageGenerationApi() =
        runTest {
            val whisper = ModelPackTestFixtures.whisperAsset()
            modelConfigFetcher.parsedCatalogResult =
                Result.success(ModelPackTestFixtures.parsedCatalog(includeWhisper = true))

            val apiConfig = activeConfig(isLocal = false)
            activeModelProvider.configurationsByType[ModelType.IMAGE_GENERATION] = apiConfig

            checkModelsUseCase.result =
                DownloadModelsResult(
                    allModels = emptyMap(),
                    utilityAssets = listOf(whisper),
                    modelsToDownload = listOf(whisper),
                    scanResult = ModelScanResult(listOf(whisper), emptyMap(), false),
                )

            useCase()

            val capturedUtilityAssets = checkModelsUseCase.calls.last().utilityAssets
            assertEquals(1, capturedUtilityAssets.size)
            assertEquals(
                ModelPackTestFixtures.WHISPER_SHA,
                capturedUtilityAssets
                    .single()
                    .metadata.sha256,
            )
        }

    @Test
    @DisplayName("MPK-019 Lazy impl must not download SD15 pack utilities when IMAGE_GENERATION is API")
    fun mpk019_apiImageGenerationExcludesPackFromModelsToDownload() =
        runTest {
            val catalogAssets =
                listOf(
                    ModelPackTestFixtures.diffusionAsset(),
                )
            modelConfigFetcher.parsedCatalogResult =
                Result.success(ModelPackTestFixtures.parsedCatalog(includeWhisper = false))

            val apiConfig = activeConfig(isLocal = false)
            activeModelProvider.configurationsByType[ModelType.IMAGE_GENERATION] = apiConfig

            val packAssets =
                listOf(
                    ModelPackTestFixtures.diffusionAsset(),
                )
            checkModelsUseCase.result =
                DownloadModelsResult(
                    allModels = emptyMap(),
                    utilityAssets = catalogAssets.filter { it.metadata.utilityType != null },
                    modelsToDownload = packAssets,
                    scanResult = ModelScanResult(packAssets, emptyMap(), false),
                )

            val result = useCase()

            assertEquals(0, result.modelsToDownload.count { it.metadata.sha256 == ModelPackTestFixtures.DIFFUSION_SHA })
        }

    @Test
    fun `unsupported device excludes image generation pack before orchestrator initialization`() =
        runTest {
            gpuCapabilityPort.localImageGenerationSupported = false
            modelConfigFetcher.parsedCatalogResult =
                Result.success(ModelPackTestFixtures.parsedCatalog(includeWhisper = true))
            val diffusionAsset = ModelPackTestFixtures.diffusionAsset()
            localModelRepository.registerSoftDeletedModel(diffusionAsset.metadata.id, diffusionAsset)

            val packAssets =
                listOf(
                    ModelPackTestFixtures.diffusionAsset(),
                )
            val whisper = ModelPackTestFixtures.whisperAsset()
            checkModelsUseCase.result =
                DownloadModelsResult(
                    allModels = mapOf(ModelType.IMAGE_GENERATION to ModelPackTestFixtures.diffusionAsset()),
                    utilityAssets = listOf(whisper),
                    modelsToDownload = packAssets + whisper,
                    scanResult = ModelScanResult(packAssets + whisper, emptyMap(), false),
                )

            val result = useCase()

            assertEquals(listOf(whisper), result.modelsToDownload)
            assertTrue(result.availableToRedownload.isEmpty())
            assertTrue(
                modelDownloadOrchestrator.initializeCalls.any { startupResult ->
                    startupResult.modelsToDownload == listOf(whisper)
                },
            )
        }

    @Test
    @DisplayName("SCN-INIT-API-05 — soft-deleted asset with API config excluded via soft-delete path")
    fun `scnInitApi05 soft-deleted asset is excluded regardless of API config`() =
        runTest {
            val localAsset = createMockAsset("sha-main", "main.gguf", DownloadSource.HUGGING_FACE)
            modelConfigFetcher.parsedCatalogResult = Result.success(catalogOf(listOf(localAsset)))
            localModelRepository.registerSoftDeletedModel(localAsset.metadata.id, localAsset)

            // API config set, but should never even be reached (soft-delete check is first)
            val apiConfig = activeConfig(isLocal = false)
            activeModelProvider.configurationsByType[ModelType.MAIN] = apiConfig

            checkModelsUseCase.result = emptyDownloadResult()

            useCase()

            assertTrue(
                checkModelsUseCase.calls.any {
                    it == FakeCheckModelsUseCase.Call(expectedModels = emptyMap(), utilityAssets = emptyList())
                },
            )
        }

    private fun catalogOf(assets: List<LocalModelAsset>): ParsedModelCatalog {
        val hasPackMembers = assets.any { it.metadata.sha256 in ModelPackTestFixtures.packMemberShas }
        return if (hasPackMembers) {
            ModelPackTestFixtures.parsedCatalog(
                includeWhisper = assets.any { it.metadata.sha256 == ModelPackTestFixtures.WHISPER_SHA },
            )
        } else {
            ParsedModelCatalog(assets = assets, packs = emptyList())
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

    @Test
    fun `DL-002 Gen2 startup result includes SD15 and excludes SDXL and Juggernaut`() =
        runTest {
            capabilityPort.capabilities =
                com.browntowndev.enclave.domain.model.media.LocalImageGenerationCapabilities(
                    snapdragonTier = SnapdragonTier.GEN2,
                    npuAvailable = com.browntowndev.enclave.domain.model.media.LocalImageGenTriState.YES,
                    sd15NpuSupported = com.browntowndev.enclave.domain.model.media.LocalImageGenTriState.YES,
                    sd15CpuSupported = com.browntowndev.enclave.domain.model.media.LocalImageGenTriState.NO,
                    sdxlNpuSupported = com.browntowndev.enclave.domain.model.media.LocalImageGenTriState.NO,
                    juggernautXlNpuSupported = com.browntowndev.enclave.domain.model.media.LocalImageGenTriState.NO,
                    recommendedBackend = com.browntowndev.enclave.domain.model.media.LocalImageGenerationBackendKind.QNN_NPU,
                    recommendedModelId = com.browntowndev.enclave.domain.model.media.LocalImageGenerationModelId.SD15_QNN,
                    diagnostics = emptyList()
                )

            val parsedCatalog = ModelPackTestFixtures.parsedCatalogWithAllThreeImagePacks()

            modelConfigFetcher.parsedCatalogResult = Result.success(parsedCatalog)

            checkModelsUseCase.result =
                DownloadModelsResult(
                    allModels = emptyMap(),
                    utilityAssets = emptyList(),
                    modelsToDownload = listOf(ModelPackTestFixtures.diffusionAsset()),
                    scanResult = ModelScanResult(emptyList(), emptyMap(), false),
                )

            val result = useCase()

            val downloadShas = result.modelsToDownload.map { it.metadata.sha256 }.toSet()
            val capturedUtilityAssets = checkModelsUseCase.calls.last().utilityAssets
            val utilityTypes = capturedUtilityAssets.map { it.metadata.utilityType }.toSet()
            val utilityShas = capturedUtilityAssets.map { it.metadata.sha256 }.toSet()

            // modelsToDownload contains the sd15-t2i diffusion member.
            assertTrue(ModelPackTestFixtures.DIFFUSION_SHA in downloadShas)
            // utilityAssets contains the SD15_QNN_PACK member.
            assertTrue(UtilityType.SD15_QNN_PACK in utilityTypes)
            // Neither models nor utilities contain any sdxl-t2i or juggernaut-xl-t2i member.
            assertFalse(ModelPackTestFixtures.SDXL_DIFFUSION_SHA in downloadShas)
            assertFalse(UtilityType.SDXL_QNN_PACK in utilityTypes)
            assertFalse(ModelPackTestFixtures.SDXL_QNN_PACK_SHA in utilityShas)
            assertFalse(ModelPackTestFixtures.JUGGERNAUT_DIFFUSION_SHA in downloadShas)
            assertFalse(UtilityType.JUGGERNAUT_XL_QNN_PACK in utilityTypes)
            assertFalse(ModelPackTestFixtures.JUGGERNAUT_QNN_PACK_SHA in utilityShas)
        }

    @Test
    fun `DL-003 Gen3+ startup result includes SD15 SDXL and Juggernaut eligible pack files`() =
        runTest {
            capabilityPort.capabilities =
                com.browntowndev.enclave.domain.model.media.LocalImageGenerationCapabilities(
                    snapdragonTier = SnapdragonTier.GEN3_PLUS,
                    npuAvailable = com.browntowndev.enclave.domain.model.media.LocalImageGenTriState.YES,
                    sd15NpuSupported = com.browntowndev.enclave.domain.model.media.LocalImageGenTriState.YES,
                    sd15CpuSupported = com.browntowndev.enclave.domain.model.media.LocalImageGenTriState.NO,
                    sdxlNpuSupported = com.browntowndev.enclave.domain.model.media.LocalImageGenTriState.YES,
                    juggernautXlNpuSupported = com.browntowndev.enclave.domain.model.media.LocalImageGenTriState.YES,
                    recommendedBackend = com.browntowndev.enclave.domain.model.media.LocalImageGenerationBackendKind.QNN_NPU,
                    recommendedModelId = com.browntowndev.enclave.domain.model.media.LocalImageGenerationModelId.SD15_QNN,
                    diagnostics = emptyList()
                )

            val parsedCatalog = ModelPackTestFixtures.parsedCatalogWithAllThreeImagePacks()

            modelConfigFetcher.parsedCatalogResult = Result.success(parsedCatalog)

            checkModelsUseCase.result =
                DownloadModelsResult(
                    allModels = emptyMap(),
                    utilityAssets = emptyList(),
                    modelsToDownload =
                        listOf(
                            ModelPackTestFixtures.diffusionAsset(),
                            ModelPackTestFixtures.sdxlDiffusionAsset(),
                            ModelPackTestFixtures.juggernautDiffusionAsset(),
                        ),
                    scanResult = ModelScanResult(emptyList(), emptyMap(), false),
                )

            val result = useCase()

            val downloadShas = result.modelsToDownload.map { it.metadata.sha256 }.toSet()
            val utilityTypes = checkModelsUseCase.calls.last().utilityAssets.map { it.metadata.utilityType }.toSet()

            // modelsToDownload contains sd15-t2i, sdxl-t2i, and juggernaut-xl-t2i diffusion members.
            assertTrue(ModelPackTestFixtures.DIFFUSION_SHA in downloadShas)
            assertTrue(ModelPackTestFixtures.SDXL_DIFFUSION_SHA in downloadShas)
            assertTrue(ModelPackTestFixtures.JUGGERNAUT_DIFFUSION_SHA in downloadShas)
            // utilityAssets contains SD15_QNN_PACK, SDXL_QNN_PACK, and JUGGERNAUT_XL_QNN_PACK.
            assertTrue(UtilityType.SD15_QNN_PACK in utilityTypes)
            assertTrue(UtilityType.SDXL_QNN_PACK in utilityTypes)
            assertTrue(UtilityType.JUGGERNAUT_XL_QNN_PACK in utilityTypes)
            assertTrue(capabilityPort.getCapabilitiesCallCount >= 1)
        }

    @Test
    fun `DL-017 diffusion member dropped from the slot map is still scanned for download`() =
        runTest {
            // Gen3+ device: SDXL wins the single IMAGE_GENERATION slot, so the SD15
            // diffusion GGUF must flow through the scan as a pack member asset.
            capabilityPort.capabilities =
                com.browntowndev.enclave.domain.model.media.LocalImageGenerationCapabilities(
                    snapdragonTier = SnapdragonTier.GEN3_PLUS,
                    npuAvailable = com.browntowndev.enclave.domain.model.media.LocalImageGenTriState.YES,
                    sd15NpuSupported = com.browntowndev.enclave.domain.model.media.LocalImageGenTriState.YES,
                    sd15CpuSupported = com.browntowndev.enclave.domain.model.media.LocalImageGenTriState.NO,
                    sdxlNpuSupported = com.browntowndev.enclave.domain.model.media.LocalImageGenTriState.YES,
                    juggernautXlNpuSupported = com.browntowndev.enclave.domain.model.media.LocalImageGenTriState.YES,
                    recommendedBackend = com.browntowndev.enclave.domain.model.media.LocalImageGenerationBackendKind.QNN_NPU,
                    recommendedModelId = com.browntowndev.enclave.domain.model.media.LocalImageGenerationModelId.SD15_QNN,
                    diagnostics = emptyList(),
                )

            val parsedCatalog = ModelPackTestFixtures.parsedCatalogWithAllThreeImagePacks()

            modelConfigFetcher.parsedCatalogResult = Result.success(parsedCatalog)

            checkModelsUseCase.result =
                DownloadModelsResult(
                    allModels = emptyMap(),
                    utilityAssets = emptyList(),
                    modelsToDownload = listOf(ModelPackTestFixtures.diffusionAsset()),
                    scanResult = ModelScanResult(emptyList(), emptyMap(), false),
                )

            useCase()

            val call = checkModelsUseCase.calls.last()
            // SDXL holds the IMAGE_GENERATION slot.
            assertEquals(
                ModelPackTestFixtures.SDXL_DIFFUSION_SHA,
                call.expectedModels[ModelType.IMAGE_GENERATION]?.metadata?.sha256,
            )
            // The losing SD15 diffusion member is still passed to the scan.
            val scannedShas = call.utilityAssets.map { it.metadata.sha256 }.toSet()
            assertTrue(ModelPackTestFixtures.DIFFUSION_SHA in scannedShas)
        }

    @Test
    fun `DL-004 API IMAGE_GENERATION suppresses SD15 and SDXL but not Juggernaut`() =
        runTest {
            capabilityPort.capabilities =
                com.browntowndev.enclave.domain.model.media.LocalImageGenerationCapabilities(
                    snapdragonTier = SnapdragonTier.GEN3_PLUS,
                    npuAvailable = com.browntowndev.enclave.domain.model.media.LocalImageGenTriState.YES,
                    sd15NpuSupported = com.browntowndev.enclave.domain.model.media.LocalImageGenTriState.YES,
                    sd15CpuSupported = com.browntowndev.enclave.domain.model.media.LocalImageGenTriState.NO,
                    sdxlNpuSupported = com.browntowndev.enclave.domain.model.media.LocalImageGenTriState.YES,
                    juggernautXlNpuSupported = com.browntowndev.enclave.domain.model.media.LocalImageGenTriState.YES,
                    recommendedBackend = com.browntowndev.enclave.domain.model.media.LocalImageGenerationBackendKind.QNN_NPU,
                    recommendedModelId = com.browntowndev.enclave.domain.model.media.LocalImageGenerationModelId.SD15_QNN,
                    diagnostics = emptyList(),
                )

            val apiConfig = activeConfig(isLocal = false)
            activeModelProvider.configurationsByType[ModelType.IMAGE_GENERATION] = apiConfig

            val parsedCatalog = ModelPackTestFixtures.parsedCatalogWithAllThreeImagePacks()

            modelConfigFetcher.parsedCatalogResult = Result.success(parsedCatalog)

            checkModelsUseCase.result =
                DownloadModelsResult(
                    allModels = emptyMap(),
                    utilityAssets = emptyList(),
                    modelsToDownload = listOf(ModelPackTestFixtures.juggernautDiffusionAsset()),
                    scanResult = ModelScanResult(emptyList(), emptyMap(), false),
                )

            val result = useCase()

            val call = checkModelsUseCase.calls.last()
            val downloadShas = result.modelsToDownload.map { it.metadata.sha256 }.toSet()
            val utilityTypes = call.utilityAssets.map { it.metadata.utilityType }.toSet()
            val expectedShas = call.expectedModels.values.map { it.metadata.sha256 }.toSet()

            // Startup gating passes only the Juggernaut diffusion slot asset to CheckModelsUseCase.
            assertFalse(ModelPackTestFixtures.DIFFUSION_SHA in expectedShas)
            assertFalse(ModelPackTestFixtures.SDXL_DIFFUSION_SHA in expectedShas)
            assertTrue(ModelPackTestFixtures.JUGGERNAUT_DIFFUSION_SHA in expectedShas)
            assertTrue(ModelPackTestFixtures.JUGGERNAUT_DIFFUSION_SHA in downloadShas)
            // utilityAssets contains no SD15_QNN_PACK or SDXL_QNN_PACK but keeps JUGGERNAUT_XL_QNN_PACK.
            assertFalse(UtilityType.SD15_QNN_PACK in utilityTypes)
            assertFalse(UtilityType.SDXL_QNN_PACK in utilityTypes)
            assertTrue(UtilityType.JUGGERNAUT_XL_QNN_PACK in utilityTypes)
        }

    @Test
    fun `DL-014 API IMAGE_EDIT suppresses Juggernaut but not SD15 or SDXL`() =
        runTest {
            capabilityPort.capabilities =
                com.browntowndev.enclave.domain.model.media.LocalImageGenerationCapabilities(
                    snapdragonTier = SnapdragonTier.GEN3_PLUS,
                    npuAvailable = com.browntowndev.enclave.domain.model.media.LocalImageGenTriState.YES,
                    sd15NpuSupported = com.browntowndev.enclave.domain.model.media.LocalImageGenTriState.YES,
                    sd15CpuSupported = com.browntowndev.enclave.domain.model.media.LocalImageGenTriState.NO,
                    sdxlNpuSupported = com.browntowndev.enclave.domain.model.media.LocalImageGenTriState.YES,
                    juggernautXlNpuSupported = com.browntowndev.enclave.domain.model.media.LocalImageGenTriState.YES,
                    recommendedBackend = com.browntowndev.enclave.domain.model.media.LocalImageGenerationBackendKind.QNN_NPU,
                    recommendedModelId = com.browntowndev.enclave.domain.model.media.LocalImageGenerationModelId.SD15_QNN,
                    diagnostics = emptyList(),
                )

            val apiConfig = activeConfig(isLocal = false)
            activeModelProvider.configurationsByType[ModelType.IMAGE_EDIT] = apiConfig

            val parsedCatalog = ModelPackTestFixtures.parsedCatalogWithAllThreeImagePacks()

            modelConfigFetcher.parsedCatalogResult = Result.success(parsedCatalog)

            checkModelsUseCase.result =
                DownloadModelsResult(
                    allModels = emptyMap(),
                    utilityAssets = emptyList(),
                    modelsToDownload = emptyList(),
                    scanResult = ModelScanResult(emptyList(), emptyMap(), false),
                )

            useCase()

            val call = checkModelsUseCase.calls.last()
            val expectedShas = call.expectedModels.values.map { it.metadata.sha256 }.toSet()
            val utilityTypes = call.utilityAssets.map { it.metadata.utilityType }.toSet()
            val utilityShas = call.utilityAssets.map { it.metadata.sha256 }.toSet()

            assertFalse(ModelPackTestFixtures.JUGGERNAUT_DIFFUSION_SHA in expectedShas)
            assertFalse(UtilityType.JUGGERNAUT_XL_QNN_PACK in utilityTypes)
            assertFalse(ModelPackTestFixtures.JUGGERNAUT_QNN_PACK_SHA in utilityShas)
            // Slot map holds one IMAGE_GENERATION default; both image-gen pack utilities remain eligible.
            assertTrue(ModelPackTestFixtures.SDXL_DIFFUSION_SHA in expectedShas)
            assertTrue(UtilityType.SD15_QNN_PACK in utilityTypes)
            assertTrue(UtilityType.SDXL_QNN_PACK in utilityTypes)
        }

    @Test
    @DisplayName("CAP-008 insufficient RAM startup excludes SD15 and SDXL pack downloads")
    fun cap008_insufficientRamStartupExcludesAllImagePackDownloads() =
        runTest {
            capabilityPort.capabilities =
                com.browntowndev.enclave.domain.model.media.LocalImageGenerationCapabilities(
                    snapdragonTier = SnapdragonTier.GEN3_PLUS,
                    npuAvailable = com.browntowndev.enclave.domain.model.media.LocalImageGenTriState.YES,
                    sd15NpuSupported = com.browntowndev.enclave.domain.model.media.LocalImageGenTriState.NO,
                    sd15CpuSupported = com.browntowndev.enclave.domain.model.media.LocalImageGenTriState.NO,
                    sdxlNpuSupported = com.browntowndev.enclave.domain.model.media.LocalImageGenTriState.NO,
                    recommendedBackend = com.browntowndev.enclave.domain.model.media.LocalImageGenerationBackendKind.NONE,
                    recommendedModelId = null,
                    diagnostics = listOf("Insufficient RAM for local diffusion (need >= 8 GiB)."),
                )

            val parsedCatalog = ModelPackTestFixtures.parsedCatalogWithSdxl()

            modelConfigFetcher.parsedCatalogResult = Result.success(parsedCatalog)

            checkModelsUseCase.result =
                DownloadModelsResult(
                    allModels = emptyMap(),
                    utilityAssets = emptyList(),
                    modelsToDownload = emptyList(),
                    scanResult = ModelScanResult(emptyList(), emptyMap(), false),
                )

            useCase()

            val call = checkModelsUseCase.calls.last()
            val expectedShas = call.expectedModels.values.map { it.metadata.sha256 }.toSet()
            val utilityTypes = call.utilityAssets.map { it.metadata.utilityType }.toSet()
            val utilityShas = call.utilityAssets.map { it.metadata.sha256 }.toSet()

            assertFalse(ModelPackTestFixtures.DIFFUSION_SHA in expectedShas)
            assertFalse(ModelPackTestFixtures.SDXL_DIFFUSION_SHA in expectedShas)
            assertFalse(UtilityType.SD15_QNN_PACK in utilityTypes)
            assertFalse(UtilityType.SDXL_QNN_PACK in utilityTypes)
            assertFalse(ModelPackTestFixtures.SDXL_QNN_PACK_SHA in utilityShas)
        }

    @Test
    @DisplayName("DL-015 Gen2 on-disk Juggernaut is not registry-synced at init")
    fun dl015_gen2OnDiskJuggernautIsNotRegistrySyncedAtInit() =
        runTest {
            capabilityPort.capabilities =
                com.browntowndev.enclave.domain.model.media.LocalImageGenerationCapabilities(
                    snapdragonTier = SnapdragonTier.GEN2,
                    npuAvailable = com.browntowndev.enclave.domain.model.media.LocalImageGenTriState.YES,
                    sd15NpuSupported = com.browntowndev.enclave.domain.model.media.LocalImageGenTriState.YES,
                    sd15CpuSupported = com.browntowndev.enclave.domain.model.media.LocalImageGenTriState.NO,
                    sdxlNpuSupported = com.browntowndev.enclave.domain.model.media.LocalImageGenTriState.NO,
                    juggernautXlNpuSupported = com.browntowndev.enclave.domain.model.media.LocalImageGenTriState.NO,
                    recommendedBackend = com.browntowndev.enclave.domain.model.media.LocalImageGenerationBackendKind.QNN_NPU,
                    recommendedModelId = com.browntowndev.enclave.domain.model.media.LocalImageGenerationModelId.SD15_QNN,
                    diagnostics = emptyList(),
                )

            val parsedCatalog = ModelPackTestFixtures.parsedCatalogWithAllThreeImagePacks()

            modelConfigFetcher.parsedCatalogResult = Result.success(parsedCatalog)
            checkModelsUseCase.result =
                DownloadModelsResult(
                    allModels = emptyMap(),
                    utilityAssets = emptyList(),
                    modelsToDownload = emptyList(),
                    scanResult = ModelScanResult(emptyList(), emptyMap(), false),
                )

            useCase()

            assertTrue(
                syncLocalModelRegistryUseCase.calls.none {
                    it.modelType == ModelType.IMAGE_EDIT &&
                        it.asset.metadata.sha256 == ModelPackTestFixtures.JUGGERNAUT_DIFFUSION_SHA
                },
            )
            assertTrue(
                syncLocalModelRegistryUseCase.calls.none {
                    it.modelType == ModelType.IMAGE_GENERATION &&
                        it.asset.metadata.sha256 == ModelPackTestFixtures.JUGGERNAUT_DIFFUSION_SHA
                },
            )
        }
}
