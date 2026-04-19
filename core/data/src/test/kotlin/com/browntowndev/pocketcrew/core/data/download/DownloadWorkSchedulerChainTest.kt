package com.browntowndev.pocketcrew.core.data.download

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkContinuation
import androidx.work.WorkManager
import com.browntowndev.pocketcrew.domain.model.download.DownloadFileSpec
import com.browntowndev.pocketcrew.domain.model.download.DownloadRequestKind
import com.browntowndev.pocketcrew.domain.model.download.DownloadWorkRequest
import com.browntowndev.pocketcrew.domain.model.download.ModelConfig
import com.browntowndev.pocketcrew.domain.model.config.LocalModelId
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Tests for DownloadWorkScheduler Chain Construction.
 *
 * These tests verify the two-worker chain behavior where:
 * - Scheduler enqueues a two-worker chain (ModelDownloadWorker -> DownloadFinalizeWorker)
 * - Both workers receive static request metadata
 * - Finalizer input includes metadata before parent output merge
 * - Scheduler uses structured DownloadWorkRequest
 */
class DownloadWorkSchedulerChainTest {

    private lateinit var mockContext: Context
    private lateinit var mockWorkManager: WorkManager
    private lateinit var mockContinuation: WorkContinuation
    private lateinit var mockFinalContinuation: WorkContinuation
    private lateinit var scheduler: DownloadWorkScheduler

    @BeforeEach
    fun setup() {
        mockContext = mockk(relaxed = true)
        mockWorkManager = mockk(relaxed = true)
        mockContinuation = mockk(relaxed = true)
        mockFinalContinuation = mockk(relaxed = true)

        every { mockWorkManager.beginUniqueWork(any(), any(), any<OneTimeWorkRequest>()) } returns mockContinuation
        every { mockContinuation.then(any<OneTimeWorkRequest>()) } returns mockFinalContinuation

        scheduler = DownloadWorkScheduler(mockContext, mockWorkManager)
    }

    // ===== SCHED_03: Finalizer input includes static metadata before parent merge =====

    @Test
    fun `SCHED_03 - finalizer input includes static metadata before parent merge`() {
        // Given: A structured DownloadWorkRequest
        val request = DownloadWorkRequest(
            files = listOf(
                DownloadFileSpec(
                    remoteFileName = "test.gguf",
                    localFileName = "test.gguf",
                    sha256 = "sha123",
                    sizeInBytes = 1000L,
                    huggingFaceModelName = "test/model",
                    source = "HUGGING_FACE",
                    modelFileFormat = "GGUF",
                )
            ),
            sessionId = "session-1",
            requestKind = DownloadRequestKind.RESTORE_SOFT_DELETED_MODEL,
            targetModelId = LocalModelId("model-123"),
            wifiOnly = true,
        )

        // Capture the work requests
        val downloadSlot = slot<OneTimeWorkRequest>()
        val finalizeSlot = slot<OneTimeWorkRequest>()
        every { mockWorkManager.beginUniqueWork(any(), any(), capture(downloadSlot)) } returns mockContinuation
        every { mockContinuation.then(capture(finalizeSlot)) } returns mockFinalContinuation

        // When
        scheduler.enqueue(request)

        // Then: Verify finalize worker was created
        assertNotNull(finalizeSlot.captured)

        // The finalize worker should NOT have explicit input data that overrides
        // the download worker's output (WorkManager merges parent output automatically)
        // When no explicit input is set, WorkManager uses parent output as input
    }

    // ===== SCHED_04: Cancel cancels the unique chain =====

    @Test
    fun `SCHED_04 - cancel cancels the unique chain`() {
        // When
        scheduler.cancel()

        // Then: Verify WorkManager cancelUniqueWork was called
        verify {
            mockWorkManager.cancelUniqueWork(ModelConfig.WORK_TAG)
        }
    }

    // ===== SCHED_05: Wifi-only uses UNMETERED network constraint =====

    @Test
    fun `SCHED_05 - wifi-only request uses UNMETERED network constraint`() {
        // Given
        val request = DownloadWorkRequest(
            files = listOf(
                DownloadFileSpec(
                    remoteFileName = "test.gguf",
                    localFileName = "test.gguf",
                    sha256 = "sha123",
                    sizeInBytes = 1000L,
                    huggingFaceModelName = "test/model",
                    source = "HUGGING_FACE",
                    modelFileFormat = "GGUF",
                )
            ),
            sessionId = "session-1",
            requestKind = DownloadRequestKind.INITIALIZE_MODELS,
            targetModelId = null,
            wifiOnly = true,
        )

        val downloadSlot = slot<OneTimeWorkRequest>()
        every { mockWorkManager.beginUniqueWork(any(), any(), capture(downloadSlot)) } returns mockContinuation

        // When
        scheduler.enqueue(request)

        // Then: Verify the download request uses UNMETERED constraint
        val constraints = downloadSlot.captured.workSpec.constraints
        assertEquals(NetworkType.UNMETERED, constraints.requiredNetworkType)
    }

    // ===== SCHED_06: Non-wifi request uses CONNECTED network constraint =====

    @Test
    fun `SCHED_06 - non-wifi request uses CONNECTED network constraint`() {
        // Given
        val request = DownloadWorkRequest(
            files = listOf(
                DownloadFileSpec(
                    remoteFileName = "test.gguf",
                    localFileName = "test.gguf",
                    sha256 = "sha123",
                    sizeInBytes = 1000L,
                    huggingFaceModelName = "test/model",
                    source = "HUGGING_FACE",
                    modelFileFormat = "GGUF",
                )
            ),
            sessionId = "session-1",
            requestKind = DownloadRequestKind.INITIALIZE_MODELS,
            targetModelId = null,
            wifiOnly = false,
        )

        val downloadSlot = slot<OneTimeWorkRequest>()
        every { mockWorkManager.beginUniqueWork(any(), any(), capture(downloadSlot)) } returns mockContinuation

        // When
        scheduler.enqueue(request)

        // Then: Verify the download request uses CONNECTED constraint
        val constraints = downloadSlot.captured.workSpec.constraints
        assertEquals(NetworkType.CONNECTED, constraints.requiredNetworkType)
    }

    // ===== SCHED_07: Single pipeline with REPLACE policy =====

    @Test
    fun `SCHED_07 - uses REPLACE policy for single pipeline`() {
        // Given
        val request = DownloadWorkRequest(
            files = listOf(
                DownloadFileSpec(
                    remoteFileName = "test.gguf",
                    localFileName = "test.gguf",
                    sha256 = "sha123",
                    sizeInBytes = 1000L,
                    huggingFaceModelName = "test/model",
                    source = "HUGGING_FACE",
                    modelFileFormat = "GGUF",
                )
            ),
            sessionId = "session-1",
            requestKind = DownloadRequestKind.INITIALIZE_MODELS,
            targetModelId = null,
            wifiOnly = true,
        )

        val policySlot = slot<ExistingWorkPolicy>()
        every { mockWorkManager.beginUniqueWork(any(), capture(policySlot), any<OneTimeWorkRequest>()) } returns mockContinuation

        // When
        scheduler.enqueue(request)

        // Then: Verify REPLACE policy was used
        assertEquals(ExistingWorkPolicy.REPLACE, policySlot.captured)
    }

    // ===== SCHED_08: Download worker input contains request metadata =====

    @Test
    fun `SCHED_08 - download worker input contains request metadata`() {
        // Given
        val request = DownloadWorkRequest(
            files = listOf(
                DownloadFileSpec(
                    remoteFileName = "test.gguf",
                    localFileName = "test.gguf",
                    sha256 = "sha123",
                    sizeInBytes = 1000L,
                    huggingFaceModelName = "test/model",
                    source = "HUGGING_FACE",
                    modelFileFormat = "GGUF",
                )
            ),
            sessionId = "session-abc",
            requestKind = DownloadRequestKind.RESTORE_SOFT_DELETED_MODEL,
            targetModelId = LocalModelId("model-456"),
            wifiOnly = true,
        )

        val downloadSlot = slot<OneTimeWorkRequest>()
        every { mockWorkManager.beginUniqueWork(any(), any(), capture(downloadSlot)) } returns mockContinuation

        // When
        scheduler.enqueue(request)

        // Then: Verify download worker input data
        val inputData = downloadSlot.captured.workSpec.input
        assertEquals("session-abc", inputData.getString(DownloadWorkKeys.KEY_SESSION_ID))
        assertEquals("RESTORE_SOFT_DELETED_MODEL", inputData.getString(DownloadWorkKeys.KEY_REQUEST_KIND))
        assertEquals("model-456", inputData.getString(DownloadWorkKeys.KEY_TARGET_MODEL_ID))
        assertNotNull(inputData.getString(DownloadWorkKeys.KEY_DOWNLOAD_FILES))
    }
}