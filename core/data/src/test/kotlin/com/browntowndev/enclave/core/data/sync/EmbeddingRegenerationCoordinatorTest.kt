package com.browntowndev.enclave.core.data.sync

import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class EmbeddingRegenerationCoordinatorTest {
    private val workManager: WorkManager = mockk(relaxed = true)
    private val testScope = kotlinx.coroutines.test.TestScope()
    private lateinit var coordinator: EmbeddingRegenerationCoordinator

    @BeforeEach
    fun setup() {
        coordinator = EmbeddingRegenerationCoordinator(workManager, testScope)
    }

    private fun advancePastDebounce() = testScope.testScheduler.advanceTimeBy(501)

    @Test
    fun `enqueueMessage adds to in-memory queue and triggers worker`() =
        runTest {
            coordinator.enqueueMessage("msg1")
            // The debounce delays worker scheduling by 500ms; advance past the window.
            advancePastDebounce()

            val pending = coordinator.consumePending()
            assertTrue(pending.messageIds.contains("msg1"))
            verify {
                workManager.enqueueUniqueWork(
                    "enclave_embedding_regen",
                    ExistingWorkPolicy.KEEP,
                    any<OneTimeWorkRequest>(),
                )
            }
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
            verify {
                workManager.enqueueUniqueWork(
                    "enclave_embedding_regen",
                    ExistingWorkPolicy.KEEP,
                    any<OneTimeWorkRequest>(),
                )
            }
        }

    @Test
    fun `burst enqueues schedule only one unique work request until worker finishes`() =
        runTest {
            coordinator.enqueueMessage("msg1")
            coordinator.enqueueMessage("msg2")
            coordinator.enqueueMemory("mem1")
            advancePastDebounce()

            verify(exactly = 1) {
                workManager.enqueueUniqueWork(
                    "enclave_embedding_regen",
                    ExistingWorkPolicy.KEEP,
                    any<OneTimeWorkRequest>(),
                )
            }

            coordinator.consumePending()
            coordinator.onWorkerFinished()

            coordinator.enqueueDocument("doc1")
            advancePastDebounce()

            verify(exactly = 2) {
                workManager.enqueueUniqueWork(
                    "enclave_embedding_regen",
                    ExistingWorkPolicy.KEEP,
                    any<OneTimeWorkRequest>(),
                )
            }
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
