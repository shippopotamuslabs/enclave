package com.browntowndev.enclave.domain.usecase.modelconfig

import com.browntowndev.enclave.core.testing.FakeDownloadWorkSchedulerPort
import com.browntowndev.enclave.core.testing.FakeLocalModelRepository
import com.browntowndev.enclave.core.testing.FakeLoggingPort
import com.browntowndev.enclave.domain.model.config.LocalModelAsset
import com.browntowndev.enclave.domain.model.config.LocalModelId
import com.browntowndev.enclave.domain.model.config.LocalModelMetadata
import com.browntowndev.enclave.domain.model.download.DownloadRequestKind
import com.browntowndev.enclave.domain.model.download.DownloadSource
import com.browntowndev.enclave.domain.model.download.ScheduledDownload
import com.browntowndev.enclave.domain.model.inference.ModelFileFormat
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Tests for ReDownloadModelUseCase.
 * These tests verify the NEW expected behavior where:
 * - Re-download does NOT call restoreSoftDeletedModel() before bytes land
 * - Re-download returns ScheduledDownload with session info
 * - Re-download schedules via structured DownloadWorkRequest
 */
class ReDownloadModelUseCaseTest {
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

    @Test
    fun `invoke with non-existent model returns failure with IllegalStateException`() =
        runTest {
            // No soft-deleted model registered, so getAssetById returns null.

            val result = useCase(modelId)

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is IllegalStateException)
            assertTrue(result.exceptionOrNull()?.message?.contains("not found") == true)
        }

    /**
     * Scenario: does NOT call restoreSoftDeletedModel() before bytes land
     *
     * This is the CENTRAL CORRECTNESS BUG being fixed.
     * The old implementation called restoreSoftDeletedModel() BEFORE download.
     * The new implementation schedules the download pipeline and returns session metadata.
     * Restoration happens in DownloadFinalizeWorker AFTER bytes are verified.
     */
    @Test
    fun `does NOT call restoreSoftDeletedModel() before bytes land`() =
        runTest {
            // Given: Soft-deleted asset exists
            localModelRepository.registerSoftDeletedModel(modelId, softDeletedAsset)

            // When
            val result = useCase(modelId)

            // Then - restoreSoftDeletedModel was NOT called: the asset is still
            // soft-deleted and has not reappeared among the live assets.
            // This is the key correctness fix: no early restoration
            assertEquals(listOf(modelId), localModelRepository.getSoftDeletedModels().map { it.metadata.id })
            assertTrue(localModelRepository.getAllLocalAssets().isEmpty())

            assertTrue(result.isSuccess)
        }

    /**
     * Scenario: no repository mutation before bytes are downloaded
     *
     * Regression test for the central correctness bug.
     */
    @Test
    fun `no repository mutation before bytes are downloaded`() =
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
     * Scenario: returns ScheduledDownload with session info for progress tracking
     */
    @Test
    fun `returns ScheduledDownload with session info`() =
        runTest {
            // Given
            localModelRepository.registerSoftDeletedModel(modelId, softDeletedAsset)

            // When
            val result = useCase(modelId)

            // Then
            assertTrue(result.isSuccess)

            val scheduledDownload = result.getOrNull()
            assertNotNull(scheduledDownload)

            // Verify session metadata is returned
            assertNotNull(scheduledDownload!!.sessionId)
            assertEquals(DownloadRequestKind.RESTORE_SOFT_DELETED_MODEL, scheduledDownload.requestKind)
            assertEquals(modelId, scheduledDownload.targetModelId)
        }

    /**
     * Scenario: schedules download via structured DownloadWorkRequest
     */
    @Test
    fun `schedules download via structured DownloadWorkRequest`() =
        runTest {
            // Given
            localModelRepository.registerSoftDeletedModel(modelId, softDeletedAsset)

            // When
            useCase(modelId)

            // Then - the structured scheduler was called with a DownloadWorkRequest
            val request = downloadWorkScheduler.enqueued.single()
            assertEquals(DownloadRequestKind.RESTORE_SOFT_DELETED_MODEL, request.requestKind)
            assertEquals(modelId, request.targetModelId)
            assertEquals(1, request.files.size)
            assertEquals(sha256, request.files[0].sha256)
        }

    /**
     * Scenario: builds DownloadFileSpec from soft-deleted asset
     */
    @Test
    fun `builds DownloadFileSpec from soft-deleted asset`() =
        runTest {
            // Given
            localModelRepository.registerSoftDeletedModel(modelId, softDeletedAsset)

            // When
            useCase(modelId)

            // Then - the file spec contains correct data from the soft-deleted asset
            val fileSpec = downloadWorkScheduler.enqueued.single().files[0]
            assertEquals(softDeletedAsset.metadata.remoteFileName, fileSpec.remoteFileName)
            assertEquals(softDeletedAsset.metadata.localFileName, fileSpec.localFileName)
            assertEquals(softDeletedAsset.metadata.sha256, fileSpec.sha256)
            assertEquals(softDeletedAsset.metadata.sizeInBytes, fileSpec.sizeInBytes)
        }

    /**
     * Scenario: returns Result<ScheduledDownload> (not Result<Unit>)
     */
    @Test
    fun `returns Result of ScheduledDownload`() =
        runTest {
            // Given
            localModelRepository.registerSoftDeletedModel(modelId, softDeletedAsset)

            // When
            val result = useCase(modelId)

            // Then - the return type is ScheduledDownload
            assertTrue(result.isSuccess)
            val scheduled = result.getOrNull()
            assertTrue(scheduled is ScheduledDownload)
        }
}
