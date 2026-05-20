package com.browntowndev.pocketcrew.core.data.remote.firestore

import android.database.sqlite.SQLiteConstraintException
import com.browntowndev.pocketcrew.core.data.remote.EntitySyncAdapter
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await

internal class FirestoreListenerCoordinator(
    private val ctx: FirestoreSyncContext,
    private val adapters: List<EntitySyncAdapter<*>>,
    private val externalScope: CoroutineScope,
) {
    private val listenerRegistrations = mutableListOf<ListenerRegistration>()
    private val listenerMutex = Mutex()
    private val processMutex = Mutex()
    private val retryQueue =
        PendingDocumentRetryQueue { key, cause ->
            ctx.loggingPort.error(
                FirestoreSyncContext.TAG,
                "Dropping Firestore document after exhausted retries: $key",
                cause,
            )
        }

    fun startListening() {
        externalScope.launch {
            listenerMutex.withLock {
                if (listenerRegistrations.isNotEmpty()) {
                    ctx.loggingPort.debug(FirestoreSyncContext.TAG, "startListening: Already listening, skipping")
                    return@withLock
                }

                if (!ctx.cryptoManager.hasCachedKey()) {
                    ctx.loggingPort.debug(FirestoreSyncContext.TAG, "startListening: Vault is locked, skipping")
                    return@withLock
                }

                val userId = ctx.userIdProvider() ?: return@withLock
                ctx.loggingPort.debug(FirestoreSyncContext.TAG, "startListening: Starting listeners for UID: $userId")

                ctx.cleanupOrphanedPlaceholderChats()

                adapters.forEach { adapter ->
                    val listener =
                        ctx.firestore
                            .collection("users/$userId/${adapter.collectionPath}")
                            .addSnapshotListener { snapshots, error ->
                                if (error != null) {
                                    ctx.loggingPort.error(
                                        FirestoreSyncContext.TAG,
                                        "${adapter.collectionPath} listener error",
                                        error,
                                    )
                                    return@addSnapshotListener
                                }
                                if (snapshots == null) return@addSnapshotListener

                                externalScope.launch {
                                    for (documentChange in snapshots.documentChanges) {
                                        processDocumentChangeSafely(adapter, documentChange)
                                    }
                                }
                            }
                    listenerRegistrations.add(listener)
                }
                ctx.loggingPort.debug(FirestoreSyncContext.TAG, "startListening: All listeners started")
            }
        }
    }

    fun stopListening() {
        externalScope.launch {
            listenerMutex.withLock {
                listenerRegistrations.forEach { it.remove() }
                listenerRegistrations.clear()
            }
        }
    }

    suspend fun detachAllListeners() {
        listenerMutex.withLock {
            listenerRegistrations.forEach { it.remove() }
            listenerRegistrations.clear()
        }
    }

    private suspend fun <T> processDocumentChangeSafely(
        adapter: EntitySyncAdapter<T>,
        documentChange: DocumentChange,
    ) {
        // Serialize across all adapter listeners so DB writes apply in arrival order. This
        // gives the retry queue a fair shot at re-running buffered children right after the
        // parent row commits, instead of racing concurrent listener coroutines.
        processMutex.withLock {
            val key = "${adapter.collectionPath}/${documentChange.document.id}"
            try {
                processDocumentChange(adapter, documentChange)
                retryQueue.drain()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (fk: SQLiteConstraintException) {
                ctx.loggingPort.debug(
                    FirestoreSyncContext.TAG,
                    "Buffering $key for retry: parent row not yet present (${fk.message})",
                )
                retryQueue.enqueue(key) { processDocumentChange(adapter, documentChange) }
            } catch (failure: Exception) {
                val wrapped =
                    FirestoreDocumentProcessingException(
                        collectionPath = adapter.collectionPath,
                        documentId = documentChange.document.id,
                        cause = failure,
                    )
                ctx.loggingPort.error(
                    FirestoreSyncContext.TAG,
                    wrapped.message ?: "Firestore document processing failed",
                    wrapped,
                )
            }
        }
    }

    private suspend fun <T> processDocumentChange(
        adapter: EntitySyncAdapter<T>,
        documentChange: DocumentChange,
    ) {
        val doc = documentChange.document
        when (documentChange.type) {
            DocumentChange.Type.ADDED -> {
                repairRemoteDocument(adapter.collectionPath, doc)
                adapter.fromFirestore(doc)?.let { entity ->
                    adapter.upsertLocal(entity, doc, overwrite = false)
                }
            }
            DocumentChange.Type.MODIFIED -> {
                repairRemoteDocument(adapter.collectionPath, doc)
                adapter.fromFirestore(doc)?.let { entity ->
                    adapter.upsertLocal(entity, doc, overwrite = true)
                }
            }
            DocumentChange.Type.REMOVED -> {
                adapter.deleteLocal(doc.id)
            }
        }
    }

    private suspend fun repairRemoteDocument(
        collectionPath: String,
        doc: DocumentSnapshot,
    ) {
        val data = doc.data ?: return
        val repaired = ctx.firestoreSensitiveFieldRepairer.repairInbound(collectionPath, doc.id, data)
        if (repaired != data) {
            doc.reference.set(repaired, SetOptions.merge()).await()
        }
    }
}
