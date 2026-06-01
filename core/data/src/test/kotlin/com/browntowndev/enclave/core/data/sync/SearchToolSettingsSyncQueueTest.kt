package com.browntowndev.enclave.core.data.sync

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

/**
 * Coverage for the `SearchToolSettingsSyncQueue` state machine against the scenarios
 * in `plans/search-tool-enable-sync/test_spec.md`. Uses a real
 * `PreferenceDataStoreFactory` backed by a `@TempDir` file, matching the handler test
 * pattern.
 */
class SearchToolSettingsSyncQueueTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `S-QUEUE-001 queue mark-claim-clear cycle`() =
        runTest {
            val queue = createQueue(backgroundScope)

            queue.markPendingSync()
            assertTrue(
                queue.isPending(),
                "After markPendingSync(), isPending() must return true",
            )

            val claimed = queue.claimPending()
            assertTrue(
                claimed,
                "claimPending() must return true when the queue was pending",
            )
            assertFalse(
                queue.isPending(),
                "After claimPending(), isPending() must return false",
            )

            queue.clearClaimedSync()
            assertFalse(
                queue.isPending(),
                "After clearClaimedSync(), isPending() must remain false",
            )
        }

    @Test
    fun `S-QUEUE-002 claimPending on empty queue`() =
        runTest {
            val queue = createQueue(backgroundScope)

            val claimed = queue.claimPending()

            assertFalse(claimed, "claimPending() on a fresh queue must return false")
            assertFalse(
                queue.isPending(),
                "isPending() on a fresh queue must return false",
            )
        }

    @Test
    fun `S-QUEUE-003 repeated markPendingSync is idempotent`() =
        runTest {
            val queue = createQueue(backgroundScope)

            queue.markPendingSync()
            queue.markPendingSync()
            queue.markPendingSync()

            assertTrue(
                queue.isPending(),
                "After three markPendingSync() calls, isPending() must return true",
            )
            assertTrue(
                queue.claimPending(),
                "A single subsequent claimPending() must return true and clear state",
            )
            assertFalse(
                queue.isPending(),
                "After a single claimPending(), isPending() must return false",
            )
            assertFalse(
                queue.claimPending(),
                "A second claimPending() after the first must return false",
            )
        }

    @Test
    fun `S-QUEUE-004 claimPending after clearClaimedSync returns false`() =
        runTest {
            val queue = createQueue(backgroundScope)
            queue.markPendingSync()
            // sanity: a pending flag exists
            assertTrue(queue.isPending())

            queue.claimPending()
            queue.clearClaimedSync()

            val secondClaim = queue.claimPending()
            assertFalse(
                secondClaim,
                "After markPendingSync → claimPending → clearClaimedSync, " +
                    "another claimPending() must return false",
            )
        }

    @Test
    fun `S-QUEUE-005 hasInFlightLocalEdit covers both pending and claimed states`() =
        runTest {
            val queue = createQueue(backgroundScope)

            // Fresh queue: no in-flight edit.
            assertFalse(
                queue.hasInFlightLocalEdit(),
                "Fresh queue must not report an in-flight edit",
            )

            // PENDING set: in-flight edit.
            queue.markPendingSync()
            assertTrue(
                queue.hasInFlightLocalEdit(),
                "After markPendingSync, hasInFlightLocalEdit must return true",
            )

            // PENDING cleared, CLAIMED_AT set (in-flight, claimed but not yet synced).
            queue.claimPending()
            assertFalse(
                queue.isPending(),
                "claimPending must clear PENDING",
            )
            assertTrue(
                queue.hasInFlightLocalEdit(),
                "After claimPending, hasInFlightLocalEdit must remain true (CLAIMED_AT is set)",
            )

            // Round-trip complete: no in-flight edit.
            queue.clearClaimedSync()
            assertFalse(
                queue.hasInFlightLocalEdit(),
                "After clearClaimedSync, hasInFlightLocalEdit must return false",
            )
        }

    private fun createQueue(scope: CoroutineScope): SearchToolSettingsSyncQueue =
        SearchToolSettingsSyncQueue(
            dataStore =
                PreferenceDataStoreFactory.create(
                    scope = scope,
                    produceFile = {
                        tempDir
                            .resolve("search-settings-${System.nanoTime()}.preferences_pb")
                            .toFile()
                    },
                ),
        )
}
