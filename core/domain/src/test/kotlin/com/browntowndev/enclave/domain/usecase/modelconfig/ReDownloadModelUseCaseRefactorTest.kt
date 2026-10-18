package com.browntowndev.enclave.domain.usecase.modelconfig

import com.browntowndev.enclave.core.testing.FakeDownloadWorkSchedulerPort
import com.browntowndev.enclave.core.testing.FakeLocalModelRepository
import com.browntowndev.enclave.core.testing.FakeLoggingPort
import com.browntowndev.enclave.domain.model.config.LocalModelAsset
import com.browntowndev.enclave.domain.model.config.LocalModelId
import com.browntowndev.enclave.domain.model.config.LocalModelMetadata
import com.browntowndev.enclave.domain.model.download.DownloadRequestKind
import com.browntowndev.enclave.domain.model.download.DownloadSource
import com.browntowndev.enclave.domain.model.inference.ModelFileFormat
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * TDD Red Phase Tests for Download Worker Refactor - Refactored Use Case.
 *
 * These tests verify the NEW expected behavior where:
 * - Re-download does NOT call restoreSoftDeletedModel() before bytes land
 * - Re-download returns ScheduledDownload with session info
 * - Re-download schedules via structured DownloadWorkRequest
 *
 * The refactored ReDownloadModelUseCase no longer depends on ModelConfigFetcherPort
 * because restoration now happens in DownloadFinalizeWorker.
 */
class ReDownloadModelUseCaseRefactorTest {
    private lateinit var localModelRepository: FakeLocalModelRepository
    private lateinit var downloadWorkScheduler: FakeDownloadWorkSchedulerPort
    private lateinit var loggingPort: FakeLoggingPort
    private lateinit var useCase: ReDownloadModelUseCase

    private val modelId = LocalModelId("test-model-id")
    private val sha256 = "abc123def456"

    private val softDeletedAsset =
        LocalModelAsset(
            metadata =
                LocalModelMetadata(
                    id = modelId,
                    huggingFaceModelName = "test/model",
                    remoteFileName = "test.gguf",
                    localFileName = "test.gguf",
                    sha256 = sha256,
                    sizeInBytes = 1_000_000_000L,
                    modelFileFormat = ModelFileFormat.GGUF,
                    source = DownloadSource.HUGGING_FACE,
                ),
            configurations = emptyList(), // Soft-deleted - no configurations
        )

    @BeforeEach
    fun setup() {
        localModelRepository = FakeLocalModelRepository()
        downloadWorkScheduler = FakeDownloadWorkSchedulerPort()
        loggingPort = FakeLoggingPort()

        useCase =
            ReDownloadModelUseCase(
                localModelRepository = localModelRepository,
                downloadWorkScheduler = downloadWorkScheduler,
                loggingPort = loggingPort,
            )
    }

    /**
     * REDOWN_01: Does NOT call restoreSoftDeletedModel() before bytes land.
     *
     * This is the CENTRAL CORRECTNESS BUG in the current implementation.
     * The refactored use case schedules the download pipeline and returns session metadata.
     * Restoration happens in DownloadFinalizeWorker AFTER bytes are verified on disk.
     *
     * EXPECTED: This test should PASS with the refactored implementation.
     */
    @Test
    fun `REDOWN_01 - does NOT call restoreSoftDeletedModel() before bytes land`() =
        runTest {
            // Given
            localModelRepository.registerSoftDeletedModel(modelId, softDeletedAsset)

            // When
            val result = useCase(modelId)

            // Then - restoreSoftDeletedModel was NOT called: the asset is still
            // soft-deleted and has not reappeared among the live assets.
            // The new design moves this call to DownloadFinalizeWorker
            assertEquals(listOf(modelId), localModelRepository.getSoftDeletedModels().map { it.metadata.id })
            assertTrue(localModelRepository.getAllLocalAssets().isEmpty())

            assertTrue(result.isSuccess)
        }

    /**
     * REDOWN_03: No repository mutation occurs before bytes are downloaded.
     *
     * This is a regression test for the central correctness bug.
     *
     * EXPECTED: This test should PASS with the refactored implementation.
     */
    @Test
    fun `REDOWN_03 - no repository mutation before bytes are downloaded`() =
        runTest {
            // Given
            localModelRepository.registerSoftDeletedModel(modelId, softDeletedAsset)

            // When - scheduling happens, not completion
            val result = useCase(modelId)

            // Then - no repository mutations should have occurred
            assertTrue(result.isSuccess)

            // restoreSoftDeletedModel was NOT called (main mutation): the asset
            // is still soft-deleted.
            assertEquals(listOf(modelId), localModelRepository.getSoftDeletedModels().map { it.metadata.id })

            // upsertLocalAsset was NOT called (another mutation): no live assets.
            assertTrue(localModelRepository.getAllLocalAssets().isEmpty())
        }

    /**
     * REDOWN_02: Returns ScheduledDownload with session info for progress tracking.
     *
     * The new design returns session metadata so the UI can observe
     * progress for the specific scheduled request.
     *
     * EXPECTED: This test should PASS with the refactored implementation.
     */
    @Test
    fun `REDOWN_02 - returns session info for progress tracking`() =
        runTest {
            // Given
            localModelRepository.registerSoftDeletedModel(modelId, softDeletedAsset)

            // When
            val result = useCase(modelId)

            // Then
            assertTrue(result.isSuccess)

            val scheduledDownload = result.getOrNull()
            assertNotNull(scheduledDownload)
            assertNotNull(scheduledDownload!!.sessionId)
            assertEquals(DownloadRequestKind.RESTORE_SOFT_DELETED_MODEL, scheduledDownload.requestKind)
            assertEquals(modelId, scheduledDownload.targetModelId)
        }

    /**
     * REDOWN_04: Schedules download via structured DownloadWorkRequest.
     *
     * The refactored use case should call downloadWorkScheduler.enqueue(DownloadWorkRequest(...))
     * with requestKind = DownloadRequestKind.RESTORE_SOFT_DELETED_MODEL and targetModelId = modelId.
     *
     * EXPECTED: This test should PASS with the refactored implementation.
     */
    @Test
    fun `REDOWN_04 - schedules download via structured request`() =
        runTest {
            // Given
            localModelRepository.registerSoftDeletedModel(modelId, softDeletedAsset)

            // When
            useCase(modelId)

            // Then - the structured scheduler was called with a matching request
            val request = downloadWorkScheduler.enqueued.single()
            assertEquals(DownloadRequestKind.RESTORE_SOFT_DELETED_MODEL, request.requestKind)
            assertEquals(modelId, request.targetModelId)
            assertTrue(request.files.isNotEmpty())
            assertEquals(sha256, request.files[0].sha256)
        }
}
