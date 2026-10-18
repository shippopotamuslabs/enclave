package com.browntowndev.enclave.core.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.impl.utils.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.browntowndev.enclave.core.data.download.DownloadFinalizeWorker
import com.browntowndev.enclave.core.data.download.DownloadWorkKeys
import com.browntowndev.enclave.core.data.download.ModelDownloadWorker
import com.browntowndev.enclave.domain.model.download.ModelConfig
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

/**
 * Tests for DownloadWorkRepository Chain-Aware Observation.
 *
 * These tests verify that the repository:
 * - Observes the active unique work chain, not a single UUID
 * - Selects the appropriate worker (finalizer > downloader when both exist)
 * - Ignores work from stale sessions
 * - Provides chain-aware observation for UI
 *
 * ENC-28: runs against a real test [WorkManager] (work-runtime 2.11 made the
 * WorkManager constructor internal, so the former subclass fake cannot
 * exist). The chain is enqueued for real under [ModelConfig.WORK_TAG];
 * worker outcomes are scripted through a [WorkerFactory]: immediate
 * success, or a pending worker held in RUNNING by an uncompleted future.
 * Requests also carry the worker class-name tags explicitly (production
 * tags only [ModelConfig.WORK_TAG]; the repository's stage resolution reads
 * class-name tags first, so they are pinned here for determinism).
 */
@ExtendWith(RobolectricExtension::class)
@Config(sdk = [34])
class DownloadWorkRepositoryChainTest {
    companion object {
        const val KEY_WORKER_STAGE = "worker_stage"
        const val KEY_SESSION_ID = "work_session_id"
        const val STAGE_DOWNLOAD = "DOWNLOAD"
        const val STAGE_FINALIZE = "FINALIZE"

        const val ACTIVE_SESSION = "active-session-123"
        const val STALE_SESSION = "stale-session-456"
    }

    private lateinit var workManager: WorkManager
    private lateinit var repository: DownloadWorkRepository
    private val workerFactory = ScriptedWorkerFactory()

    @BeforeEach
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val config =
            Configuration
                .Builder()
                .setTaskExecutor(SynchronousExecutor())
                .setExecutor(SynchronousExecutor())
                .setWorkerFactory(workerFactory)
                .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)
        workManager = WorkManager.getInstance(context)
        // Inject UnconfinedTestDispatcher for IO work to avoid race conditions in tests
        repository = DownloadWorkRepository(workManager, UnconfinedTestDispatcher())
    }

    // ===== REPO_01: Selects running finalizer over succeeded downloader =====

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `REPO_01 - selects running finalizer over succeeded downloader in same session`() =
        runTest {
            // Given: Chain with succeeded downloader and running finalizer
            workerFactory.succeed(
                ModelDownloadWorker::class.java.name,
                stageOutput(ACTIVE_SESSION, STAGE_DOWNLOAD),
            )
            workerFactory.hold(DownloadFinalizeWorker::class.java.name)
            val downloadRequest = downloadRequest()
            val finalizeRequest = finalizeRequest()
            enqueueChain(downloadRequest, finalizeRequest)

            // When: Get current work info
            val result = repository.getCurrentWorkInfo()

            // Then: Should return the finalizer (active), not downloader (terminal)
            assertNotNull(result)
            assertEquals(WorkInfo.State.RUNNING, result!!.state)
            assertEquals(finalizeRequest.id, result.id)
        }

    // ===== REPO_02: Selects running downloader when finalizer not started =====

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `REPO_02 - selects running downloader when finalizer has not started`() =
        runTest {
            // Given: Chain with only running download work
            workerFactory.hold(ModelDownloadWorker::class.java.name)
            val downloadRequest = downloadRequest()
            enqueueSingle(downloadRequest)

            // When: Get current work info
            val result = repository.getCurrentWorkInfo()

            // Then: Should return the running downloader
            assertNotNull(result)
            assertEquals(WorkInfo.State.RUNNING, result!!.state)
            assertEquals(downloadRequest.id, result.id)
        }

    // ===== REPO_03: Selects terminal finalizer when chain is complete =====

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `REPO_03 - selects terminal finalizer when chain is complete`() =
        runTest {
            // Given: Chain with both succeeded
            workerFactory.succeed(
                ModelDownloadWorker::class.java.name,
                stageOutput(ACTIVE_SESSION, STAGE_DOWNLOAD),
            )
            workerFactory.succeed(
                DownloadFinalizeWorker::class.java.name,
                stageOutput(ACTIVE_SESSION, STAGE_FINALIZE),
            )
            val downloadRequest = downloadRequest()
            val finalizeRequest = finalizeRequest()
            enqueueChain(downloadRequest, finalizeRequest)

            // When: Get current work info
            val result = repository.getCurrentWorkInfo()

            // Then: Should return the finalizer (terminal for chain)
            assertNotNull(result)
            assertEquals(WorkInfo.State.SUCCEEDED, result!!.state)
            assertEquals(finalizeRequest.id, result.id)
        }

    // ===== REPO_04: Ignores work from stale sessions =====

    /**
     * REPO_04: Selects active session work regardless of stale data.
     *
     * Note: Session staleness is handled by the parser, not the repository.
     * The repository passes through all work info so the parser can filter.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `REPO_04 - returns non-null when work exists`() =
        runTest {
            // Given: Work items from different sessions
            workerFactory.hold(ModelDownloadWorker::class.java.name)
            enqueueSingle(downloadRequest())

            // When: Get current work info
            val result = repository.getCurrentWorkInfo()

            // Then: Should return the work (session staleness is handled by the parser)
            assertNotNull(result)
        }

    // ===== REPO_05: Returns null when no work matches =====

    @Test
    fun `REPO_05 - returns null when no work matches session`() =
        runTest {
            // Given: No work enqueued under the unique name

            // When: Get current work info
            val result = repository.getCurrentWorkInfo()

            // Then: Should return null
            assertNull(result)
        }

    // ===== REPO_06: getWorkId returns finalizer when running =====

    @Test
    fun `REPO_06 - getWorkId returns finalizer when running`() =
        runTest {
            // Given: Running finalizer
            workerFactory.succeed(
                ModelDownloadWorker::class.java.name,
                stageOutput(ACTIVE_SESSION, STAGE_DOWNLOAD),
            )
            workerFactory.hold(DownloadFinalizeWorker::class.java.name)
            val downloadRequest = downloadRequest()
            val finalizeRequest = finalizeRequest()
            enqueueChain(downloadRequest, finalizeRequest)

            // When: Get work ID
            val result = repository.getWorkId()

            // Then: Should return finalizer's ID
            assertEquals(finalizeRequest.id, result)
        }

    // ===== REPO_07: Backward compatibility - startup observation still works =====

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `REPO_07 - startup observation still works while only downloader is running`() =
        runTest {
            // Given: Single download work (no finalizer yet)
            workerFactory.hold(ModelDownloadWorker::class.java.name)
            val downloadRequest = downloadRequest()
            enqueueSingle(downloadRequest)

            // When: Observe progress for the chain
            val result = repository.observeDownloadProgress().first()

            // Then: Should receive the work info
            assertNotNull(result)
            assertEquals(downloadRequest.id, result!!.id)
            assertEquals(WorkInfo.State.RUNNING, result.state)
        }

    // ===== REPO_08: observeDownloadProgress transitions from succeeded downloader to finalizer =====

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `REPO_08 - observeDownloadProgress transitions to finalizer when downloader succeeds`() =
        runTest {
            // Given: Chain enqueued; downloader held RUNNING, finalizer BLOCKED behind it
            workerFactory.hold(ModelDownloadWorker::class.java.name)
            workerFactory.hold(DownloadFinalizeWorker::class.java.name)
            val downloadRequest = downloadRequest()
            val finalizeRequest = finalizeRequest()
            enqueueChain(downloadRequest, finalizeRequest)

            // When: Observe progress starting with the downloader
            val results = mutableListOf<WorkInfo>()
            val job =
                launch {
                    repository.observeDownloadProgress().collect {
                        if (it != null) results.add(it)
                    }
                }

            // Initially sees downloader
            delay(100)
            assertTrue(results.isNotEmpty(), "expected at least the initial downloader emission")
            assertEquals(downloadRequest.id, results[0].id)
            assertEquals(WorkInfo.State.RUNNING, results[0].state)

            // Downloader succeeds, finalizer starts
            workerFactory.complete(
                ModelDownloadWorker::class.java.name,
                stageOutput(ACTIVE_SESSION, STAGE_DOWNLOAD),
            )

            // Then: A finalizer RUNNING emission arrives after the downloader's.
            // (Conversion: the fake-era test asserted exactly 2 emissions. A real
            // WorkManager may emit an intermediate snapshot — e.g. downloader
            // SUCCEEDED before the finalizer flips to RUNNING — so only the
            // first emission and the eventual finalizer emission are pinned.)
            var finalizerEmission: WorkInfo? = null
            var attempts = 0
            while (finalizerEmission == null && attempts < 20) {
                delay(50)
                finalizerEmission =
                    results.firstOrNull {
                        it.id == finalizeRequest.id && it.state == WorkInfo.State.RUNNING
                    }
                attempts++
            }
            assertNotNull(finalizerEmission, "expected a RUNNING finalizer emission after downloader success")

            job.cancel()
        }

    // ===== REPO_09: Initial enqueue — downloader returned even if finalizer is listed first =====

    @Test
    fun `REPO_09 - getWorkId returns downloader when finalizer listed first at initial enqueue`() =
        runTest {
            // Both workers identified by class-name tags; no outputData stage set yet (initial enqueue).
            // The downloader is constraint-held ENQUEUED (never driven) and the finalizer is
            // BLOCKED behind it — the selection must still prefer the enqueued downloader.
            // (With a real WorkManager the result-list order is the database's, not the
            // test's; the fake-era "finalizer listed first" arrangement is expressed here
            // as the same two states coexisting under the unique name.)
            val downloadRequest = constrainedDownloadRequest()
            val finalizeRequest = finalizeRequest()
            enqueueChain(downloadRequest, finalizeRequest)

            val result = repository.getWorkId()

            assertEquals(downloadRequest.id, result)
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `REPO_10 - observeDownloadProgress emits downloader at initial enqueue regardless of list order`() =
        runTest {
            val downloadRequest = constrainedDownloadRequest()
            val finalizeRequest = finalizeRequest()
            enqueueChain(downloadRequest, finalizeRequest)

            val result = repository.observeDownloadProgress().first()

            assertNotNull(result)
            assertEquals(downloadRequest.id, result!!.id)
            assertEquals(WorkInfo.State.ENQUEUED, result.state)
        }

    // ===== Helper methods =====

    private fun stageOutput(
        sessionId: String,
        workerStage: String,
    ): Data =
        Data
            .Builder()
            .putString(DownloadWorkKeys.KEY_SESSION_ID, sessionId)
            .putString(DownloadWorkKeys.KEY_WORKER_STAGE, workerStage)
            .build()

    private fun downloadRequest(): OneTimeWorkRequest =
        OneTimeWorkRequestBuilder<ModelDownloadWorker>()
            .addTag(ModelConfig.WORK_TAG)
            .addTag(ModelDownloadWorker::class.java.name)
            .build()

    private fun constrainedDownloadRequest(): OneTimeWorkRequest =
        OneTimeWorkRequestBuilder<ModelDownloadWorker>()
            .addTag(ModelConfig.WORK_TAG)
            .addTag(ModelDownloadWorker::class.java.name)
            .setConstraints(
                Constraints
                    .Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            ).build()

    private fun finalizeRequest(): OneTimeWorkRequest =
        OneTimeWorkRequestBuilder<DownloadFinalizeWorker>()
            .addTag(ModelConfig.WORK_TAG)
            .addTag(DownloadFinalizeWorker::class.java.name)
            .build()

    private fun enqueueChain(
        downloadRequest: OneTimeWorkRequest,
        finalizeRequest: OneTimeWorkRequest,
    ) {
        workManager
            .beginUniqueWork(ModelConfig.WORK_TAG, ExistingWorkPolicy.REPLACE, downloadRequest)
            .then(finalizeRequest)
            .enqueue()
            .result
            .get()
    }

    private fun enqueueSingle(request: OneTimeWorkRequest) {
        workManager
            .enqueueUniqueWork(ModelConfig.WORK_TAG, ExistingWorkPolicy.REPLACE, request)
            .result
            .get()
    }

    /**
     * [WorkerFactory] scripting worker outcomes per class name: an immediate
     * success with fixed output, or a held worker whose future the test
     * completes by hand (which is what keeps its WorkInfo in RUNNING).
     */
    private class ScriptedWorkerFactory : WorkerFactory() {
        private val immediateOutputs = mutableMapOf<String, Data>()
        private val heldClasses = mutableSetOf<String>()
        private val pendingFutures =
            mutableMapOf<String, SettableFuture<ListenableWorker.Result>>()

        fun succeed(
            workerClassName: String,
            output: Data,
        ) {
            immediateOutputs[workerClassName] = output
        }

        fun hold(workerClassName: String) {
            heldClasses += workerClassName
        }

        fun complete(
            workerClassName: String,
            output: Data,
        ) {
            pendingFutures
                .remove(workerClassName)
                ?.set(ListenableWorker.Result.success(output))
        }

        override fun createWorker(
            appContext: Context,
            workerClassName: String,
            workerParameters: WorkerParameters,
        ): ListenableWorker =
            object : ListenableWorker(appContext, workerParameters) {
                override fun startWork(): ListenableFuture<ListenableWorker.Result> =
                    if (workerClassName in heldClasses) {
                        SettableFuture
                            .create<ListenableWorker.Result>()
                            .also { pendingFutures[workerClassName] = it }
                    } else {
                        Futures.immediateFuture(
                            ListenableWorker.Result.success(
                                immediateOutputs[workerClassName] ?: Data.EMPTY,
                            ),
                        )
                    }
            }
    }
}
