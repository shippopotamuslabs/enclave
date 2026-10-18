package com.browntowndev.enclave.core.data.download

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.impl.utils.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.browntowndev.enclave.domain.model.config.LocalModelId
import com.browntowndev.enclave.domain.model.download.DownloadFileSpec
import com.browntowndev.enclave.domain.model.download.DownloadRequestKind
import com.browntowndev.enclave.domain.model.download.DownloadWorkRequest
import com.browntowndev.enclave.domain.model.download.ModelConfig
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

/**
 * Tests for DownloadWorkScheduler Chain Construction.
 *
 * These tests verify the two-worker chain behavior where:
 * - Scheduler enqueues a two-worker chain (ModelDownloadWorker -> DownloadFinalizeWorker)
 * - Both workers receive static request metadata
 * - Finalizer input includes metadata before parent output merge
 * - Scheduler uses structured DownloadWorkRequest
 *
 * ENC-28: runs against a real test WorkManager. A recording WorkerFactory stands in
 * for the two chain workers so the input Data each worker actually receives (after
 * WorkManager's input merge) can be asserted; TestDriver forces constraints met.
 */
@ExtendWith(RobolectricExtension::class)
@Config(sdk = [34])
class DownloadWorkSchedulerChainTest {
    private lateinit var context: Context
    private lateinit var workManager: WorkManager
    private lateinit var scheduler: DownloadWorkScheduler

    private val downloadInputs = mutableListOf<Data>()
    private val finalizeInputs = mutableListOf<Data>()

    private inner class RecordingWorkerFactory : WorkerFactory() {
        override fun createWorker(
            appContext: Context,
            workerClassName: String,
            workerParameters: WorkerParameters,
        ): ListenableWorker =
            object : ListenableWorker(appContext, workerParameters) {
                override fun startWork(): ListenableFuture<Result> {
                    when (workerClassName) {
                        ModelDownloadWorker::class.java.name -> downloadInputs += inputData
                        DownloadFinalizeWorker::class.java.name -> finalizeInputs += inputData
                    }
                    return Futures.immediateFuture(Result.success())
                }
            }
    }

    @BeforeEach
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        val config =
            Configuration
                .Builder()
                .setTaskExecutor(SynchronousExecutor())
                .setExecutor(SynchronousExecutor())
                .setWorkerFactory(RecordingWorkerFactory())
                .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)
        workManager = WorkManager.getInstance(context)

        scheduler = DownloadWorkScheduler(context, workManager)
    }

    private fun chainWork(): List<WorkInfo> = workManager.getWorkInfosForUniqueWork(ModelConfig.WORK_TAG).get()

    private fun downloadInfo(): WorkInfo = chainWork().first { it.tags.contains(ModelDownloadWorker::class.java.name) }

    private fun finalizeInfo(): WorkInfo = chainWork().first { it.tags.contains(DownloadFinalizeWorker::class.java.name) }

    /** Forces constraints met so the given work runs on the synchronous test executor. */
    private fun runWork(info: WorkInfo) {
        WorkManagerTestInitHelper.getTestDriver(context)?.setAllConstraintsMet(info.id)
    }

    private fun testRequest(
        requestKind: DownloadRequestKind = DownloadRequestKind.RESTORE_SOFT_DELETED_MODEL,
        targetModelId: LocalModelId? = LocalModelId("model-123"),
        sessionId: String = "session-1",
        wifiOnly: Boolean = true,
    ) = DownloadWorkRequest(
        files =
            listOf(
                DownloadFileSpec(
                    remoteFileName = "test.gguf",
                    localFileName = "test.gguf",
                    sha256 = "sha123",
                    sizeInBytes = 1000L,
                    huggingFaceModelName = "test/model",
                    source = "HUGGING_FACE",
                    modelFileFormat = "GGUF",
                ),
            ),
        sessionId = sessionId,
        requestKind = requestKind,
        targetModelId = targetModelId,
        wifiOnly = wifiOnly,
    )

    // ===== SCHED_03: Finalizer input includes static metadata before parent merge =====

    @Test
    fun `SCHED_03 - finalizer input includes static metadata before parent merge`() {
        // Given: A structured DownloadWorkRequest
        val request = testRequest()

        // When
        scheduler.enqueue(request)

        // Then: the finalize worker was chained after the download worker
        val finalize = finalizeInfo()
        assertNotNull(finalize)

        // Run the chain so the finalizer actually starts with its merged input.
        runWork(downloadInfo())
        runWork(finalizeInfo())

        // The finalizer's received input carries the scheduler's static metadata
        // (session / kind / target), independent of the parent worker's output.
        val finalizeInput = finalizeInputs.single()
        assertEquals("session-1", finalizeInput.getString(DownloadWorkKeys.KEY_SESSION_ID))
        assertEquals("RESTORE_SOFT_DELETED_MODEL", finalizeInput.getString(DownloadWorkKeys.KEY_REQUEST_KIND))
        assertEquals("model-123", finalizeInput.getString(DownloadWorkKeys.KEY_TARGET_MODEL_ID))
    }

    // ===== SCHED_04: Cancel cancels the unique chain =====

    @Test
    fun `SCHED_04 - cancel cancels the unique chain`() {
        scheduler.enqueue(testRequest())

        // When
        scheduler.cancel()

        // Then: every work in the unique chain is cancelled
        val infos = chainWork()
        assertEquals(2, infos.size)
        assertTrue(infos.all { it.state == WorkInfo.State.CANCELLED })
    }

    // ===== SCHED_05: Wifi-only uses UNMETERED network constraint =====

    @Test
    fun `SCHED_05 - wifi-only request uses UNMETERED network constraint`() {
        // Given
        val request =
            testRequest(
                requestKind = DownloadRequestKind.INITIALIZE_MODELS,
                targetModelId = null,
                wifiOnly = true,
            )

        // When
        scheduler.enqueue(request)

        // Then: the download request uses UNMETERED constraint
        assertEquals(NetworkType.UNMETERED, downloadInfo().constraints.requiredNetworkType)
    }

    // ===== SCHED_06: Non-wifi request uses CONNECTED network constraint =====

    @Test
    fun `SCHED_06 - non-wifi request uses CONNECTED network constraint`() {
        // Given
        val request =
            testRequest(
                requestKind = DownloadRequestKind.INITIALIZE_MODELS,
                targetModelId = null,
                wifiOnly = false,
            )

        // When
        scheduler.enqueue(request)

        // Then: the download request uses CONNECTED constraint
        assertEquals(NetworkType.CONNECTED, downloadInfo().constraints.requiredNetworkType)
    }

    // ===== SCHED_07: Single pipeline with REPLACE policy =====

    @Test
    fun `SCHED_07 - uses REPLACE policy for single pipeline`() {
        // Given
        val request =
            testRequest(
                requestKind = DownloadRequestKind.INITIALIZE_MODELS,
                targetModelId = null,
                wifiOnly = true,
            )

        // When: the same unique work is enqueued twice
        scheduler.enqueue(request)
        val firstChainIds = chainWork().map { it.id }.toSet()
        val firstDownloadId = downloadInfo().id
        scheduler.enqueue(request)

        // Then: REPLACE cancelled the first chain and enqueued a fresh one.
        // Against a real WorkManager the cancelled first chain is NOT observable
        // after the second enqueue: EnqueueRunnable deletes the superseded
        // WorkSpec rows in the same operation (verified against work-runtime
        // 2.11.2 — the rows are deleted outright, so neither the unique-name
        // query nor a tag query can return them). The FakeWorkManager-era
        // expectation of 4 infos (2 CANCELLED + 2 ENQUEUED) cannot occur.
        // REPLACE is therefore asserted in its observable form: exactly the
        // fresh chain remains (2 infos, none carrying a first-chain id), its
        // download is ENQUEUED under a new id, and its finalizer is BLOCKED
        // behind it — the old pipeline was replaced, not appended or kept.
        val infos = chainWork()
        assertEquals(2, infos.size)
        assertTrue(infos.none { it.id in firstChainIds })
        val activeDownload = infos.first { it.tags.contains(ModelDownloadWorker::class.java.name) && it.state == WorkInfo.State.ENQUEUED }
        assertTrue(activeDownload.id != firstDownloadId)
        assertEquals(WorkInfo.State.BLOCKED, finalizeInfo().state)
    }

    // ===== SCHED_08: Download worker input contains request metadata =====

    @Test
    fun `SCHED_08 - download worker input contains request metadata`() {
        // Given
        val request =
            testRequest(
                requestKind = DownloadRequestKind.RESTORE_SOFT_DELETED_MODEL,
                targetModelId = LocalModelId("model-456"),
                sessionId = "session-abc",
                wifiOnly = true,
            )

        // When
        scheduler.enqueue(request)
        runWork(downloadInfo())

        // Then: the download worker received the request metadata as input data
        val inputData = downloadInputs.single()
        assertEquals("session-abc", inputData.getString(DownloadWorkKeys.KEY_SESSION_ID))
        assertEquals("RESTORE_SOFT_DELETED_MODEL", inputData.getString(DownloadWorkKeys.KEY_REQUEST_KIND))
        assertEquals("model-456", inputData.getString(DownloadWorkKeys.KEY_TARGET_MODEL_ID))
        assertNotNull(inputData.getString(DownloadWorkKeys.KEY_DOWNLOAD_FILES))
    }
}
