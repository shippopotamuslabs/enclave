package com.browntowndev.pocketcrew.core.data.remote.firestore

import androidx.room.withTransaction
import com.browntowndev.pocketcrew.core.data.local.PocketCrewDatabase
import com.browntowndev.pocketcrew.core.data.remote.EntitySyncAdapter
import com.browntowndev.pocketcrew.core.data.remote.FirestoreSensitiveFieldRepairer
import com.browntowndev.pocketcrew.core.data.sync.EmbeddingRegenerationCoordinator
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Production [FirestoreSyncEventQueryGateway] backed by Firestore. Builds the canonical
 * `users/{uid}/sync_events` query shape required by the spec
 * (`whereGreaterThan("sequence", lastAppliedSequence)`, `orderBy("sequence")`,
 * `limit(pageSize)`) and decodes documents through [FirestoreSyncEventCodec] so the rest
 * of the data layer never sees Firestore types.
 */
internal class FirestoreBackedSyncEventQueryGateway(
    private val firestore: FirebaseFirestore,
    private val externalScope: CoroutineScope,
    private val loggingPort: LoggingPort,
) : FirestoreSyncEventQueryGateway {
    override suspend fun fetchEvents(queryShape: FirestoreSyncEventFeedClient.QueryShape): List<FirestoreSyncEvent> {
        val snapshot = buildQuery(queryShape).get().await()
        return snapshot.documents.map { doc ->
            val data =
                doc.data
                    ?: throw IllegalArgumentException("sync event missing document data for ${doc.id}")
            FirestoreSyncEventCodec.parse(data)
        }
    }

    override fun listen(
        queryShape: FirestoreSyncEventFeedClient.QueryShape,
        onEvents: suspend (List<FirestoreSyncEvent>) -> Unit,
    ): FirestoreSyncEventListenerRegistration {
        val registration =
            buildQuery(queryShape).addSnapshotListener { snapshot, error ->
                if (error != null) {
                    loggingPort.error(FirestoreSyncContext.TAG, "sync_events listener error", error)
                    return@addSnapshotListener
                }
                if (snapshot == null) return@addSnapshotListener
                val events =
                    snapshot.documentChanges
                        .filter { change ->
                            change.type == com.google.firebase.firestore.DocumentChange.Type.ADDED ||
                                change.type == com.google.firebase.firestore.DocumentChange.Type.MODIFIED
                        }.map { change ->
                            FirestoreSyncEventCodec.parse(change.document.data)
                        }
                if (events.isEmpty()) return@addSnapshotListener
                externalScope.launch {
                    try {
                        onEvents(events)
                    } catch (cancellation: CancellationException) {
                        throw cancellation
                    } catch (failure: Throwable) {
                        loggingPort.error(
                            FirestoreSyncContext.TAG,
                            "sync_events listener dispatch failed",
                            failure,
                        )
                        throw failure
                    }
                }
            }
        return object : FirestoreSyncEventListenerRegistration {
            override fun remove() = registration.remove()
        }
    }

    private fun buildQuery(queryShape: FirestoreSyncEventFeedClient.QueryShape): Query {
        val lastApplied =
            queryShape.filter.substringAfter("sequence > ", "0").toLongOrNull() ?: 0L
        return firestore
            .collection(queryShape.collectionPath)
            .whereGreaterThan("sequence", lastApplied)
            .orderBy("sequence")
            .let { query ->
                if (queryShape.limit == FirestoreSyncEventFeedClient.UNBOUNDED_LIVE_LISTENER) {
                    query
                } else {
                    query.limit(queryShape.limit)
                }
            }
    }
}

/**
 * Production [FirestoreSyncSourceDocumentStore] backed by Firestore. Fetches the canonical
 * source document at `users/{uid}/{collectionPath}/{entityId}` so the applier can resolve
 * dependencies recursively without each handler reaching into Firestore directly.
 */
internal class FirestoreBackedSyncSourceDocumentStore(
    private val firestore: FirebaseFirestore,
) : FirestoreSyncSourceDocumentStore {
    override suspend fun getDocument(path: String): Map<String, Any?>? {
        val snapshot = firestore.document(path).get().await()
        if (!snapshot.exists()) return null
        return snapshot.data
    }
}

/**
 * Production [FirestoreSyncLocalStore] backed by Room. For each upsert, the store fetches
 * the canonical Firestore [DocumentSnapshot] (so adapter mappers can use the same
 * `DocumentSnapshot.getString/getLong/...` API they use under the snapshot-listener path),
 * runs [FirestoreSensitiveFieldRepairer.repairInbound], decodes via
 * [EntitySyncAdapter.fromFirestore], calls [EntitySyncAdapter.upsertLocal] inside a
 * Room transaction via [PocketCrewDatabase.withTransaction], and enqueues
 * [EmbeddingRegenerationCoordinator] for message/document content changes — mirroring the
 * existing per-collection handler behavior.
 */
internal class RoomFirestoreSyncLocalStore(
    private val database: PocketCrewDatabase,
    private val firestore: FirebaseFirestore,
    private val userIdProvider: () -> String?,
    private val adapterLookup: Map<String, EntitySyncAdapter<*>>,
    private val repairer: FirestoreSensitiveFieldRepairer,
    private val embeddingCoordinator: EmbeddingRegenerationCoordinator,
    private val loggingPort: LoggingPort,
    private val syncStateStore: FirestoreSyncStateStore,
    private val pendingSyncLookup: FirestoreSyncPendingSyncLookup,
) : FirestoreSyncLocalStore {
    override suspend fun upsert(
        collectionPath: String,
        entityId: String,
        data: Map<String, Any?>,
        eventDeviceId: String?,
    ) {
        val adapter =
            adapterLookup[collectionPath]
                ?: throw IllegalStateException("missing adapter for $collectionPath")
        val userId = userIdProvider() ?: return
        val docRef = firestore.document("users/$userId/$collectionPath/$entityId")
        val snapshot = docRef.get().await()
        if (!snapshot.exists()) {
            throw IllegalStateException(
                "source document missing at apply for $collectionPath/$entityId",
            )
        }
        val original = snapshot.data ?: data
        val repaired = repairer.repairInbound(collectionPath, entityId, original)
        val decodeSnapshot =
            if (repaired != original) {
                runCatching {
                    docRef.set(repaired, SetOptions.merge()).await()
                }.onFailure { failure ->
                    loggingPort.error(
                        FirestoreSyncContext.TAG,
                        "Failed to write back repaired doc $collectionPath/$entityId",
                        failure,
                    )
                }.getOrThrow()
                docRef.get().await()
            } else {
                snapshot
            }
        if (!decodeSnapshot.exists()) {
            throw IllegalStateException(
                "source document missing at apply for $collectionPath/$entityId",
            )
        }
        applyAdapterUpsert(
            adapter = adapter,
            snapshot = decodeSnapshot,
            collectionPath = collectionPath,
            entityId = entityId,
            eventDeviceId = eventDeviceId ?: decodeSnapshot.getString("_sync_device_id"),
        )
    }

    override suspend fun delete(
        collectionPath: String,
        entityId: String,
    ) {
        val adapter =
            adapterLookup[collectionPath]
                ?: throw IllegalStateException("missing adapter for $collectionPath")
        adapter.deleteLocal(entityId)
    }

    override suspend fun withTransaction(block: suspend () -> Unit) {
        database.withTransaction { block() }
    }

    private suspend fun <RoomT> applyAdapterUpsert(
        adapter: EntitySyncAdapter<RoomT>,
        snapshot: DocumentSnapshot,
        collectionPath: String,
        entityId: String,
        eventDeviceId: String?,
    ) {
        val localDeviceId = syncStateStore.deviceId()
        val pendingSync = pendingSyncLookup.pendingSync(collectionPath, entityId)
        val entity =
            adapter.fromFirestore(snapshot)
                ?: throw IllegalStateException(
                    "failed to decode $collectionPath/$entityId from Firestore",
                )
        val overwrite =
            FirestoreSyncRemoteApplyPolicy.overwriteForSameDeviceEcho(
                pendingSync = pendingSync,
                eventDeviceId = eventDeviceId,
                localDeviceId = localDeviceId,
            )
        adapter.upsertLocal(entity, snapshot, overwrite = overwrite)
        when (collectionPath) {
            "messages" -> embeddingCoordinator.enqueueMessage(entityId)
            "documents" -> embeddingCoordinator.enqueueDocument(entityId)
            else -> Unit
        }
    }
}
