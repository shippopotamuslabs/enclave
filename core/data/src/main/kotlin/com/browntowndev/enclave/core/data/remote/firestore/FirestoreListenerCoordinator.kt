package com.browntowndev.enclave.core.data.remote.firestore

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.TreeMap

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
    private val pendingLiveEvents = TreeMap<Long, FirestoreSyncEvent>()
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
                            applyLiveEvents(userId, events)
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
        eventApplyMutex.withLock {
            pendingLiveEvents.clear()
        }
    }

    private suspend fun applyLiveEvents(
        userId: String,
        events: List<FirestoreSyncEvent>,
    ) {
        eventApplyMutex.withLock {
            bufferFutureEvents(userId, events)
            drainContiguousEvents(userId)
        }
    }

    private suspend fun bufferFutureEvents(
        userId: String,
        events: List<FirestoreSyncEvent>,
    ) {
        val lastApplied = syncStateStore.lastAppliedSequence(userId)
        for (event in events) {
            if (event.sequence > lastApplied) {
                pendingLiveEvents[event.sequence] = event
            }
        }
    }

    private suspend fun drainContiguousEvents(userId: String) {
        var lastApplied = syncStateStore.lastAppliedSequence(userId)
        pruneAppliedEvents(lastApplied)
        while (true) {
            val nextSequence = lastApplied + 1
            val event = pendingLiveEvents.remove(nextSequence) ?: break
            val result =
                runCatching {
                    syncEventApplier.apply(userId, event)
                }.getOrElse { failure ->
                    pendingLiveEvents[nextSequence] = event
                    if (failure is CancellationException) throw failure
                    ctx.loggingPort.error(
                        FirestoreSyncContext.TAG,
                        "sync_events listener apply failed at sequence $nextSequence",
                        failure,
                    )
                    return
                }
            if (result.lastAppliedSequence > lastApplied) {
                lastApplied = result.lastAppliedSequence
                syncStateStore.setLastAppliedSequence(userId, lastApplied)
                pruneAppliedEvents(lastApplied)
            }
        }
        val nextPending = pendingLiveEvents.firstKeyOrNull()
        if (nextPending != null && nextPending > lastApplied + 1) {
            ctx.loggingPort.warning(
                FirestoreSyncContext.TAG,
                "sync_events listener waiting for sequence ${lastApplied + 1} before applying $nextPending",
            )
        }
    }

    private fun pruneAppliedEvents(lastApplied: Long) {
        while (true) {
            val firstKey = pendingLiveEvents.firstKeyOrNull() ?: return
            if (firstKey > lastApplied) return
            pendingLiveEvents.remove(firstKey)
        }
    }

    private fun TreeMap<Long, FirestoreSyncEvent>.firstKeyOrNull(): Long? = if (isEmpty()) null else firstKey()
}
