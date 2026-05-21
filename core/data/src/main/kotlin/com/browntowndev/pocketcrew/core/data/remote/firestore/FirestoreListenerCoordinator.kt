package com.browntowndev.pocketcrew.core.data.remote.firestore

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Owns the single live `users/{uid}/sync_events` listener. Entity collection listeners were
 * removed in favor of the server-sequenced event feed (FEAT-cloud-sync-event-feed).
 */
internal class FirestoreListenerCoordinator(
    private val ctx: FirestoreSyncContext,
    private val syncEventFeedClient: FirestoreSyncEventFeedClient,
    private val syncEventApplier: FirestoreSyncEventDispatcher,
    private val syncStateStore: FirestoreSyncStateStore,
    private val eventApplyMutex: Mutex = Mutex(),
    private val externalScope: CoroutineScope,
) {
    private val listenerMutex = Mutex()
    private var eventListenerRegistration: FirestoreSyncEventListenerRegistration? = null

    fun startListening() {
        externalScope.launch {
            try {
                listenerMutex.withLock {
                    if (eventListenerRegistration != null) {
                        ctx.loggingPort.debug(
                            FirestoreSyncContext.TAG,
                            "startListening: sync_events listener already attached",
                        )
                        return@withLock
                    }
                    if (!ctx.cryptoManager.hasCachedKey()) {
                        ctx.loggingPort.debug(
                            FirestoreSyncContext.TAG,
                            "startListening: Vault is locked, skipping",
                        )
                        return@withLock
                    }
                    val userId = ctx.userIdProvider() ?: return@withLock
                    ctx.loggingPort.debug(
                        FirestoreSyncContext.TAG,
                        "startListening: Attaching sync_events listener for UID: $userId",
                    )
                    ctx.cleanupOrphanedPlaceholderChats()
                    val lastApplied = syncStateStore.lastAppliedSequence(userId)
                    eventListenerRegistration =
                        syncEventFeedClient.attachLiveListener(userId, lastApplied) { events ->
                            eventApplyMutex.withLock {
                                for (event in events) {
                                    syncEventApplier.apply(userId, event)
                                }
                            }
                        }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Throwable) {
                ctx.loggingPort.error(
                    FirestoreSyncContext.TAG,
                    "startListening: failed to attach sync_events listener",
                    failure,
                )
            }
        }
    }

    fun stopListening() {
        externalScope.launch {
            detachAllListeners()
        }
    }

    suspend fun detachAllListeners() {
        listenerMutex.withLock {
            eventListenerRegistration?.remove()
            eventListenerRegistration = null
        }
    }
}
