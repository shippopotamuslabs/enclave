package com.browntowndev.enclave.domain.usecase.security

import com.browntowndev.enclave.core.testing.FakeCloudSyncPort
import com.browntowndev.enclave.domain.model.sync.CloudSyncEventReplayResult
import com.browntowndev.enclave.domain.model.sync.CloudSyncReplayProgress
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class UnlockProgressTddRedTest {
    @Test
    fun `SCENARIO-16 Unlock sync progress is reported and cleared correctly`() =
        runTest {
            val port = FakeCloudSyncPort()

            // Remaining count = 300 events
            port.remainingEventsResult = Result.success(300L)

            // Page 1: 200 events applied
            // Page 2: 100 events applied, no more
            port.syncRemoteResults.addAll(
                listOf(
                    Result.success(CloudSyncEventReplayResult(eventsApplied = 200, lastAppliedSequence = 200L, hasMore = true)),
                    Result.success(CloudSyncEventReplayResult(eventsApplied = 100, lastAppliedSequence = 300L, hasMore = false)),
                ),
            )

            val useCase = StartCloudSyncListeningUseCase(port)
            val progressUpdates = mutableListOf<CloudSyncReplayProgress>()

            val result =
                useCase.invoke(uploadLocalFirst = false) { progress ->
                    progressUpdates.add(progress)
                }

            assertTrue(result.isSuccess)
            // Initial progress (fraction = 0, estimate = 300)
            val initial = progressUpdates[0]
            assertEquals(0L, initial.appliedCount)
            assertEquals(300L, initial.totalEstimate)

            // After page 1: 200 applied of 300 total = 0.6666...
            val afterPage1 = progressUpdates[1]
            assertEquals(200L, afterPage1.appliedCount)
            assertEquals(300L, afterPage1.totalEstimate)
            assertTrue(afterPage1.progressFraction != null)
            assertEquals("Syncing updates (200/300)...", afterPage1.status)

            // After page 2: 300 applied of 300 = 1.0
            val afterPage2 = progressUpdates[2]
            assertEquals(300L, afterPage2.appliedCount)
            assertEquals(300L, afterPage2.totalEstimate)
            assertEquals(1.0f, afterPage2.progressFraction)
            assertEquals("Syncing updates (300/300)...", afterPage2.status)
        }

    @Test
    fun `SCENARIO-17 Unlock progress handles changing or unavailable totals`() =
        runTest {
            val port = FakeCloudSyncPort()

            // Count returns 0 — indeterminate
            port.remainingEventsResult = Result.success(0L)

            // One page of events replayed successfully
            port.syncRemoteResult =
                Result.success(
                    CloudSyncEventReplayResult(eventsApplied = 50, lastAppliedSequence = 50L, hasMore = false),
                )

            val useCase = StartCloudSyncListeningUseCase(port)
            val progressUpdates = mutableListOf<CloudSyncReplayProgress>()

            val result =
                useCase.invoke(uploadLocalFirst = false) { progress ->
                    progressUpdates.add(progress)
                }

            assertTrue(result.isSuccess)

            // Initial state: indeterminate (no known total)
            val initial = progressUpdates[0]
            assertNull(initial.progressFraction)
            // Status should be useful without a false total
            assertTrue(
                initial.status.isNotBlank(),
                "Status should not be empty when count is 0",
            )

            // After replay page, fraction should remain null (no known total)
            val afterReplay = progressUpdates[1]
            assertNull(afterReplay.progressFraction)
            assertTrue(
                afterReplay.status.contains("Syncing"),
                "Status should mention syncing: ${afterReplay.status}",
            )

            // Listener should still have been started
            assertEquals(1, port.count("startListening"))
        }
}
