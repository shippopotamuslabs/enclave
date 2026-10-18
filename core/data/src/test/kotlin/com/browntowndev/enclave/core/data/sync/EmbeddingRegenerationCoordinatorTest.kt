package com.browntowndev.enclave.core.data.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [34])
class EmbeddingRegenerationCoordinatorTest {
    private lateinit var workManager: WorkManager
    private val testScope = kotlinx.coroutines.test.TestScope()
    private lateinit var coordinator: EmbeddingRegenerationCoordinator

    @BeforeEach
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        // Both executors synchronous (the DownloadWorkScheduler / TestDoubles house
        // pattern): enqueued work stays ENQUEUED under the test driver, so scheduling
        // is observable as WorkInfo state on the real WorkManager. A no-op main
        // executor drops WorkManager's main-thread state/constraint posts and lets
        // work start mid-test, corrupting those observations.
        val config =
            Configuration
                .Builder()
                .setTaskExecutor(androidx.work.impl.utils.SynchronousExecutor())
                .setExecutor(androidx.work.impl.utils.SynchronousExecutor())
                .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)
        workManager = WorkManager.getInstance(context)
        coordinator = EmbeddingRegenerationCoordinator(workManager, testScope)
    }

    private fun advancePastDebounce() = testScope.testScheduler.advanceTimeBy(501)

    private fun regenWork(): List<WorkInfo> = workManager.getWorkInfosForUniqueWork("enclave_embedding_regen").get()

    @Test
    fun `enqueueMessage adds to in-memory queue and triggers worker`() =
        runTest {
            coordinator.enqueueMessage("msg1")
            // The debounce delays worker scheduling by 500ms; advance past the window.
            advancePastDebounce()

            val pending = coordinator.consumePending()
            assertTrue(pending.messageIds.contains("msg1"))
            val work = regenWork()
            assertEquals(1, work.size)
            assertTrue(work.single().tags.contains(EmbeddingRegenerationWorker::class.java.name))
        }

    @Test
    fun `duplicate enqueueMessage calls collapse`() =
        runTest {
            coordinator.enqueueMessage("msg1")
            coordinator.enqueueMessage("msg1")
            // No need to advance time for this assertion — we just check in-memory deduplication.
            advancePastDebounce()

            val pending = coordinator.consumePending()
            assertEquals(1, pending.messageIds.size)
            assertEquals("msg1", pending.messageIds.first())
        }

    @Test
    fun `enqueue for memory and message route to correct sets`() =
        runTest {
            coordinator.enqueueMessage("msg1")
            coordinator.enqueueMemory("mem1")
            advancePastDebounce()

            val pending = coordinator.consumePending()
            assertEquals(listOf("msg1"), pending.messageIds)
            assertEquals(listOf("mem1"), pending.memoryIds)
        }

    @Test
    fun `consumePending clears the queue`() =
        runTest {
            coordinator.enqueueMessage("msg1")
            advancePastDebounce()
            coordinator.consumePending()

            val pendingAfter = coordinator.consumePending()
            assertTrue(pendingAfter.isEmpty())
        }

    @Test
    fun `markFailed re-enqueues items`() =
        runTest {
            val work = PendingWork(listOf("msg1"), listOf("mem1"), emptyList())
            coordinator.markFailed(work)
            // markFailed does NOT trigger a new debounce; items are just returned to the pending store.
            // No advancement needed to test that items are re-queued.

            val pending = coordinator.consumePending()
            assertTrue(pending.messageIds.contains("msg1"))
            assertTrue(pending.memoryIds.contains("mem1"))
        }

    @Test
    fun `enqueueFullRegeneration triggers worker and sets flag`() =
        runTest {
            coordinator.enqueueFullRegeneration()
            advancePastDebounce()

            val pending = coordinator.consumePending()
            assertTrue(pending.fullScanPending)
            assertEquals(1, regenWork().size)
        }

    @Test
    fun `burst enqueues schedule only one unique work request until worker finishes`() =
        runTest {
            coordinator.enqueueMessage("msg1")
            coordinator.enqueueMessage("msg2")
            coordinator.enqueueMemory("mem1")
            advancePastDebounce()

            // The whole burst produced exactly one unique work request.
            assertEquals(1, regenWork().size)
            val firstWorkId = regenWork().single().id

            coordinator.consumePending()
            // Simulate the scheduled worker having run to completion on the
            // WorkManager side, then notify the coordinator as production does.
            workManager.cancelUniqueWork("enclave_embedding_regen")
            coordinator.onWorkerFinished()

            coordinator.enqueueDocument("doc1")
            advancePastDebounce()

            // A second, distinct work request was scheduled after the worker finished.
            // Against a real WorkManager the finished request's WorkSpec row is
            // deleted by the follow-up enqueue (EnqueueRunnable prune), so the count
            // stays 1 — the FakeWorkManager-era expectation of 2 records cannot
            // occur. "Second request scheduled" is asserted in its observable form:
            // the single remaining record is ENQUEUED with a different id.
            val work = regenWork()
            assertEquals(1, work.size)
            assertEquals(WorkInfo.State.ENQUEUED, work.single().state)
            assertTrue(work.single().id != firstWorkId)
        }

    @Test
    fun `hasPendingWork reflects in-memory queue`() =
        runTest {
            assertEquals(false, coordinator.hasPendingWork())
            coordinator.enqueueMessage("msg1")
            advancePastDebounce()
            assertEquals(true, coordinator.hasPendingWork())
            coordinator.consumePending()
            assertEquals(false, coordinator.hasPendingWork())
        }
}
