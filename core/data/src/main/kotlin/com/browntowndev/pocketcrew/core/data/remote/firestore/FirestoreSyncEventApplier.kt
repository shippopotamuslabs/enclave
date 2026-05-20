package com.browntowndev.pocketcrew.core.data.remote.firestore

/**
 * Narrow seam for the data source to dispatch ordered sync events to the applier. Tests stub
 * this interface so the production [FirestoreSyncEventApplier] stays final.
 */
internal interface FirestoreSyncEventDispatcher {
    suspend fun apply(
        userId: String,
        event: FirestoreSyncEvent,
    ): FirestoreSyncEventApplier.ApplyResult
}

/**
 * Applies a single ordered sync event into the local source of truth.
 *
 * Algorithm per upsert event:
 * 1. Ignore events whose sequence is <= the persisted `lastAppliedSequence`.
 * 2. Fetch the event's source document at `entityPath`.
 * 3. Resolve required parents declared by [FirestoreSyncDependencyGraph]; if a required
 *    parent source document is missing, throw [IllegalStateException] with the exact
 *    message `"missing required parent COLLECTION/ID for COLLECTION/ID"` and do not advance
 *    the sequence.
 * 4. Upsert parents (deepest-first) and the child inside a single local-store transaction.
 * 5. Advance `lastAppliedSequence` only after the transaction commits.
 *
 * Delete events skip parent resolution and call the local-store delete path. Sequence is
 * advanced after the deletion commits.
 */
internal class FirestoreSyncEventApplier(
    private val sourceDocuments: FirestoreSyncSourceDocumentStore = ThrowingFirestoreSyncSourceDocumentStore,
    private val localStore: FirestoreSyncLocalStore = ThrowingFirestoreSyncLocalStore,
    private val stateStore: FirestoreSyncStateStore = FirestoreSyncStateStore(),
) : FirestoreSyncEventDispatcher {
    data class ApplyResult(
        val appliedCount: Int,
        val lastAppliedSequence: Long,
    )

    override suspend fun apply(
        userId: String,
        event: FirestoreSyncEvent,
    ): ApplyResult {
        val currentSequence = stateStore.lastAppliedSequence(userId)
        if (event.sequence <= currentSequence) {
            return ApplyResult(appliedCount = 0, lastAppliedSequence = currentSequence)
        }
        val expectedSequence = currentSequence + 1
        if (event.sequence != expectedSequence) {
            throw IllegalStateException(
                "sync event sequence gap: expected $expectedSequence but received ${event.sequence}",
            )
        }

        val applied =
            when (event.operation) {
                "delete" -> {
                    applyDelete(userId, event)
                    true
                }
                else -> applyUpsert(userId, event)
            }

        stateStore.setLastAppliedSequence(userId, event.sequence)
        return ApplyResult(
            appliedCount = if (applied) 1 else 0,
            lastAppliedSequence = event.sequence,
        )
    }

    private suspend fun applyUpsert(
        userId: String,
        event: FirestoreSyncEvent,
    ): Boolean {
        // Resolve the dependency closure first so we fail BEFORE opening the transaction
        // when a required parent source document is missing — leaving the local store
        // and the sequence both untouched. A missing root source means this upsert was
        // superseded by a later delete/recreate race; advance past the stale pointer.
        val resolutionOrder = mutableListOf<ResolvedDocument>()
        val seen = mutableSetOf<String>()
        val rootExists =
            resolveDocument(
                userId = userId,
                collectionPath = event.collectionPath,
                entityId = event.entityId,
                requesterPath = "${event.collectionPath}/${event.entityId}",
                resolutionOrder = resolutionOrder,
                seen = seen,
            )
        if (!rootExists) {
            return false
        }

        // resolutionOrder is parents-first because the recursion appends the requested
        // document last. Apply inside a single transaction so a constraint failure rolls
        // the whole event back.
        localStore.withTransaction {
            for (doc in resolutionOrder) {
                val originDeviceId =
                    doc.data[SYNC_DEVICE_ID_FIELD] as? String ?: event.deviceId
                localStore.upsert(
                    collectionPath = doc.collectionPath,
                    entityId = doc.entityId,
                    data = doc.data,
                    eventDeviceId = originDeviceId,
                )
            }
        }
        return true
    }

    private suspend fun applyDelete(
        userId: String,
        event: FirestoreSyncEvent,
    ) {
        @Suppress("UnusedPrivateMember")
        val unused = userId
        localStore.withTransaction {
            localStore.delete(event.collectionPath, event.entityId)
        }
    }

    private suspend fun resolveDocument(
        userId: String,
        collectionPath: String,
        entityId: String,
        requesterPath: String,
        resolutionOrder: MutableList<ResolvedDocument>,
        seen: MutableSet<String>,
    ): Boolean {
        val key = "$collectionPath/$entityId"
        if (!seen.add(key)) return true
        val path = "users/$userId/$collectionPath/$entityId"
        val source =
            sourceDocuments.getDocument(path)
                ?: if (requesterPath == key) {
                    return false
                } else {
                    throw IllegalStateException("missing required parent $key for $requesterPath")
                }
        // Resolve parents recursively before queuing this document.
        val parents = FirestoreSyncDependencyGraph.requiredParents(collectionPath, source)
        for (parent in parents) {
            resolveDocument(
                userId = userId,
                collectionPath = parent.collectionPath,
                entityId = parent.entityId,
                requesterPath = key,
                resolutionOrder = resolutionOrder,
                seen = seen,
            )
        }
        resolutionOrder.add(ResolvedDocument(collectionPath, entityId, source))
        return true
    }

    private data class ResolvedDocument(
        val collectionPath: String,
        val entityId: String,
        val data: Map<String, Any?>,
    )
}

internal interface FirestoreSyncSourceDocumentStore {
    suspend fun getDocument(path: String): Map<String, Any?>?
}

private const val SYNC_DEVICE_ID_FIELD = "_sync_device_id"

internal interface FirestoreSyncLocalStore {
    suspend fun upsert(
        collectionPath: String,
        entityId: String,
        data: Map<String, Any?>,
        eventDeviceId: String? = null,
    )

    suspend fun delete(
        collectionPath: String,
        entityId: String,
    )

    suspend fun withTransaction(block: suspend () -> Unit)
}

private object ThrowingFirestoreSyncSourceDocumentStore : FirestoreSyncSourceDocumentStore {
    override suspend fun getDocument(path: String): Map<String, Any?>? {
        throw IllegalStateException("FirestoreSyncSourceDocumentStore not wired in this context")
    }
}

private object ThrowingFirestoreSyncLocalStore : FirestoreSyncLocalStore {
    override suspend fun upsert(
        collectionPath: String,
        entityId: String,
        data: Map<String, Any?>,
        eventDeviceId: String?,
    ) {
        throw IllegalStateException("FirestoreSyncLocalStore not wired in this context")
    }

    override suspend fun delete(
        collectionPath: String,
        entityId: String,
    ) {
        throw IllegalStateException("FirestoreSyncLocalStore not wired in this context")
    }

    override suspend fun withTransaction(block: suspend () -> Unit) {
        throw IllegalStateException("FirestoreSyncLocalStore not wired in this context")
    }
}
