package com.browntowndev.pocketcrew.core.data.download

import android.content.Context
import android.util.Log
import androidx.work.Data
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.browntowndev.pocketcrew.domain.model.config.LocalModelAsset
import com.browntowndev.pocketcrew.domain.model.config.LocalModelConfiguration
import com.browntowndev.pocketcrew.domain.model.config.LocalModelId
import com.browntowndev.pocketcrew.domain.model.config.LocalModelMetadata
import com.browntowndev.pocketcrew.domain.model.inference.ModelFileFormat
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.repository.LocalModelRepositoryPort
import com.browntowndev.pocketcrew.domain.port.repository.ModelConfigFetcherPort
import com.browntowndev.pocketcrew.domain.usecase.modelconfig.SyncLocalModelRegistryUseCase
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

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
 */
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
    }

    @MockK
    private lateinit var mockContext: Context

    @MockK
    private lateinit var mockWorkerParams: WorkerParameters

    @MockK
    private lateinit var mockLocalModelRepository: LocalModelRepositoryPort

    @MockK
    private lateinit var mockModelConfigFetcher: ModelConfigFetcherPort

    @MockK
    private lateinit var mockSyncLocalModelRegistryUseCase: SyncLocalModelRegistryUseCase

    @MockK
    private lateinit var mockLoggingPort: LoggingPort

    private lateinit var worker: DownloadFinalizeWorker

    @BeforeEach
    fun setup() {
        MockKAnnotations.init(this)
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any<Throwable>()) } returns 0

        every { mockContext.getString(any()) } returns "test_string"
        every { mockWorkerParams.id } returns java.util.UUID.randomUUID()
        // Default to EMPTY, tests will override as needed
        every { mockWorkerParams.inputData } returns Data.EMPTY

        // Default mock behavior
        coEvery { mockLocalModelRepository.getAllLocalAssets() } returns emptyList()
        coEvery { mockLocalModelRepository.getAssetById(any()) } returns null
        coEvery { mockModelConfigFetcher.fetchRemoteConfig() } returns Result.success(emptyList())

        // Setup logging port mock with all logging methods
        every { mockLoggingPort.debug(any(), any()) } returns Unit
        every { mockLoggingPort.info(any(), any()) } returns Unit
        every { mockLoggingPort.warning(any(), any()) } returns Unit
        every { mockLoggingPort.error(any(), any()) } returns Unit
        every { mockLoggingPort.error(any(), any(), any()) } returns Unit

        // Create the worker with all dependencies
        worker = DownloadFinalizeWorker(
            context = mockContext,
            workerParams = mockWorkerParams,
            localModelRepository = mockLocalModelRepository,
            modelConfigFetcher = mockModelConfigFetcher,
            syncLocalModelRegistryUseCase = mockSyncLocalModelRegistryUseCase,
            logger = mockLoggingPort
        )
    }

    /**
     * Helper to set input data before running worker.
     */
    private fun setInputData(data: androidx.work.Data) {
        every { mockWorkerParams.inputData } returns data
    }

    // ===== 3.2 Common Parsing and Guard Tests =====

    /**
     * Scenario: fails when requestKind is missing
     * Given input without requestKind
     * When doWork() runs
     * Then the worker returns terminal failure
     */
    @Test
    fun `fails when requestKind is missing`() = runTest {
        // Given: Input without requestKind
        val inputData = workDataOf(
            KEY_SESSION_ID to SESSION_ID
            // Missing KEY_REQUEST_KIND
        )
        setInputData(inputData)

        // When: Run the worker
        val result = worker.doWork()

        // Then: Should return terminal failure
        assertTrue(result is androidx.work.ListenableWorker.Result.Failure)
    }

    /**
     * Scenario: fails when sessionId is missing
     * Given input without sessionId
     * When doWork() runs
     * Then the worker returns terminal failure
     */
    @Test
    fun `fails when sessionId is missing`() = runTest {
        // Given: Input without sessionId
        val inputData = workDataOf(
            KEY_REQUEST_KIND to REQUEST_INITIALIZE
            // Missing KEY_SESSION_ID
        )
        setInputData(inputData)

        // When: Run the worker
        val result = worker.doWork()

        // Then: Should return terminal failure
        assertTrue(result is androidx.work.ListenableWorker.Result.Failure)
    }

    /**
     * Scenario: success output includes finalizer stage
     * Given finalization succeeds
     * When doWork() returns
     * Then output includes workerStage = FINALIZE
     */
    @Test
    fun `success output includes finalizer stage`() = runTest {
        // Given: Valid startup finalization input
        val downloadedShas = Json.encodeToString(listOf(SHA_1))
        val inputData = workDataOf(
            KEY_SESSION_ID to SESSION_ID,
            KEY_REQUEST_KIND to REQUEST_INITIALIZE,
            KEY_DOWNLOADED_SHAS to downloadedShas,
        )
        setInputData(inputData)

        // Remote config with matching SHA
        val remoteAsset = createTestAsset("model1.gguf", SHA_1)
        coEvery { mockModelConfigFetcher.fetchRemoteConfig() } returns Result.success(
            listOf(remoteAsset)
        )
        coEvery { mockSyncLocalModelRegistryUseCase.invoke(any(), any()) } returns mockk()

        // When: Run the worker
        val result = worker.doWork()

        // Then: Success output includes finalizer stage
        assertTrue(result is androidx.work.ListenableWorker.Result.Success)
        val outputData = (result as androidx.work.ListenableWorker.Result.Success).outputData
        assertEquals(STAGE_FINALIZE, outputData.getString(KEY_WORKER_STAGE))
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
    fun `startup finalizer activates downloaded slots by SHA`() = runTest {
        // Given: Startup finalization input
        val downloadedShas = Json.encodeToString(listOf(SHA_1, SHA_2))
        val inputData = workDataOf(
            KEY_SESSION_ID to SESSION_ID,
            KEY_REQUEST_KIND to REQUEST_INITIALIZE,
            KEY_DOWNLOADED_SHAS to downloadedShas,
        )
        setInputData(inputData)

        // Remote config with matching SHAs
        val remoteAsset1 = createTestAsset("model1.gguf", SHA_1)
        val remoteAsset2 = createTestAsset("model2.gguf", SHA_2).copy(
            configurations = listOf(
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
                    defaultAssignments = listOf(ModelType.VISION)
                )
            )
        )
        val remoteConfig = listOf(remoteAsset1, remoteAsset2)

        coEvery { mockModelConfigFetcher.fetchRemoteConfig() } returns Result.success(remoteConfig)
        coEvery { mockSyncLocalModelRegistryUseCase.invoke(any(), any()) } returns mockk()

        // When: Run the worker
        worker.doWork()

        // Then: SyncLocalModelRegistryUseCase should be called for matching slots
        coVerify {
            mockSyncLocalModelRegistryUseCase.invoke(ModelType.MAIN, remoteAsset1)
        }
        coVerify {
            mockSyncLocalModelRegistryUseCase.invoke(ModelType.VISION, remoteAsset2)
        }
    }

    /**
     * Scenario: startup finalizer ignores remote assets not downloaded in this session
     * Given remote config includes assets A, B, and C
     * And downloaded_shas contains only A
     * When the finalizer runs
     * Then only A-backed slots are activated
     */
    @Test
    fun `startup finalizer ignores remote assets not downloaded in this session`() = runTest {
        // Given: Only SHA_1 was downloaded, but remote config has SHA_1, SHA_2, SHA_3
        val downloadedShas = Json.encodeToString(listOf(SHA_1))
        val inputData = workDataOf(
            KEY_SESSION_ID to SESSION_ID,
            KEY_REQUEST_KIND to REQUEST_INITIALIZE,
            KEY_DOWNLOADED_SHAS to downloadedShas,
        )
        setInputData(inputData)

        val remoteAsset1 = createTestAsset("model1.gguf", SHA_1)
        val remoteAsset2 = createTestAsset("model2.gguf", SHA_2).copy(
            configurations = listOf(
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
                    defaultAssignments = listOf(ModelType.VISION)
                )
            )
        )
        val remoteAsset3 = createTestAsset("model3.gguf", "sha-not-downloaded").copy(
            configurations = listOf(
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
                    defaultAssignments = listOf(ModelType.DRAFT_ONE)
                )
            )
        )

        coEvery { mockModelConfigFetcher.fetchRemoteConfig() } returns Result.success(
            listOf(remoteAsset1, remoteAsset2, remoteAsset3)
        )
        coEvery { mockSyncLocalModelRegistryUseCase.invoke(any(), any()) } returns mockk()

        // When: Run the worker
        worker.doWork()

        // Then: Only SHA_1 asset should be synced
        coVerify {
            mockSyncLocalModelRegistryUseCase.invoke(ModelType.MAIN, remoteAsset1)
        }
        // SHA_2 and SHA_3 should NOT be synced (not downloaded)
        coVerify(inverse = true) {
            mockSyncLocalModelRegistryUseCase.invoke(ModelType.VISION, remoteAsset2)
        }
        coVerify(inverse = true) {
            mockSyncLocalModelRegistryUseCase.invoke(ModelType.DRAFT_ONE, remoteAsset3)
        }
    }

    /**
     * Scenario: startup finalizer is idempotent under retry
     * Given the same startup finalizer input is executed twice
     * When both runs complete
     * Then registry state converges without duplicates
     */
    @Test
    fun `startup finalizer is idempotent under retry`() = runTest {
        // Given: Valid startup finalization input
        val downloadedShas = Json.encodeToString(listOf(SHA_1))
        val inputData = workDataOf(
            KEY_SESSION_ID to SESSION_ID,
            KEY_REQUEST_KIND to REQUEST_INITIALIZE,
            KEY_DOWNLOADED_SHAS to downloadedShas,
        )
        setInputData(inputData)

        val remoteAsset = createTestAsset("model1.gguf", SHA_1)
        coEvery { mockModelConfigFetcher.fetchRemoteConfig() } returns Result.success(
            listOf(remoteAsset)
        )
        coEvery { mockSyncLocalModelRegistryUseCase.invoke(any(), any()) } returns mockk()

        // When: Run the worker twice (simulating retry)
        worker.doWork()
        worker.doWork()

        // Then: Both runs succeed (idempotent behavior)
        // The use case should be called twice but not fail
        coVerify(exactly = 2) {
            mockSyncLocalModelRegistryUseCase.invoke(ModelType.MAIN, remoteAsset)
        }
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
    fun `restore finalizer requires target model id`() = runTest {
        // Given: Restore input WITHOUT targetModelId (invalid)
        val downloadedShas = Json.encodeToString(listOf(SHA_1))
        val inputData = workDataOf(
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
            result is androidx.work.ListenableWorker.Result.Failure,
            "Worker should return failure when targetModelId is missing for restore request"
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
    fun `restore finalizer restores configs only after successful download`() = runTest {
        // Given: Restore finalization input
        val downloadedShas = Json.encodeToString(listOf(SHA_1))
        val inputData = workDataOf(
            KEY_SESSION_ID to SESSION_ID,
            KEY_REQUEST_KIND to REQUEST_RESTORE,
            KEY_TARGET_MODEL_ID to MODEL_ID,
            KEY_DOWNLOADED_SHAS to downloadedShas,
        )
        setInputData(inputData)

        // Soft-deleted asset exists in repository
        val softDeletedAsset = createTestAsset("model.gguf", SHA_1)
        coEvery { mockLocalModelRepository.getAssetById(LocalModelId(MODEL_ID)) } returns softDeletedAsset

        // Remote config has matching SHA
        val remoteAsset = softDeletedAsset.copy(
            configurations = listOf(
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
                    defaultAssignments = listOf(ModelType.MAIN)
                )
            )
        )
        coEvery { mockModelConfigFetcher.fetchRemoteConfig() } returns Result.success(
            listOf(remoteAsset)
        )

        coEvery {
            mockLocalModelRepository.restoreSoftDeletedModel(any(), any())
        } returns softDeletedAsset

        // When: Run the worker
        val result = worker.doWork()

        // Then: restoreSoftDeletedModel should be called
        coVerify {
            mockLocalModelRepository.restoreSoftDeletedModel(
                LocalModelId(MODEL_ID),
                match { configs ->
                    configs.isNotEmpty() &&
                            configs.all { it.isSystemPreset && it.localModelId.value == MODEL_ID }
                }
            )
        }
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
    fun `restore finalizer rebuilds system presets from remote config`() = runTest {
        // Given: Restore finalization input
        val downloadedShas = Json.encodeToString(listOf(SHA_1))
        val inputData = workDataOf(
            KEY_SESSION_ID to SESSION_ID,
            KEY_REQUEST_KIND to REQUEST_RESTORE,
            KEY_TARGET_MODEL_ID to MODEL_ID,
            KEY_DOWNLOADED_SHAS to downloadedShas,
        )
        setInputData(inputData)

        val softDeletedAsset = createTestAsset("model.gguf", SHA_1)
        coEvery { mockLocalModelRepository.getAssetById(LocalModelId(MODEL_ID)) } returns softDeletedAsset

        // Remote config with multiple presets
        val remoteAsset = softDeletedAsset.copy(
            configurations = listOf(
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
                    defaultAssignments = listOf(ModelType.MAIN)
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
                    defaultAssignments = listOf(ModelType.MAIN)
                )
            )
        )
        coEvery { mockModelConfigFetcher.fetchRemoteConfig() } returns Result.success(
            listOf(remoteAsset)
        )
        coEvery { mockLocalModelRepository.restoreSoftDeletedModel(any(), any()) } returns softDeletedAsset

        // When: Run the worker
        worker.doWork()

        // Then: Restored configs should have targetModelId and isSystemPreset=true
        coVerify {
            mockLocalModelRepository.restoreSoftDeletedModel(
                eq(LocalModelId(MODEL_ID)),
                match { configs ->
                    configs.size == 2 &&
                            configs.all { it.isSystemPreset && it.localModelId.value == MODEL_ID }
                }
            )
        }
    }

    /**
     * Scenario: restore finalizer does not call SyncLocalModelRegistryUseCase
     * Given restore finalization succeeds
     * When the finalizer runs
     * Then role-slot assignment is not performed
     */
    @Test
    fun `restore finalizer does not call SyncLocalModelRegistryUseCase`() = runTest {
        // Given: Restore finalization input
        val downloadedShas = Json.encodeToString(listOf(SHA_1))
        val inputData = workDataOf(
            KEY_SESSION_ID to SESSION_ID,
            KEY_REQUEST_KIND to REQUEST_RESTORE,
            KEY_TARGET_MODEL_ID to MODEL_ID,
            KEY_DOWNLOADED_SHAS to downloadedShas,
        )
        setInputData(inputData)

        val softDeletedAsset = createTestAsset("model.gguf", SHA_1)
        coEvery { mockLocalModelRepository.getAssetById(LocalModelId(MODEL_ID)) } returns softDeletedAsset

        val remoteAsset = softDeletedAsset.copy(
            configurations = listOf(
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
                    defaultAssignments = listOf(ModelType.MAIN)
                )
            )
        )
        coEvery { mockModelConfigFetcher.fetchRemoteConfig() } returns Result.success(
            listOf(remoteAsset)
        )
        coEvery { mockLocalModelRepository.restoreSoftDeletedModel(any(), any()) } returns softDeletedAsset

        // When: Run the worker
        worker.doWork()

        // Then: SyncLocalModelRegistryUseCase should NOT be called for restore
        coVerify(inverse = true) {
            mockSyncLocalModelRegistryUseCase.invoke(any(), any())
        }
    }

    /**
     * Scenario: restore finalizer fails if soft-deleted asset no longer exists
     * Given getAssetById(targetModelId) returns null
     * When the finalizer runs
     * Then it returns terminal failure
     */
    @Test
    fun `restore finalizer fails if soft-deleted asset no longer exists`() = runTest {
        // Given: Restore input but asset not found
        val downloadedShas = Json.encodeToString(listOf(SHA_1))
        val inputData = workDataOf(
            KEY_SESSION_ID to SESSION_ID,
            KEY_REQUEST_KIND to REQUEST_RESTORE,
            KEY_TARGET_MODEL_ID to MODEL_ID,
            KEY_DOWNLOADED_SHAS to downloadedShas,
        )
        setInputData(inputData)

        coEvery { mockLocalModelRepository.getAssetById(LocalModelId(MODEL_ID)) } returns null

        // When: Run the worker
        val result = worker.doWork()

        // Then: Should return failure
        assertTrue(result is androidx.work.ListenableWorker.Result.Failure)
    }

    // ===== 3.5 Terminal Output Tests =====

    /**
     * Scenario: finalizer success output includes session and request metadata
     * Given finalization succeeds
     * When output data is inspected
     * Then output includes sessionId, requestKind, workerStage = FINALIZE
     */
    @Test
    fun `finalizer success output includes session and request metadata`() = runTest {
        // Given: Valid finalization input
        val downloadedShas = Json.encodeToString(listOf(SHA_1))
        val inputData = workDataOf(
            KEY_SESSION_ID to SESSION_ID,
            KEY_REQUEST_KIND to REQUEST_INITIALIZE,
            KEY_DOWNLOADED_SHAS to downloadedShas,
        )
        setInputData(inputData)

        val remoteAsset = createTestAsset("model1.gguf", SHA_1)
        coEvery { mockModelConfigFetcher.fetchRemoteConfig() } returns Result.success(
            listOf(remoteAsset)
        )
        coEvery { mockSyncLocalModelRegistryUseCase.invoke(any(), any()) } returns mockk()

        // When: Run the worker
        val result = worker.doWork()

        // Then: Output includes metadata
        assertTrue(result is androidx.work.ListenableWorker.Result.Success)
        val outputData = (result as androidx.work.ListenableWorker.Result.Success).outputData
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
    fun `finalizer failure output includes error metadata`() = runTest {
        // Given: Finalization that will fail
        val downloadedShas = Json.encodeToString(listOf(SHA_1))
        val inputData = workDataOf(
            KEY_SESSION_ID to SESSION_ID,
            KEY_REQUEST_KIND to REQUEST_RESTORE,
            KEY_TARGET_MODEL_ID to MODEL_ID,
            KEY_DOWNLOADED_SHAS to downloadedShas,
        )
        setInputData(inputData)

        // Asset not found - causes failure
        coEvery { mockLocalModelRepository.getAssetById(LocalModelId(MODEL_ID)) } returns null

        // When: Run the worker
        val result = worker.doWork()

        // Then: Failure output includes metadata
        assertTrue(result is androidx.work.ListenableWorker.Result.Failure)
        val outputData = (result as androidx.work.ListenableWorker.Result.Failure).outputData
        assertEquals(SESSION_ID, outputData.getString(KEY_SESSION_ID))
        assertEquals(REQUEST_RESTORE, outputData.getString(KEY_REQUEST_KIND))
        assertEquals(STAGE_FINALIZE, outputData.getString(KEY_WORKER_STAGE))
    }

    // ===== Helper method =====

    private fun createTestAsset(localFileName: String, sha256: String): LocalModelAsset {
        return LocalModelAsset(
            metadata = LocalModelMetadata(
                id = LocalModelId("test-id"),
                huggingFaceModelName = "test/model",
                remoteFileName = localFileName,
                localFileName = localFileName,
                sha256 = sha256,
                sizeInBytes = 1000000L,
                modelFileFormat = ModelFileFormat.GGUF
            ),
            configurations = listOf(
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
                    defaultAssignments = listOf(ModelType.MAIN)
                )
            )
        )
    }
}
