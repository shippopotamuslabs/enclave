package com.browntowndev.enclave.core.data.remote.firestore

import com.browntowndev.enclave.core.testing.FakeCryptoManagerPort
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

/**
 * Listener lifecycle contract: a stale [FirestoreListenerCoordinator.stopListening] coroutine
 * must not detach a listener attached by a newer [FirestoreListenerCoordinator.startListening].
 */
@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(RobolectricExtension::class)
@Config(sdk = [34])
class FirestoreListenerCoordinatorLifecycleTest {
    @Test
    fun staleStopDoesNotDetachNewerStart() =
        runTest {
            val testDispatcher = StandardTestDispatcher(testScheduler)
            val externalScope = TestScope(testDispatcher)
            val gateway = FakeFeedQueryGateway()
            val feedClient = FirestoreSyncEventFeedClient(gateway = gateway)

            val coordinator =
                buildCoordinator(
                    externalScope = externalScope,
                    feedClient = feedClient,
                )

            coordinator.stopListening()
            coordinator.startListening()
            externalScope.advanceUntilIdle()
            advanceUntilIdle()
            runCurrent()

            assertEquals(1, gateway.listenCalls.size)
            assertEquals(0, gateway.registrations.single().removeCount)
        }

    @Test
    fun staleStartDoesNotAttachAfterNewerStop() =
        runTest {
            val testDispatcher = StandardTestDispatcher(testScheduler)
            val externalScope = TestScope(testDispatcher)
            val gateway = FakeFeedQueryGateway()
            val feedClient = FirestoreSyncEventFeedClient(gateway = gateway)

            val coordinator =
                buildCoordinator(
                    externalScope = externalScope,
                    feedClient = feedClient,
                )

            coordinator.startListening()
            coordinator.stopListening()
            externalScope.advanceUntilIdle()
            advanceUntilIdle()
            runCurrent()

            assertEquals(0, gateway.listenCalls.size)
            assertEquals(0, gateway.registrations.sumOf { it.removeCount })
        }

    @Test
    fun detachAllListenersForceDetachesRegardlessOfPendingOperations() =
        runTest {
            val testDispatcher = StandardTestDispatcher(testScheduler)
            val externalScope = TestScope(testDispatcher)
            val gateway = FakeFeedQueryGateway()
            val feedClient = FirestoreSyncEventFeedClient(gateway = gateway)

            val coordinator =
                buildCoordinator(
                    externalScope = externalScope,
                    feedClient = feedClient,
                )

            coordinator.startListening()
            externalScope.advanceUntilIdle()
            advanceUntilIdle()
            runCurrent()

            coordinator.detachAllListeners()
            advanceUntilIdle()

            assertEquals(1, gateway.registrations.single().removeCount)
        }

    private fun buildCoordinator(
        externalScope: kotlinx.coroutines.CoroutineScope,
        feedClient: FirestoreSyncEventFeedClient,
    ): FirestoreListenerCoordinator {
        val cryptoManager = FakeCryptoManagerPort()
        cryptoManager.hasCachedKeyValue = true
        val ctx =
            testSyncContext(
                cryptoManager = cryptoManager,
                userId = "test-user-id",
            )
        return FirestoreListenerCoordinator(
            ctx = ctx,
            syncEventFeedClient = feedClient,
            syncEventApplier = RecordingSyncEventDispatcher(),
            syncStateStore = FirestoreSyncStateStore(),
            externalScope = externalScope,
        )
    }
}
