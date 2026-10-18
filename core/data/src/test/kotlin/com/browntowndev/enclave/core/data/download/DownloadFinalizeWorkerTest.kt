package com.browntowndev.enclave.core.data.download

import android.app.NotificationManager
import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.workDataOf
import com.browntowndev.enclave.core.data.sync.EmbeddingRegenerationCoordinator
import com.browntowndev.enclave.core.data.sync.realWorkerParameters
import com.browntowndev.enclave.core.testing.FakeDefaultModelRepository
import com.browntowndev.enclave.core.testing.FakeLocalImageGenerationCapability
import com.browntowndev.enclave.core.testing.FakeLocalModelRepository
import com.browntowndev.enclave.core.testing.FakeLoggingPort
import com.browntowndev.enclave.core.testing.FakeModelConfigFetcher
import com.browntowndev.enclave.core.testing.FakeQnnPackInstaller
import com.browntowndev.enclave.core.testing.FakeSnapdragonTier
import com.browntowndev.enclave.core.testing.FakeTransactionProvider
import com.browntowndev.enclave.domain.model.config.ApiModelConfigurationId
import com.browntowndev.enclave.domain.model.config.DefaultModelAssignment
import com.browntowndev.enclave.domain.model.config.LocalModelAsset
import com.browntowndev.enclave.domain.model.config.LocalModelConfiguration
import com.browntowndev.enclave.domain.model.config.LocalModelConfigurationId
import com.browntowndev.enclave.domain.model.config.LocalModelId
import com.browntowndev.enclave.domain.model.config.LiveVoiceProviderId
import com.browntowndev.enclave.domain.model.config.LocalModelMetadata
import com.browntowndev.enclave.domain.model.config.MediaProviderId
import com.browntowndev.enclave.domain.model.config.SlotResolvedLocalModel
import com.browntowndev.enclave.domain.model.config.TtsProviderId
import com.browntowndev.enclave.domain.model.config.UtilityType
import com.browntowndev.enclave.domain.model.download.ModelPack
import com.browntowndev.enclave.domain.model.download.ModelPackVerificationDepth
import com.browntowndev.enclave.domain.model.inference.ModelFileFormat
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.model.media.LocalImageGenerationPaths
import com.browntowndev.enclave.domain.port.download.HashingPort
import com.browntowndev.enclave.domain.usecase.FakeUtilityModelRepository
import com.browntowndev.enclave.domain.usecase.byok.SetDefaultModelUseCase
import com.browntowndev.enclave.domain.usecase.download.ResolveSd15QnnPackCatalogAssetUseCase
import com.browntowndev.enclave.domain.usecase.modelconfig.SyncLocalModelRegistryUseCase
import com.browntowndev.enclave.domain.usecase.modelconfig.SyncUtilityModelRegistryUseCase
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension
import java.io.File

/**
 * TDD Red Phase Tests for DownloadFinalizeWorker.
 *
 * These tests verify the NEW expected behavior for DownloadFinalizeWorker,
 * which is the background-owned owner of post-download business logic.
 *
 * The DownloadFinalizeWorker class does NOT exist yet.
 * These tests will fail at compile time until the worker class is created.
 *
 * EXPECTED: Tests FAIL (worker doesn't exist).
 * EXPECTED: Tests PASS after worker is implemented.
 *
 * ENC-28: hand-written fakes throughout. The worker is rebuilt per test with real
 * [androidx.work.WorkerParameters] carrying the test's input Data (the old
 * `setInputData` re-stub). [SyncLocalModelRegistryUseCase] is the real open use
 * case subclassed to record calls; [SetDefaultModelUseCase] is an interface,
 * faked by recording. The embedding coordinator is real — its pending
 * full-regeneration flag replaces the old call verification.
 */
@ExtendWith(RobolectricExtension::class)
@Config(sdk = [34])
class DownloadFinalizeWorkerTest {
    companion object {
        const val KEY_SESSION_ID = "work_session_id"
        const val KEY_REQUEST_KIND = "request_kind"
        const val KEY_TARGET_MODEL_ID = "target_model_id"
        const val KEY_DOWNLOADED_SHAS = "downloaded_shas"
        const val KEY_WORKER_STAGE = "worker_stage"
        const val KEY_ERROR_MESSAGE = "error_message"

        const val STAGE_FINALIZE = "FINALIZE"

        const val REQUEST_INITIALIZE = "INITIALIZE_MODELS"
        const val REQUEST_RESTORE = "RESTORE_SOFT_DELETED_MODEL"

        const val SESSION_ID = "test-session-123"
        const val MODEL_ID = "test-model-id"
        const val SHA_1 = "sha256-downloaded-1"
        const val SHA_2 = "sha256-downloaded-2"
        const val SHA_ONNX = "sha256-onnx"
    }

    /** Records every invoke() call; returns the same SlotResolvedLocalModel the old stubs produced. */
    private class RecordingSyncLocalModelRegistryUseCase(
        localModelRepository: FakeLocalModelRepository,
        defaultModelRepository: FakeDefaultModelRepository,
        logger: FakeLoggingPort,
    ) : SyncLocalModelRegistryUseCase(
            localModelRepository,
            defaultModelRepository,
            FakeTransactionProvider(),
            logger,
        ) {
        data class Call(
            val modelType: ModelType,
            val asset: LocalModelAsset,
            val assignAsDefault: Boolean,
        )

        val calls = mutableListOf<Call>()

        /** Optional side-effect hook (default-tracking scenarios). */
        var onInvoke: ((ModelType, LocalModelAsset) -> Unit)? = null

        override suspend fun invoke(
            modelType: ModelType,
            asset: LocalModelAsset,
            assignAsDefault: Boolean,
        ): SlotResolvedLocalModel {
            calls += Call(modelType, asset, assignAsDefault)
            onInvoke?.invoke(modelType, asset)
            val config =
                asset.configurations.firstOrNull()
                    ?: LocalModelConfiguration(
                        id = LocalModelConfigurationId("test-config"),
                        localModelId = asset.metadata.id,
                        displayName = "Test",
                        temperature = 0.7,
                        topK = 40,
                        topP = 0.95,
                        minP = 0.0,
                        repetitionPenalty = 1.1,
                        maxTokens = 4096,
                        contextWindow = 4096,
                        systemPrompt = "",
                        isSystemPreset = true,
                        thinkingEnabled = false,
                        defaultAssignments = listOf(modelType),
                    )
            return SlotResolvedLocalModel(modelType, asset, config)
        }
    }

    /** Recording fake for the [SetDefaultModelUseCase] interface. */
    private class RecordingSetDefaultModelUseCase : SetDefaultModelUseCase {
        data class Call(
            val modelType: ModelType,
            val localConfigId: LocalModelConfigurationId?,
        )

        val calls = mutableListOf<Call>()

        override suspend fun invoke(
            modelType: ModelType,
            localConfigId: LocalModelConfigurationId?,
            apiConfigId: ApiModelConfigurationId?,
            ttsProviderId: TtsProviderId?,
            mediaProviderId: MediaProviderId?,
            liveVoiceProviderId: LiveVoiceProviderId?,
        ) {
            calls += Call(modelType, localConfigId)
        }
    }

    private class FakeHashingPort : HashingPort {
        override fun calculateMd5(filePath: String): String = ""

        override fun calculateSha256(filePath: String): String = ""
    }

    private lateinit var context: Context
    private lateinit var localModelRepository: FakeLocalModelRepository
    private lateinit var modelConfigFetcher: FakeModelConfigFetcher
    private lateinit var syncLocalModelRegistryUseCase: RecordingSyncLocalModelRegistryUseCase
    private lateinit var setDefaultModelUseCase: RecordingSetDefaultModelUseCase
    private lateinit var defaultModelRepository: FakeDefaultModelRepository
    private lateinit var syncUtilityModelRegistryUseCase: SyncUtilityModelRegistryUseCase
    private lateinit var fakeUtilityModelRepository: FakeUtilityModelRepository
    private lateinit var embeddingCoordinator: EmbeddingRegenerationCoordinator
    private lateinit var coordinatorScope: TestScope
    private lateinit var loggingPort: FakeLoggingPort
    private lateinit var qnnPackInstaller: FakeQnnPackInstaller

    /** When set, replaces the Context's external files dir (DL-007 stages a pack under @TempDir). */
    private var externalFilesDirOverride: File? = null

    // Field injection, not a test-method parameter: the Apter Robolectric
    // extension builds a JUnit 4 RobolectricTestRunner for the class, whose
    // validation rejects @Test methods with parameters ("should have no
    // parameters"), which silently killed this whole class at discovery.
    @TempDir
    lateinit var tempDir: File

    private lateinit var worker: DownloadFinalizeWorker

    @BeforeEach
    fun setup() {
        val appContext = ApplicationProvider.getApplicationContext<Context>()
        context =
            object : ContextWrapper(appContext) {
                override fun getExternalFilesDir(type: String?): File? = externalFilesDirOverride ?: super.getExternalFilesDir(type)
            }
        localModelRepository = FakeLocalModelRepository()
        modelConfigFetcher = FakeModelConfigFetcher()
        loggingPort = FakeLoggingPort()
        defaultModelRepository = FakeDefaultModelRepository()
        syncLocalModelRegistryUseCase =
            RecordingSyncLocalModelRegistryUseCase(localModelRepository, defaultModelRepository, loggingPort)
        setDefaultModelUseCase = RecordingSetDefaultModelUseCase()
        fakeUtilityModelRepository = FakeUtilityModelRepository()
        syncUtilityModelRegistryUseCase =
            SyncUtilityModelRegistryUseCase(fakeUtilityModelRepository, loggingPort)
        qnnPackInstaller = FakeQnnPackInstaller().apply { installResult = true }

        androidx.work.testing.WorkManagerTestInitHelper.initializeTestWorkManager(context)
        val workManager =
            androidx.work.WorkManager
                .getInstance(context)
        coordinatorScope = TestScope()
        embeddingCoordinator =
            EmbeddingRegenerationCoordinator(
                workManager = workManager,
                externalScope = coordinatorScope,
            )

        // Create the worker with all dependencies (empty input; tests set their own)
        setInputData(Data.EMPTY)
    }

    /**
     * Helper to set input data before running worker.
     * Rebuilds the worker with real WorkerParameters carrying [data].
     */
    private fun setInputData(data: Data) {
        worker =
            DownloadFinalizeWorker(
                context = context,
                workerParams = realWorkerParameters(inputData = data),
                localModelRepository = localModelRepository,
                modelConfigFetcher = modelConfigFetcher,
                syncLocalModelRegistryUseCase = syncLocalModelRegistryUseCase,
                syncUtilityModelRegistryUseCase = syncUtilityModelRegistryUseCase,
                setDefaultModelUseCase = setDefaultModelUseCase,
                defaultModelRepository = defaultModelRepository,
                qnnPackInstaller = qnnPackInstaller,
                resolveSd15QnnPackCatalogAssetUseCase =
                    ResolveSd15QnnPackCatalogAssetUseCase(FakeSnapdragonTier()),
                embeddingCoordinator = embeddingCoordinator,
                notificationManager =
                    DownloadNotificationManager(
                        context,
                        context.getSystemService(NotificationManager::class.java),
                    ),
                logger = loggingPort,
            )
    }

    /** Settles the coordinator scope and reports whether a full regeneration is pending. */
    private suspend fun fullRegenerationPending(): Boolean {
        coordinatorScope.testScheduler.advanceUntilIdle()
        return embeddingCoordinator.consumePending().fullScanPending
    }

    // ===== 3.2 Common Parsing and Guard Tests =====

    /**
     * Scenario: triggers embedding regeneration when onnx model is finalized
     * Given requestKind = INITIALIZE_MODELS
     * And downloaded_shas contains SHA for an ONNX model
     * When the finalizer runs
     * Then embeddingCoordinator.enqueueFullRegeneration() is called
     */
    @Test
    fun `triggers embedding regeneration when onnx model is finalized`() =
        runTest {
            // Given: INITIALIZE_MODELS with ONNX model SHA
            val downloadedShas = Json.encodeToString(listOf(SHA_ONNX))
            val inputData =
                workDataOf(
                    KEY_SESSION_ID to SESSION_ID,
                    KEY_REQUEST_KIND to REQUEST_INITIALIZE,
                    KEY_DOWNLOADED_SHAS to downloadedShas,
                )
            setInputData(inputData)

            val onnxAsset =
                createTestAsset("model.onnx", SHA_ONNX).let { asset ->
                    asset.copy(metadata = asset.metadata.copy(utilityType = UtilityType.ONNX_MODEL))
                }
            modelConfigFetcher.fetchRemoteConfigResult = Result.success(listOf(onnxAsset))

            // When: Run the worker
            worker.doWork()

            // Then: Embedding regeneration should be triggered
            assertTrue(fullRegenerationPending())
        }

    /**
     * Scenario: triggers embedding regeneration when onnx vocab is finalized
     * Given requestKind = INITIALIZE_MODELS
     * And downloaded_shas contains SHA for an ONNX vocab
     * When the finalizer runs
     * Then embeddingCoordinator.enqueueFullRegeneration() is called
     */
    @Test
    fun `triggers embedding regeneration when onnx vocab is finalized`() =
        runTest {
            // Given: INITIALIZE_MODELS with ONNX vocab SHA
            val downloadedShas = Json.encodeToString(listOf(SHA_ONNX))
            val inputData =
                workDataOf(
                    KEY_SESSION_ID to SESSION_ID,
                    KEY_REQUEST_KIND to REQUEST_INITIALIZE,
                    KEY_DOWNLOADED_SHAS to downloadedShas,
                )
            setInputData(inputData)

            val onnxVocabAsset =
                createTestAsset("vocab.txt", SHA_ONNX).let { asset ->
                    asset.copy(metadata = asset.metadata.copy(utilityType = UtilityType.ONNX_VOCAB))
                }
            modelConfigFetcher.fetchRemoteConfigResult = Result.success(listOf(onnxVocabAsset))

            // When: Run the worker
            worker.doWork()

            // Then: Embedding regeneration should be triggered
            assertTrue(fullRegenerationPending())
        }

    /**
     * Scenario: does not trigger embedding regeneration when no onnx assets are finalized
     * Given requestKind = INITIALIZE_MODELS
     * And downloaded_shas does not contain ONNX assets
     * When the finalizer runs
     * Then embeddingCoordinator.enqueueFullRegeneration() is NOT called
     */
    @Test
    fun `does not trigger embedding regeneration when no onnx assets are finalized`() =
        runTest {
            // Given: INITIALIZE_MODELS with standard model SHA
            val downloadedShas = Json.encodeToString(listOf(SHA_1))
            val inputData =
                workDataOf(
                    KEY_SESSION_ID to SESSION_ID,
                    KEY_REQUEST_KIND to REQUEST_INITIALIZE,
                    KEY_DOWNLOADED_SHAS to downloadedShas,
                )
            setInputData(inputData)

            val standardAsset = createTestAsset("model1.gguf", SHA_1)
            modelConfigFetcher.fetchRemoteConfigResult = Result.success(listOf(standardAsset))

            // When: Run the worker
            worker.doWork()

            // Then: Embedding regeneration should NOT be triggered
            assertFalse(fullRegenerationPending())
        }

    /**
     * Scenario: triggers embedding regeneration when onnx model is restored
     * Given requestKind = RESTORE_SOFT_DELETED_MODEL
     * And the restored asset is an ONNX model
     * When the finalizer runs
     * Then embeddingCoordinator.enqueueFullRegeneration() is called
     */
    @Test
    fun `triggers embedding regeneration when onnx model is restored`() =
        runTest {
            // Given: RESTORE_SOFT_DELETED_MODEL with ONNX model
            val downloadedShas = Json.encodeToString(listOf(SHA_ONNX))
            val inputData =
                workDataOf(
                    KEY_SESSION_ID to SESSION_ID,
                    KEY_REQUEST_KIND to REQUEST_RESTORE,
                    KEY_TARGET_MODEL_ID to MODEL_ID,
                    KEY_DOWNLOADED_SHAS to downloadedShas,
                )
            setInputData(inputData)

            val onnxAsset =
                createTestAsset("model.onnx", SHA_ONNX).let { asset ->
                    asset.copy(metadata = asset.metadata.copy(utilityType = UtilityType.ONNX_MODEL))
                }
            localModelRepository.registerSoftDeletedModel(LocalModelId(MODEL_ID), onnxAsset)
            modelConfigFetcher.fetchRemoteConfigResult = Result.success(listOf(onnxAsset))

            // When: Run the worker
            worker.doWork()

            // Then: Embedding regeneration should be triggered
            assertTrue(fullRegenerationPending())
        }

    /**
     * Scenario: fails when requestKind is missing
     * Given input without requestKind
     * When doWork() runs
     * Then the worker returns terminal failure
     */
    @Test
    fun `fails when requestKind is missing`() =
        runTest {
            // Given: Input without requestKind
            val inputData =
                workDataOf(
                    KEY_SESSION_ID to SESSION_ID,
                    // Missing KEY_REQUEST_KIND
                )
            setInputData(inputData)

            // When: Run the worker
            val result = worker.doWork()

            // Then: Should return terminal failure
            assertTrue(result is ListenableWorker.Result.Failure)
        }

    /**
     * Scenario: fails when sessionId is missing
     * Given input without sessionId
     * When doWork() runs
     * Then the worker returns terminal failure
     */
    @Test
    fun `fails when sessionId is missing`() =
        runTest {
            // Given: Input without sessionId
            val inputData =
                workDataOf(
                    KEY_REQUEST_KIND to REQUEST_INITIALIZE,
                    // Missing KEY_SESSION_ID
                )
            setInputData(inputData)

            // When: Run the worker
            val result = worker.doWork()

            // Then: Should return terminal failure
            assertTrue(result is ListenableWorker.Result.Failure)
        }

    /**
     * Scenario: success output includes finalizer stage
     * Given finalization succeeds
     * When doWork() returns
     * Then output includes workerStage = FINALIZE
     */
    @Test
    fun `success output includes finalizer stage`() =
        runTest {
            // Given: Valid startup finalization input
            val downloadedShas = Json.encodeToString(listOf(SHA_1))
            val inputData =
                workDataOf(
                    KEY_SESSION_ID to SESSION_ID,
                    KEY_REQUEST_KIND to REQUEST_INITIALIZE,
                    KEY_DOWNLOADED_SHAS to downloadedShas,
                )
            setInputData(inputData)

            // Remote config with matching SHA
            val remoteAsset = createTestAsset("model1.gguf", SHA_1)
            modelConfigFetcher.fetchRemoteConfigResult =
                Result.success(
                    listOf(remoteAsset),
                )

            // When: Run the worker
            val result = worker.doWork()

            // Then: Success output includes finalizer stage
            assertTrue(result is ListenableWorker.Result.Success)
            val outputData = (result as ListenableWorker.Result.Success).outputData
            assertEquals(STAGE_FINALIZE, outputData.getString(KEY_WORKER_STAGE))
        }

    @Test
    fun `SCN-FINALIZE-001 download finalize persists asset displayName from catalog`() =
        runTest {
            val gemmaSha = "gemma-sha"
            val downloadedShas = Json.encodeToString(listOf(gemmaSha))
            val inputData =
                workDataOf(
                    KEY_SESSION_ID to SESSION_ID,
                    KEY_REQUEST_KIND to REQUEST_INITIALIZE,
                    KEY_DOWNLOADED_SHAS to downloadedShas,
                )
            setInputData(inputData)

            val remoteAsset =
                createTestAsset("gemma-4-E4B-it.litertlm", gemmaSha).copy(
                    metadata =
                        createTestAsset("gemma-4-E4B-it.litertlm", gemmaSha).metadata.copy(
                            displayName = "Gemma 4 E4B",
                        ),
                )
            modelConfigFetcher.fetchRemoteConfigResult = Result.success(listOf(remoteAsset))

            worker.doWork()

            assertEquals("Gemma 4 E4B", syncLocalModelRegistryUseCase.calls.single().asset.metadata.displayName)
        }

    // ===== 3.3 Startup Finalization Tests =====

    /**
     * Scenario: startup finalizer activates downloaded slots by SHA
     * Given requestKind = INITIALIZE_MODELS
     * And downloaded_shas contains SHA A and SHA B
     * And remote config contains slots whose assets match A and B
     * When the finalizer runs
     * Then SyncLocalModelRegistryUseCase is invoked for those slots only
     */
    @Test
    fun `startup finalizer activates downloaded slots by SHA`() =
        runTest {
            // Given: Startup finalization input
            val downloadedShas = Json.encodeToString(listOf(SHA_1, SHA_2))
            val inputData =
                workDataOf(
                    KEY_SESSION_ID to SESSION_ID,
                    KEY_REQUEST_KIND to REQUEST_INITIALIZE,
                    KEY_DOWNLOADED_SHAS to downloadedShas,
                )
            setInputData(inputData)

            // Remote config with matching SHAs
            val remoteAsset1 = createTestAsset("model1.gguf", SHA_1)
            val remoteAsset2 =
                createTestAsset("model2.gguf", SHA_2).copy(
                    configurations =
                        listOf(
                            LocalModelConfiguration(
                                localModelId = LocalModelId("test-id"),
                                displayName = "Test",
                                temperature = 0.7,
                                topK = 40,
                                topP = 0.95,
                                minP = 0.0,
                                repetitionPenalty = 1.1,
                                maxTokens = 4096,
                                contextWindow = 4096,
                                systemPrompt = "",
                                isSystemPreset = true,
                                thinkingEnabled = false,
                                defaultAssignments = listOf(ModelType.VISION),
                            ),
                        ),
                )
            val remoteConfig = listOf(remoteAsset1, remoteAsset2)

            modelConfigFetcher.fetchRemoteConfigResult = Result.success(remoteConfig)

            // When: Run the worker
            worker.doWork()

            // Then: SyncLocalModelRegistryUseCase should be called for matching slots
            assertTrue(
                syncLocalModelRegistryUseCase.calls.contains(
                    RecordingSyncLocalModelRegistryUseCase.Call(ModelType.MAIN, remoteAsset1, assignAsDefault = false),
                ),
            )
            assertTrue(
                syncLocalModelRegistryUseCase.calls.contains(
                    RecordingSyncLocalModelRegistryUseCase.Call(ModelType.VISION, remoteAsset2, assignAsDefault = false),
                ),
            )
        }

    /**
     * Scenario: startup finalizer ignores remote assets not downloaded in this session
     * Given remote config includes assets A, B, and C
     * And downloaded_shas contains only A
     * When the finalizer runs
     * Then only A-backed slots are activated
     */
    @Test
    fun `startup finalizer ignores remote assets not downloaded in this session`() =
        runTest {
            // Given: Only SHA_1 was downloaded, but remote config has SHA_1, SHA_2, SHA_3
            val downloadedShas = Json.encodeToString(listOf(SHA_1))
            val inputData =
                workDataOf(
                    KEY_SESSION_ID to SESSION_ID,
                    KEY_REQUEST_KIND to REQUEST_INITIALIZE,
                    KEY_DOWNLOADED_SHAS to downloadedShas,
                )
            setInputData(inputData)

            val remoteAsset1 = createTestAsset("model1.gguf", SHA_1)
            val remoteAsset2 =
                createTestAsset("model2.gguf", SHA_2).copy(
                    configurations =
                        listOf(
                            LocalModelConfiguration(
                                localModelId = LocalModelId("test-id"),
                                displayName = "Test",
                                temperature = 0.7,
                                topK = 40,
                                topP = 0.95,
                                minP = 0.0,
                                repetitionPenalty = 1.1,
                                maxTokens = 4096,
                                contextWindow = 4096,
                                systemPrompt = "",
                                isSystemPreset = true,
                                thinkingEnabled = false,
                                defaultAssignments = listOf(ModelType.VISION),
                            ),
                        ),
                )
            val remoteAsset3 =
                createTestAsset("model3.gguf", "sha-not-downloaded").copy(
                    configurations =
                        listOf(
                            LocalModelConfiguration(
                                localModelId = LocalModelId("test-id"),
                                displayName = "Test",
                                temperature = 0.7,
                                topK = 40,
                                topP = 0.95,
                                minP = 0.0,
                                repetitionPenalty = 1.1,
                                maxTokens = 4096,
                                contextWindow = 4096,
                                systemPrompt = "",
                                isSystemPreset = true,
                                thinkingEnabled = false,
                                defaultAssignments = listOf(ModelType.DRAFT_ONE),
                            ),
                        ),
                )

            modelConfigFetcher.fetchRemoteConfigResult =
                Result.success(
                    listOf(remoteAsset1, remoteAsset2, remoteAsset3),
                )

            // When: Run the worker
            worker.doWork()

            // Then: Only SHA_1 asset should be synced
            assertTrue(
                syncLocalModelRegistryUseCase.calls.contains(
                    RecordingSyncLocalModelRegistryUseCase.Call(ModelType.MAIN, remoteAsset1, assignAsDefault = false),
                ),
            )
            // SHA_2 and SHA_3 should NOT be synced (not downloaded)
            assertFalse(
                syncLocalModelRegistryUseCase.calls.contains(
                    RecordingSyncLocalModelRegistryUseCase.Call(ModelType.VISION, remoteAsset2, assignAsDefault = false),
                ),
            )
            assertFalse(
                syncLocalModelRegistryUseCase.calls.contains(
                    RecordingSyncLocalModelRegistryUseCase.Call(ModelType.DRAFT_ONE, remoteAsset3, assignAsDefault = false),
                ),
            )
        }

    /**
     * Scenario: startup finalizer is idempotent under retry
     * Given the same startup finalizer input is executed twice
     * When both runs complete
     * Then registry state converges without duplicates
     */
    @Test
    fun `startup finalizer is idempotent under retry`() =
        runTest {
            // Given: Valid startup finalization input
            val downloadedShas = Json.encodeToString(listOf(SHA_1))
            val inputData =
                workDataOf(
                    KEY_SESSION_ID to SESSION_ID,
                    KEY_REQUEST_KIND to REQUEST_INITIALIZE,
                    KEY_DOWNLOADED_SHAS to downloadedShas,
                )
            setInputData(inputData)

            val remoteAsset = createTestAsset("model1.gguf", SHA_1)
            modelConfigFetcher.fetchRemoteConfigResult =
                Result.success(
                    listOf(remoteAsset),
                )

            // When: Run the worker twice (simulating retry)
            worker.doWork()
            worker.doWork()

            // Then: Both runs succeed (idempotent behavior)
            // The use case should be called twice but not fail
            assertEquals(
                2,
                syncLocalModelRegistryUseCase.calls.count {
                    it == RecordingSyncLocalModelRegistryUseCase.Call(ModelType.MAIN, remoteAsset, assignAsDefault = false)
                },
            )
        }

    // ===== 3.4 Soft-Deleted Restore Finalization Tests =====

    /**
     * Scenario: restore finalizer requires target model id
     * Given requestKind = RESTORE_SOFT_DELETED_MODEL
     * And targetModelId is null
     * When the finalizer runs
     * Then it returns terminal failure
     */
    @Test
    fun `restore finalizer requires target model id`() =
        runTest {
            // Given: Restore input WITHOUT targetModelId (invalid)
            val downloadedShas = Json.encodeToString(listOf(SHA_1))
            val inputData =
                workDataOf(
                    KEY_SESSION_ID to SESSION_ID,
                    KEY_REQUEST_KIND to REQUEST_RESTORE,
                    KEY_DOWNLOADED_SHAS to downloadedShas,
                    // Missing KEY_TARGET_MODEL_ID
                )
            setInputData(inputData)

            // When: Run the worker
            val result = worker.doWork()

            // Then: Should return failure
            assertTrue(
                result is ListenableWorker.Result.Failure,
                "Worker should return failure when targetModelId is missing for restore request",
            )
        }

    /**
     * Scenario: restore finalizer restores configs only after successful download
     * Given a soft-deleted asset exists in the repository
     * And remote config contains a SHA-matching asset
     * When the finalizer runs
     * Then restoreSoftDeletedModel(modelId, rebuiltConfigs) is called
     */
    @Test
    fun `restore finalizer restores configs only after successful download`() =
        runTest {
            // Given: Restore finalization input
            val downloadedShas = Json.encodeToString(listOf(SHA_1))
            val inputData =
                workDataOf(
                    KEY_SESSION_ID to SESSION_ID,
                    KEY_REQUEST_KIND to REQUEST_RESTORE,
                    KEY_TARGET_MODEL_ID to MODEL_ID,
                    KEY_DOWNLOADED_SHAS to downloadedShas,
                )
            setInputData(inputData)

            // Soft-deleted asset exists in repository
            val softDeletedAsset = createTestAsset("model.gguf", SHA_1)
            localModelRepository.registerSoftDeletedModel(LocalModelId(MODEL_ID), softDeletedAsset)

            // Remote config has matching SHA
            val remoteAsset =
                softDeletedAsset.copy(
                    configurations =
                        listOf(
                            LocalModelConfiguration(
                                localModelId = LocalModelId(MODEL_ID),
                                displayName = "Restored Preset",
                                temperature = 0.7,
                                topK = 40,
                                topP = 0.95,
                                minP = 0.0,
                                repetitionPenalty = 1.1,
                                maxTokens = 4096,
                                contextWindow = 4096,
                                systemPrompt = "Test",
                                isSystemPreset = true,
                                thinkingEnabled = false,
                                defaultAssignments = listOf(ModelType.MAIN),
                            ),
                        ),
                )
            modelConfigFetcher.fetchRemoteConfigResult =
                Result.success(
                    listOf(remoteAsset),
                )

            // When: Run the worker
            worker.doWork()

            // Then: restoreSoftDeletedModel should be called
            val restoreCall = localModelRepository.restoreCalls.single()
            assertEquals(LocalModelId(MODEL_ID), restoreCall.first)
            val configs = restoreCall.second
            assertTrue(
                configs.isNotEmpty() &&
                    configs.all { it.isSystemPreset && it.localModelId.value == MODEL_ID },
            )
        }

    /**
     * Scenario: restore finalizer rebuilds system presets from remote config
     * Given remote config has multiple configurations for the matching asset
     * When the finalizer runs
     * Then the restored config list uses:
     * - localModelId = targetModelId
     * - isSystemPreset = true
     */
    @Test
    fun `restore finalizer rebuilds system presets from remote config`() =
        runTest {
            // Given: Restore finalization input
            val downloadedShas = Json.encodeToString(listOf(SHA_1))
            val inputData =
                workDataOf(
                    KEY_SESSION_ID to SESSION_ID,
                    KEY_REQUEST_KIND to REQUEST_RESTORE,
                    KEY_TARGET_MODEL_ID to MODEL_ID,
                    KEY_DOWNLOADED_SHAS to downloadedShas,
                )
            setInputData(inputData)

            val softDeletedAsset = createTestAsset("model.gguf", SHA_1)
            localModelRepository.registerSoftDeletedModel(LocalModelId(MODEL_ID), softDeletedAsset)

            // Remote config with multiple presets
            val remoteAsset =
                softDeletedAsset.copy(
                    configurations =
                        listOf(
                            LocalModelConfiguration(
                                localModelId = LocalModelId("original-id"),
                                displayName = "Preset 1",
                                temperature = 0.7,
                                topK = 40,
                                topP = 0.95,
                                minP = 0.0,
                                repetitionPenalty = 1.1,
                                maxTokens = 4096,
                                contextWindow = 4096,
                                systemPrompt = "Test",
                                isSystemPreset = true,
                                thinkingEnabled = false,
                                defaultAssignments = listOf(ModelType.MAIN),
                            ),
                            LocalModelConfiguration(
                                localModelId = LocalModelId("original-id-2"),
                                displayName = "Preset 2",
                                temperature = 0.8,
                                topK = 50,
                                topP = 0.9,
                                minP = 0.0,
                                repetitionPenalty = 1.0,
                                maxTokens = 2048,
                                contextWindow = 2048,
                                systemPrompt = "Test 2",
                                isSystemPreset = true,
                                thinkingEnabled = false,
                                defaultAssignments = listOf(ModelType.MAIN),
                            ),
                        ),
                )
            modelConfigFetcher.fetchRemoteConfigResult =
                Result.success(
                    listOf(remoteAsset),
                )

            // When: Run the worker
            worker.doWork()

            // Then: Restored configs should have targetModelId and isSystemPreset=true
            val restoreCall = localModelRepository.restoreCalls.single()
            assertEquals(LocalModelId(MODEL_ID), restoreCall.first)
            val configs = restoreCall.second
            assertTrue(
                configs.size == 2 &&
                    configs.all { it.isSystemPreset && it.localModelId.value == MODEL_ID },
            )
        }

    /**
     * Scenario: restore finalizer does not call SyncLocalModelRegistryUseCase
     * Given restore finalization succeeds
     * When the finalizer runs
     * Then role-slot assignment is not performed
     */
    @Test
    fun `restore finalizer does not call SyncLocalModelRegistryUseCase`() =
        runTest {
            // Given: Restore finalization input
            val downloadedShas = Json.encodeToString(listOf(SHA_1))
            val inputData =
                workDataOf(
                    KEY_SESSION_ID to SESSION_ID,
                    KEY_REQUEST_KIND to REQUEST_RESTORE,
                    KEY_TARGET_MODEL_ID to MODEL_ID,
                    KEY_DOWNLOADED_SHAS to downloadedShas,
                )
            setInputData(inputData)

            val softDeletedAsset = createTestAsset("model.gguf", SHA_1)
            localModelRepository.registerSoftDeletedModel(LocalModelId(MODEL_ID), softDeletedAsset)

            val remoteAsset =
                softDeletedAsset.copy(
                    configurations =
                        listOf(
                            LocalModelConfiguration(
                                localModelId = LocalModelId(MODEL_ID),
                                displayName = "Restored",
                                temperature = 0.7,
                                topK = 40,
                                topP = 0.95,
                                minP = 0.0,
                                repetitionPenalty = 1.1,
                                maxTokens = 4096,
                                contextWindow = 4096,
                                systemPrompt = "",
                                isSystemPreset = true,
                                thinkingEnabled = false,
                                defaultAssignments = listOf(ModelType.MAIN),
                            ),
                        ),
                )
            modelConfigFetcher.fetchRemoteConfigResult =
                Result.success(
                    listOf(remoteAsset),
                )

            // When: Run the worker
            worker.doWork()

            // Then: SyncLocalModelRegistryUseCase should NOT be called for restore
            assertTrue(syncLocalModelRegistryUseCase.calls.isEmpty())
        }

    /**
     * Scenario: restore finalizer fails if soft-deleted asset no longer exists
     * Given getAssetById(targetModelId) returns null
     * When the finalizer runs
     * Then it returns terminal failure
     */
    @Test
    fun `restore finalizer fails if soft-deleted asset no longer exists`() =
        runTest {
            // Given: Restore input but asset not found
            val downloadedShas = Json.encodeToString(listOf(SHA_1))
            val inputData =
                workDataOf(
                    KEY_SESSION_ID to SESSION_ID,
                    KEY_REQUEST_KIND to REQUEST_RESTORE,
                    KEY_TARGET_MODEL_ID to MODEL_ID,
                    KEY_DOWNLOADED_SHAS to downloadedShas,
                )
            setInputData(inputData)

            // (no soft-deleted model registered — getAssetById returns null)

            // When: Run the worker
            val result = worker.doWork()

            // Then: Should return failure
            assertTrue(result is ListenableWorker.Result.Failure)
        }

    // ===== 3.5 Terminal Output Tests =====

    /**
     * Scenario: finalizer success output includes session and request metadata
     * Given finalization succeeds
     * When output data is inspected
     * Then output includes sessionId, requestKind, workerStage = FINALIZE
     */
    @Test
    fun `finalizer success output includes session and request metadata`() =
        runTest {
            // Given: Valid finalization input
            val downloadedShas = Json.encodeToString(listOf(SHA_1))
            val inputData =
                workDataOf(
                    KEY_SESSION_ID to SESSION_ID,
                    KEY_REQUEST_KIND to REQUEST_INITIALIZE,
                    KEY_DOWNLOADED_SHAS to downloadedShas,
                )
            setInputData(inputData)

            val remoteAsset = createTestAsset("model1.gguf", SHA_1)
            modelConfigFetcher.fetchRemoteConfigResult =
                Result.success(
                    listOf(remoteAsset),
                )

            // When: Run the worker
            val result = worker.doWork()

            // Then: Output includes metadata
            assertTrue(result is ListenableWorker.Result.Success)
            val outputData = (result as ListenableWorker.Result.Success).outputData
            assertEquals(SESSION_ID, outputData.getString(KEY_SESSION_ID))
            assertEquals(REQUEST_INITIALIZE, outputData.getString(KEY_REQUEST_KIND))
            assertEquals(STAGE_FINALIZE, outputData.getString(KEY_WORKER_STAGE))
        }

    /**
     * Scenario: finalizer failure output includes error metadata
     * Given finalization fails permanently
     * When output data is inspected
     * Then output includes sessionId, requestKind, workerStage = FINALIZE, error_message
     */
    @Test
    fun `finalizer failure output includes error metadata`() =
        runTest {
            // Given: Finalization that will fail
            val downloadedShas = Json.encodeToString(listOf(SHA_1))
            val inputData =
                workDataOf(
                    KEY_SESSION_ID to SESSION_ID,
                    KEY_REQUEST_KIND to REQUEST_RESTORE,
                    KEY_TARGET_MODEL_ID to MODEL_ID,
                    KEY_DOWNLOADED_SHAS to downloadedShas,
                )
            setInputData(inputData)

            // Asset not found - causes failure
            // (no soft-deleted model registered — getAssetById returns null)

            // When: Run the worker
            val result = worker.doWork()

            // Then: Failure output includes metadata
            assertTrue(result is ListenableWorker.Result.Failure)
            val outputData = (result as ListenableWorker.Result.Failure).outputData
            assertEquals(SESSION_ID, outputData.getString(KEY_SESSION_ID))
            assertEquals(REQUEST_RESTORE, outputData.getString(KEY_REQUEST_KIND))
            assertEquals(STAGE_FINALIZE, outputData.getString(KEY_WORKER_STAGE))
        }

    @Test
    @DisplayName("SCN-FINALIZE-001")
    fun scnFinalize001_persistsDownloadedUtilityAsset() =
        runTest {
            val onnxAsset =
                createTestAsset("model.onnx", "onnx-sha").let { asset ->
                    asset.copy(
                        metadata = asset.metadata.copy(utilityType = UtilityType.ONNX_MODEL),
                        configurations = emptyList(),
                    )
                }
            val downloadedShas = Json.encodeToString(listOf("onnx-sha"))
            setInputData(
                workDataOf(
                    KEY_SESSION_ID to SESSION_ID,
                    KEY_REQUEST_KIND to REQUEST_INITIALIZE,
                    KEY_DOWNLOADED_SHAS to downloadedShas,
                ),
            )
            modelConfigFetcher.fetchRemoteConfigResult = Result.success(listOf(onnxAsset))

            worker.doWork()

            assertEquals(
                "onnx-sha",
                fakeUtilityModelRepository.getByUtilityType(UtilityType.ONNX_MODEL)?.sha256,
            )
            assertEquals(0, syncLocalModelRegistryUseCase.calls.size)
        }

    @Test
    @DisplayName("SCN-FINALIZE-002")
    fun scnFinalize002_enqueuesEmbeddingRegenForOnnxModel() =
        runTest {
            val onnxAsset =
                createTestAsset("model.onnx", SHA_ONNX).let { asset ->
                    asset.copy(
                        metadata = asset.metadata.copy(utilityType = UtilityType.ONNX_MODEL),
                        configurations = emptyList(),
                    )
                }
            setInputData(
                workDataOf(
                    KEY_SESSION_ID to SESSION_ID,
                    KEY_REQUEST_KIND to REQUEST_INITIALIZE,
                    KEY_DOWNLOADED_SHAS to Json.encodeToString(listOf(SHA_ONNX)),
                ),
            )
            modelConfigFetcher.fetchRemoteConfigResult = Result.success(listOf(onnxAsset))

            worker.doWork()

            // The real coordinator coalesces repeated full-regeneration requests into
            // one pending flag; the pending flag is the observable outcome.
            assertTrue(fullRegenerationPending())
        }

    @Test
    @DisplayName("SCN-MUT-002")
    fun scnMut002_utilityFinalizeUsesUtilityRegistryNotSlotSync() =
        runTest {
            val whisperAsset =
                createTestAsset("ggml-base.en.bin", "whisper-sha").let { asset ->
                    asset.copy(
                        metadata = asset.metadata.copy(utilityType = UtilityType.WHISPER),
                        configurations = emptyList(),
                    )
                }
            setInputData(
                workDataOf(
                    KEY_SESSION_ID to SESSION_ID,
                    KEY_REQUEST_KIND to REQUEST_INITIALIZE,
                    KEY_DOWNLOADED_SHAS to Json.encodeToString(listOf("whisper-sha")),
                ),
            )
            modelConfigFetcher.fetchRemoteConfigResult = Result.success(listOf(whisperAsset))

            worker.doWork()

            assertEquals(0, syncLocalModelRegistryUseCase.calls.size)
            assertEquals(
                "whisper-sha",
                fakeUtilityModelRepository.getByUtilityType(UtilityType.WHISPER)?.sha256,
            )
        }

    @Test
    @DisplayName("DL-007 incomplete SDXL extraction stays incomplete and does not register as ready")
    fun dl007_incompleteSdxlExtractionStaysIncompleteAndSkipsRegistry() = runTest {
        val sdxlSha = "5250591bc45e58490c2fd3c0e0e7d4d166ba1aa6f443d5ebdb962c95a9e99f83"
        val filesRoot = File(tempDir, "files").apply { mkdirs() }
        externalFilesDirOverride = filesRoot
        val modelsDir = File(filesRoot, com.browntowndev.enclave.domain.model.download.ModelConfig.MODELS_DIR).apply { mkdirs() }
        val packDir = File(modelsDir, LocalImageGenerationPaths.SDXL_QNN_SUBDIR).apply { mkdirs() }
        listOf("clip_v2.mnn", "token_emb.bin", "pos_emb.bin", "unet.bin", "vae_decoder.bin").forEach { name ->
            File(packDir, name).writeBytes(byteArrayOf(1))
        }
        File(modelsDir, "realvis_xl_v5_dmd2_qnn2.28_8gen3.zip").writeBytes(byteArrayOf(1, 2, 3))
        val sdxlAsset =
            createTestAsset("realvis_xl_v5_dmd2_qnn2.28_8gen3.zip", sdxlSha).let { asset ->
                asset.copy(
                    metadata =
                        asset.metadata.copy(
                            id = LocalModelId("sdxl-qnn-pack"),
                            utilityType = UtilityType.SDXL_QNN_PACK,
                        ),
                    configurations = emptyList(),
                )
            }
        setInputData(
            workDataOf(
                KEY_SESSION_ID to SESSION_ID,
                KEY_REQUEST_KIND to REQUEST_INITIALIZE,
                KEY_DOWNLOADED_SHAS to Json.encodeToString(listOf(sdxlSha)),
            ),
        )
        modelConfigFetcher.fetchRemoteConfigResult = Result.success(listOf(sdxlAsset))
        qnnPackInstaller.installResult = false

        val result = worker.doWork()

        assertTrue(result is ListenableWorker.Result.Failure)
        assertFalse(LocalImageGenerationPaths.hasSdxlQnnArtifacts(packDir))
        val packVerifier =
            ModelPackVerifierImpl(
                context,
                FakeHashingPort(),
                FakeLocalImageGenerationCapability(),
            )
        val completeness =
            packVerifier.verifyPack(
                ModelPack(
                    packId = "sdxl-t2i",
                    displayName = "Stable Diffusion XL",
                    slot = ModelType.IMAGE_GENERATION,
                    members = emptyList(),
                ),
                ModelPackVerificationDepth.SIZE_ON_DISK,
            )
        assertFalse(completeness.isComplete)
        assertEquals(
            null,
            fakeUtilityModelRepository.getByUtilityType(UtilityType.SDXL_QNN_PACK),
        )
        assertEquals(
            1,
            qnnPackInstaller.installCalls.count { it.utilityType == UtilityType.SDXL_QNN_PACK },
        )
        assertEquals(0, syncLocalModelRegistryUseCase.calls.size)
    }

    @Test
    @DisplayName("DL-009 registers SDXL without overriding the SD1.5 default")
    fun dl009_registersSdxlWithoutOverridingSd15Default() =
        runTest {
            val sdxlSha = "5250591bc45e58490c2fd3c0e0e7d4d166ba1aa6f443d5ebdb962c95a9e99f83"
            val sdxlAsset =
                createTestAsset("realvis_xl_v5_dmd2_qnn2.28_8gen3.zip", sdxlSha).let { asset ->
                    asset.copy(
                        metadata =
                            asset.metadata.copy(
                                id = LocalModelId("sdxl-qnn-pack"),
                                utilityType = UtilityType.SDXL_QNN_PACK,
                            ),
                        configurations =
                            listOf(
                                LocalModelConfiguration(
                                    id =
                                        com.browntowndev.enclave.domain.model.config
                                            .LocalModelConfigurationId("sdxl-local-config"),
                                    localModelId = LocalModelId("sdxl-qnn-pack"),
                                    displayName = "Stable Diffusion XL",
                                    temperature = 0.0,
                                    topK = 1,
                                    topP = 1.0,
                                    minP = 0.0,
                                    repetitionPenalty = 1.0,
                                    maxTokens = 0,
                                    contextWindow = 0,
                                    systemPrompt = "",
                                    isSystemPreset = true,
                                    thinkingEnabled = false,
                                    defaultAssignments = listOf(ModelType.IMAGE_GENERATION),
                                ),
                            ),
                    )
                }
            setInputData(
                workDataOf(
                    KEY_SESSION_ID to SESSION_ID,
                    KEY_REQUEST_KIND to REQUEST_INITIALIZE,
                    KEY_DOWNLOADED_SHAS to Json.encodeToString(listOf(sdxlSha)),
                ),
            )
            // Seed an existing default: IMAGE_GENERATION -> sd15-local-config.
            val existingSd15Default =
                com.browntowndev.enclave.domain.model.config
                    .LocalModelConfigurationId("sd15-local-config")
            var persistedImageGenerationDefault = existingSd15Default
            modelConfigFetcher.fetchRemoteConfigResult = Result.success(listOf(sdxlAsset))
            qnnPackInstaller.installResult = true
            syncLocalModelRegistryUseCase.onInvoke = { modelType, asset ->
                if (modelType == ModelType.IMAGE_GENERATION) {
                    persistedImageGenerationDefault =
                        asset.configurations.first().id
                }
            }

            worker.doWork()

            assertEquals(
                1,
                qnnPackInstaller.installCalls.count { it.utilityType == UtilityType.SDXL_QNN_PACK },
            )
            // The SDXL local asset is registered as assignable (utility registry row present).
            assertEquals(
                sdxlSha,
                fakeUtilityModelRepository.getByUtilityType(UtilityType.SDXL_QNN_PACK)?.sha256,
            )
            // Finalize must not re-sync the IMAGE_GENERATION slot, so the existing SD1.5 default stands.
            assertEquals(
                0,
                syncLocalModelRegistryUseCase.calls.count {
                    it.modelType == ModelType.IMAGE_GENERATION && !it.assignAsDefault
                },
            )
            assertEquals(existingSd15Default, persistedImageGenerationDefault)
        }

    @Test
    @DisplayName("DL-013 registers Juggernaut without overriding existing IMAGE_EDIT default")
    fun dl013_registersJuggernautWithoutOverridingImageEditDefault() =
        runTest {
            val juggernautSha = "b27231823434c4e4184a61f77401227264a5308c6846c6fc628c37583e76413c"
            val juggernautAsset =
                createTestAsset("juggernaut_qnn2.28_8gen3.zip", juggernautSha).let { asset ->
                    asset.copy(
                        metadata =
                            asset.metadata.copy(
                                id = LocalModelId("juggernaut-qnn-pack"),
                                utilityType = UtilityType.JUGGERNAUT_XL_QNN_PACK,
                            ),
                        configurations =
                            listOf(
                                LocalModelConfiguration(
                                    id =
                                        com.browntowndev.enclave.domain.model.config
                                            .LocalModelConfigurationId("juggernaut-local-config"),
                                    localModelId = LocalModelId("juggernaut-qnn-pack"),
                                    displayName = "Juggernaut XL",
                                    temperature = 0.0,
                                    topK = 1,
                                    topP = 1.0,
                                    minP = 0.0,
                                    repetitionPenalty = 1.0,
                                    maxTokens = 0,
                                    contextWindow = 0,
                                    systemPrompt = "",
                                    isSystemPreset = true,
                                    thinkingEnabled = false,
                                    defaultAssignments = listOf(ModelType.IMAGE_EDIT),
                                ),
                            ),
                    )
                }
            setInputData(
                workDataOf(
                    KEY_SESSION_ID to SESSION_ID,
                    KEY_REQUEST_KIND to REQUEST_INITIALIZE,
                    KEY_DOWNLOADED_SHAS to Json.encodeToString(listOf(juggernautSha)),
                ),
            )
            val existingImageEditDefault =
                com.browntowndev.enclave.domain.model.config
                    .LocalModelConfigurationId("sdxl-local-config")
            var persistedImageEditDefault = existingImageEditDefault
            modelConfigFetcher.fetchRemoteConfigResult = Result.success(listOf(juggernautAsset))
            qnnPackInstaller.installResult = true
            syncLocalModelRegistryUseCase.onInvoke = { modelType, asset ->
                if (modelType == ModelType.IMAGE_EDIT) {
                    persistedImageEditDefault = asset.configurations.first().id
                }
            }

            worker.doWork()

            assertEquals(
                juggernautSha,
                fakeUtilityModelRepository.getByUtilityType(UtilityType.JUGGERNAUT_XL_QNN_PACK)?.sha256,
            )
            assertEquals(
                0,
                syncLocalModelRegistryUseCase.calls.count {
                    it.modelType == ModelType.IMAGE_EDIT && !it.assignAsDefault
                },
            )
            assertEquals(existingImageEditDefault, persistedImageEditDefault)
        }

    @Test
    @DisplayName("DL-013b assigns Juggernaut to empty IMAGE_EDIT slot when config and pack assets share a SHA")
    fun dl013b_assignsJuggernautImageEditDefaultWhenConfigAndPackShareSha() =
        runTest {
            val juggernautSha = "b27231823434c4e4184a61f77401227264a5308c6846c6fc628c37583e76413c"
            val configAsset =
                createTestAsset("juggernaut_qnn2.28_8gen3.zip", juggernautSha).let { asset ->
                    asset.copy(
                        metadata = asset.metadata.copy(id = LocalModelId("juggernaut-config")),
                        configurations =
                            listOf(
                                LocalModelConfiguration(
                                    id = LocalModelConfigurationId("juggernaut-local-config"),
                                    localModelId = LocalModelId("juggernaut-config"),
                                    displayName = "Juggernaut XL",
                                    temperature = 0.0,
                                    topK = 1,
                                    topP = 1.0,
                                    minP = 0.0,
                                    repetitionPenalty = 1.0,
                                    maxTokens = 0,
                                    contextWindow = 0,
                                    systemPrompt = "",
                                    isSystemPreset = true,
                                    thinkingEnabled = false,
                                    defaultAssignments = listOf(ModelType.IMAGE_EDIT),
                                ),
                            ),
                    )
                }
            // Separate pack (utility) asset reuses the same zip SHA as the config asset, exactly
            // as model_config.json models Juggernaut/SDXL. The shared SHA must not suppress the
            // slot assignment performed by the config asset.
            val packAsset =
                createTestAsset("juggernaut_qnn2.28_8gen3.zip", juggernautSha).let { asset ->
                    asset.copy(
                        metadata =
                            asset.metadata.copy(
                                id = LocalModelId("juggernaut-qnn-pack"),
                                utilityType = UtilityType.JUGGERNAUT_XL_QNN_PACK,
                            ),
                        configurations = emptyList(),
                    )
                }
            setInputData(
                workDataOf(
                    KEY_SESSION_ID to SESSION_ID,
                    KEY_REQUEST_KIND to REQUEST_INITIALIZE,
                    KEY_DOWNLOADED_SHAS to Json.encodeToString(listOf(juggernautSha)),
                ),
            )
            modelConfigFetcher.fetchRemoteConfigResult =
                Result.success(listOf(configAsset, packAsset))
            // FakeDefaultModelRepository.getDefault defaults to null (empty IMAGE_EDIT slot).

            worker.doWork()

            assertEquals(
                juggernautSha,
                fakeUtilityModelRepository.getByUtilityType(UtilityType.JUGGERNAUT_XL_QNN_PACK)?.sha256,
            )
            assertEquals(
                1,
                syncLocalModelRegistryUseCase.calls.count {
                    it.modelType == ModelType.IMAGE_EDIT && !it.assignAsDefault
                },
            )
            assertEquals(
                1,
                setDefaultModelUseCase.calls.count {
                    it.modelType == ModelType.IMAGE_EDIT &&
                        it.localConfigId == LocalModelConfigurationId("juggernaut-local-config")
                },
            )
        }

    @Test
    @DisplayName("SDXL supersedes an existing SD1.5 IMAGE_GENERATION default when it finalizes")
    fun sdxlSupersedesSd15ImageGenerationDefault() =
        runTest {
            val sdxlSha = "5250591bc45e58490c2fd3c0e0e7d4d166ba1aa6f443d5ebdb962c95a9e99f83"
            val sd15Sha = "c2f6e92f9d08d69cc673a1003528ac8199274b3c0eaec88d5fbefe5af67bd42b"
            val sdxlConfigAsset =
                imageGenerationConfigAsset(
                    fileName = "realvis_xl_v5_dmd2_qnn2.28_8gen3.zip",
                    sha256 = sdxlSha,
                    modelId = "sdxl-qnn",
                    configId = "sdxl-local-config",
                )
            val sd15ExistingAsset =
                imageGenerationConfigAsset(
                    fileName = "stable-diffusion-v1-5-Q4_0.gguf",
                    sha256 = sd15Sha,
                    modelId = "sd15-qnn",
                    configId = "sd15-local-config",
                )
            setInputData(
                workDataOf(
                    KEY_SESSION_ID to SESSION_ID,
                    KEY_REQUEST_KIND to REQUEST_INITIALIZE,
                    KEY_DOWNLOADED_SHAS to Json.encodeToString(listOf(sdxlSha)),
                ),
            )
            modelConfigFetcher.fetchRemoteConfigResult = Result.success(listOf(sdxlConfigAsset))
            defaultModelRepository.seed(
                DefaultModelAssignment(
                    modelType = ModelType.IMAGE_GENERATION,
                    localConfigId = LocalModelConfigurationId("sd15-local-config"),
                ),
            )
            localModelRepository.seedAssetForConfig(LocalModelConfigurationId("sd15-local-config"), sd15ExistingAsset)

            worker.doWork()

            assertEquals(
                1,
                setDefaultModelUseCase.calls.count {
                    it.modelType == ModelType.IMAGE_GENERATION &&
                        it.localConfigId == LocalModelConfigurationId("sdxl-local-config")
                },
            )
        }

    @Test
    @DisplayName("SD1.5 does not downgrade an existing SDXL IMAGE_GENERATION default")
    fun sd15DoesNotDowngradeSdxlImageGenerationDefault() =
        runTest {
            val sdxlSha = "5250591bc45e58490c2fd3c0e0e7d4d166ba1aa6f443d5ebdb962c95a9e99f83"
            val sd15Sha = "c2f6e92f9d08d69cc673a1003528ac8199274b3c0eaec88d5fbefe5af67bd42b"
            val sd15ConfigAsset =
                imageGenerationConfigAsset(
                    fileName = "stable-diffusion-v1-5-Q4_0.gguf",
                    sha256 = sd15Sha,
                    modelId = "sd15-qnn",
                    configId = "sd15-local-config",
                )
            val sdxlExistingAsset =
                imageGenerationConfigAsset(
                    fileName = "realvis_xl_v5_dmd2_qnn2.28_8gen3.zip",
                    sha256 = sdxlSha,
                    modelId = "sdxl-qnn",
                    configId = "sdxl-local-config",
                )
            setInputData(
                workDataOf(
                    KEY_SESSION_ID to SESSION_ID,
                    KEY_REQUEST_KIND to REQUEST_INITIALIZE,
                    KEY_DOWNLOADED_SHAS to Json.encodeToString(listOf(sd15Sha)),
                ),
            )
            modelConfigFetcher.fetchRemoteConfigResult = Result.success(listOf(sd15ConfigAsset))
            defaultModelRepository.seed(
                DefaultModelAssignment(
                    modelType = ModelType.IMAGE_GENERATION,
                    localConfigId = LocalModelConfigurationId("sdxl-local-config"),
                ),
            )
            localModelRepository.seedAssetForConfig(LocalModelConfigurationId("sdxl-local-config"), sdxlExistingAsset)

            worker.doWork()

            assertEquals(
                0,
                setDefaultModelUseCase.calls.count { it.modelType == ModelType.IMAGE_GENERATION },
            )
        }

    // ===== Helper method =====

    private fun imageGenerationConfigAsset(
        fileName: String,
        sha256: String,
        modelId: String,
        configId: String,
    ): LocalModelAsset =
        createTestAsset(fileName, sha256).let { asset ->
            asset.copy(
                metadata = asset.metadata.copy(id = LocalModelId(modelId)),
                configurations =
                    listOf(
                        LocalModelConfiguration(
                            id = LocalModelConfigurationId(configId),
                            localModelId = LocalModelId(modelId),
                            displayName = configId,
                            temperature = 0.0,
                            topK = 1,
                            topP = 1.0,
                            minP = 0.0,
                            repetitionPenalty = 1.0,
                            maxTokens = 0,
                            contextWindow = 0,
                            systemPrompt = "",
                            isSystemPreset = true,
                            thinkingEnabled = false,
                            defaultAssignments = listOf(ModelType.IMAGE_GENERATION),
                        ),
                    ),
            )
        }

    private fun createTestAsset(
        localFileName: String,
        sha256: String,
    ): LocalModelAsset =
        LocalModelAsset(
            metadata =
                LocalModelMetadata(
                    id = LocalModelId("test-id"),
                    huggingFaceModelName = "test/model",
                    remoteFileName = localFileName,
                    localFileName = localFileName,
                    sha256 = sha256,
                    sizeInBytes = 1000000L,
                    modelFileFormat = ModelFileFormat.GGUF,
                ),
            configurations =
                listOf(
                    LocalModelConfiguration(
                        localModelId = LocalModelId("test-id"),
                        displayName = "Test",
                        temperature = 0.7,
                        topK = 40,
                        topP = 0.95,
                        minP = 0.0,
                        repetitionPenalty = 1.1,
                        maxTokens = 4096,
                        contextWindow = 4096,
                        systemPrompt = "",
                        isSystemPreset = true,
                        thinkingEnabled = false,
                        defaultAssignments = listOf(ModelType.MAIN),
                    ),
                ),
        )
}
