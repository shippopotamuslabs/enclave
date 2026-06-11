package com.browntowndev.enclave.core.data.remote.firestore

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.TreeMap
import java.util.concurrent.atomic.AtomicLong

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
    private val cache: FirestoreSyncDocumentCache? = null,
    private val feedIntegrityRecovery: FirestoreSyncFeedIntegrityRecovery? = null,
) {
    private val listenerMutex = Mutex()
    private val pendingLiveEvents = TreeMap<Long, FirestoreSyncEvent>()
    private var eventListenerRegistration: FirestoreSyncEventListenerRegistration? = null
    private val listenerOperationSeq = AtomicLong(0)
    private var listenerDesiredAttached = false

    fun startListening() {
        val operation = listenerOperationSeq.incrementAndGet()
        listenerDesiredAttached = true
        externalScope.launch {
            applyListenerOperation(operation)
        }
    }

    fun stopListening() {
        val operation = listenerOperationSeq.incrementAndGet()
        listenerDesiredAttached = false
        externalScope.launch {
            applyListenerOperation(operation)
        }
    }

    suspend fun detachAllListeners() {
        listenerOperationSeq.incrementAndGet()
        listenerDesiredAttached = false
        listenerMutex.withLock {
            eventListenerRegistration?.remove()
            eventListenerRegistration = null
        }
        eventApplyMutex.withLock {
            pendingLiveEvents.clear()
        }
    }

    private suspend fun applyListenerOperation(operation: Long) {
        try {
            listenerMutex.withLock {
                if (operation != listenerOperationSeq.get()) {
                    return@withLock
                }
                if (listenerDesiredAttached) {
                    attachListenerIfNeeded()
                } else {
                    detachListenerIfCurrent()
                }
            }
            if (!listenerDesiredAttached && operation == listenerOperationSeq.get()) {
                eventApplyMutex.withLock {
                    if (operation == listenerOperationSeq.get()) {
                        pendingLiveEvents.clear()
                    }
                }
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Throwable) {
            ctx.loggingPort.error(
                FirestoreSyncContext.TAG,
                "applyListenerOperation: failed for attach=$listenerDesiredAttached",
                failure,
            )
        }
    }

    private suspend fun attachListenerIfNeeded() {
        if (eventListenerRegistration != null) {
            ctx.loggingPort.debug(
                FirestoreSyncContext.TAG,
                "startListening: sync_events listener already attached",
            )
            return
        }
        if (!ctx.cryptoManager.hasCachedKey()) {
            ctx.loggingPort.debug(
                FirestoreSyncContext.TAG,
                "startListening: Vault is locked, skipping",
            )
            return
        }
        val userId = ctx.userIdProvider() ?: return
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

    private fun detachListenerIfCurrent() {
        eventListenerRegistration?.remove()
        eventListenerRegistration = null
    }

    private suspend fun applyLiveEvents(
        userId: String,
        events: List<FirestoreSyncEvent>,
    ) {
        eventApplyMutex.withLock {
            bufferFutureEvents(userId, events)

            val lastApplied = syncStateStore.lastAppliedSequence(userId)
            val contiguousEvents = mutableListOf<FirestoreSyncEvent>()
            var seq = lastApplied + 1
            while (true) {
                val event = pendingLiveEvents[seq] ?: break
                contiguousEvents.add(event)
                seq++
            }
            if (contiguousEvents.isNotEmpty()) {
                cache?.prefetch(userId, contiguousEvents)
            }

            try {
                drainContiguousEvents(userId)
            } finally {
                cache?.clear()
            }
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
                    if (failure is SyncFeedSequenceGapException) {
                        val recovery = feedIntegrityRecovery
                        if (recovery == null) {
                            pendingLiveEvents[nextSequence] = event
                            throw failure
                        }
                        recovery.recoverFromSequenceGap(userId, failure.expectedSequence)
                        return
                    }
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
