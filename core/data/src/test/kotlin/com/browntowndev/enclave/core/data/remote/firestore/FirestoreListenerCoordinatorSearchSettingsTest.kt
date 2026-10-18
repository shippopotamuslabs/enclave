package com.browntowndev.enclave.core.data.remote.firestore

import com.browntowndev.enclave.core.testing.FakeCryptoManagerPort
import com.browntowndev.enclave.domain.port.security.CryptoManagerPort
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

/**
 * Coverage for the vault-unlock gate on `FirestoreListenerCoordinator.startListening()`
 * with respect to the new `search_tool_settings` collection. The new handler rides
 * the existing `sync_events` live listener; we do NOT introduce a new gate. The
 * assertions look at whether the coordinator attached its sync_events listener
 * (which would subsequently route events through `FirestoreSyncEventDispatcher`
 * to entity handlers, including `SearchToolSettingsSyncHandler`).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(RobolectricExtension::class)
@Config(sdk = [34])
class FirestoreListenerCoordinatorSearchSettingsTest {
    @Test
    fun `S-LISTENER-001 listener does not apply search_tool_settings events while vault is locked`() =
        runTest {
            val testDispatcher = StandardTestDispatcher(testScheduler)
            val externalScope = TestScope(testDispatcher)
            val cryptoManager = FakeCryptoManagerPort()
            cryptoManager.hasCachedKeyValue = false

            val gateway = FakeFeedQueryGateway()
            val feedClient = FirestoreSyncEventFeedClient(gateway = gateway)
            val dispatcher = RecordingSyncEventDispatcher()
            val stateStore = FirestoreSyncStateStore()

            val coordinator =
                buildCoordinator(
                    externalScope = externalScope,
                    cryptoManager = cryptoManager,
                    feedClient = feedClient,
                    dispatcher = dispatcher,
                    stateStore = stateStore,
                )

            coordinator.startListening()
            externalScope.advanceUntilIdle()
            advanceUntilIdle()

            // Gate contract: when the vault is locked, attachLiveListener is never
            // called. This guarantees the search_tool_settings handler (and every
            // other handler downstream of the sync_events feed) is not invoked.
            assertEquals(0, gateway.listenCalls.size)
        }

    @Test
    fun `S-LISTENER-002 listener applies search_tool_settings events once the vault is unlocked`() =
        runTest {
            val testDispatcher = StandardTestDispatcher(testScheduler)
            val externalScope = TestScope(testDispatcher)
            // The first startListening() call must observe a locked vault; flipping
            // this knob to true mirrors the unlock before the second start.
            val cryptoManager = FakeCryptoManagerPort()
            cryptoManager.hasCachedKeyValue = false

            val gateway = FakeFeedQueryGateway()
            val feedClient = FirestoreSyncEventFeedClient(gateway = gateway)

            val dispatcher = RecordingSyncEventDispatcher()
            val stateStore = FirestoreSyncStateStore()

            val coordinator =
                buildCoordinator(
                    externalScope = externalScope,
                    cryptoManager = cryptoManager,
                    feedClient = feedClient,
                    dispatcher = dispatcher,
                    stateStore = stateStore,
                )

            // First start: vault is locked → no listener attached.
            coordinator.startListening()
            externalScope.advanceUntilIdle()
            advanceUntilIdle()
            assertEquals(0, gateway.listenCalls.size)

            // Unlock the vault and try again. The second startListening() call
            // observes hasCachedKey() == true and must attach the listener.
            cryptoManager.hasCachedKeyValue = true
            coordinator.startListening()
            externalScope.advanceUntilIdle()
            advanceUntilIdle()
            runCurrent()

            val callback = gateway.listenerCallbacks.singleOrNull()
            assertNotNull(callback, "after unlock, startListening must attach a listener")

            // Drive a synthetic live event for the search_tool_settings singleton.
            val syntheticEvent =
                FirestoreSyncEvent(
                    sequence = 1L,
                    schemaVersion = 1,
                    operation = "upsert",
                    collectionPath = "search_tool_settings",
                    entityType = "search_tool_settings",
                    entityId = "main",
                    entityPath = "users/test-user-id/search_tool_settings/main",
                    sourceExists = true,
                    sourceUpdatedAt = 1700000000000L,
                    sourceDeleted = false,
                    deviceId = "device-1",
                    clientMutationId = "mut-1",
                    triggerEventId = "evt-1",
                    emittedAtMillis = 1700000000000L,
                )
            callback?.invoke(listOf(syntheticEvent))
            externalScope.advanceUntilIdle()
            advanceUntilIdle()

            // The event must have been dispatched to the applier exactly once.
            assertEquals(listOf("test-user-id" to syntheticEvent), dispatcher.applyCalls)
        }

    private fun buildCoordinator(
        externalScope: kotlinx.coroutines.CoroutineScope,
        cryptoManager: CryptoManagerPort,
        feedClient: FirestoreSyncEventFeedClient,
        dispatcher: FirestoreSyncEventDispatcher,
        stateStore: FirestoreSyncStateStore,
    ): FirestoreListenerCoordinator {
        val ctx =
            testSyncContext(
                cryptoManager = cryptoManager,
                userId = "test-user-id",
            )
        return FirestoreListenerCoordinator(
            ctx = ctx,
            syncEventFeedClient = feedClient,
            syncEventApplier = dispatcher,
            syncStateStore = stateStore,
            externalScope = externalScope,
        )
    }
}
