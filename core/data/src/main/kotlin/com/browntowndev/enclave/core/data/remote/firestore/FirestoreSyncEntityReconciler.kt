package com.browntowndev.enclave.core.data.remote.firestore

import com.browntowndev.enclave.core.data.remote.EntitySyncAdapter
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await

internal class FirestoreSyncEntityReconciler(
    private val firestore: FirebaseFirestore,
    private val ctx: FirestoreSyncContext,
    private val adapterLookup: Map<String, EntitySyncAdapter<*>>,
    private val localStore: FirestoreSyncLocalStore,
    private val pendingSyncLookup: FirestoreSyncPendingSyncLookup,
    private val loggingPort: LoggingPort,
) {
    data class ReconcileResult(
        val upsertsApplied: Int,
        val tombstonesApplied: Int,
    )

    suspend fun reconcileFromCloudEntitySources(userId: String): ReconcileResult {
        var upsertsApplied = 0
        for (collectionPath in FirestoreSyncCollectionOrder.reconciliationOrder.filter { it in adapterLookup }) {
            upsertsApplied += upsertCollection(userId, collectionPath)
        }
        val tombstonesApplied = tombstoneSweep(userId)
        loggingPort.warning(
            FirestoreSyncContext.TAG,
            "Entity-source reconciliation for uid=$userId upserts=$upsertsApplied tombstones=$tombstonesApplied",
        )
        return ReconcileResult(upsertsApplied = upsertsApplied, tombstonesApplied = tombstonesApplied)
    }

    private suspend fun upsertCollection(
        userId: String,
        collectionPath: String,
    ): Int {
        var applied = 0
        paginateCollection(userId, collectionPath) { document ->
            val data = document.data ?: return@paginateCollection
            localStore.withTransaction {
                localStore.upsert(
                    collectionPath = collectionPath,
                    entityId = document.id,
                    data = data,
                    eventDeviceId = document.getString("_sync_device_id"),
                )
            }
            applied += 1
        }
        return applied
    }

    private suspend fun tombstoneSweep(userId: String): Int {
        var tombstones = 0
        for (collectionPath in FirestoreSyncCollectionOrder.reconciliationOrder.asReversed().filter { it in adapterLookup }) {
            val cloudIds = fetchCloudEntityIds(userId, collectionPath)
            val localIds = listLocalActiveEntityIds(collectionPath)
            val staleLocalIds =
                localIds.filter { localId ->
                    localId !in cloudIds && pendingSyncLookup.pendingSync(collectionPath, localId) == 0
                }
            for (localId in staleLocalIds) {
                localStore.withTransaction {
                    localStore.delete(collectionPath, localId)
                }
                tombstones += 1
            }
        }
        return tombstones
    }

    private suspend fun fetchCloudEntityIds(
        userId: String,
        collectionPath: String,
    ): Set<String> {
        val ids = mutableSetOf<String>()
        paginateCollection(userId, collectionPath) { document ->
            ids += document.id
        }
        return ids
    }

    private suspend fun paginateCollection(
        userId: String,
        collectionPath: String,
        consumer: suspend (DocumentSnapshot) -> Unit,
    ) {
        var query: Query = firestore.collection("users/$userId/$collectionPath")
        var hasMore = true
        while (hasMore) {
            val snapshot = query.limit(PAGE_SIZE).get().await()
            snapshot.documents.filter { it.exists() }.forEach { document ->
                consumer(document)
            }
            hasMore = snapshot.size() >= PAGE_SIZE
            if (hasMore) {
                val lastDocument = snapshot.documents.last()
                query = query.startAfter(lastDocument)
            }
        }
    }

    private suspend fun listLocalActiveEntityIds(collectionPath: String): List<String> =
        when (collectionPath) {
            "chats" ->
                ctx.daos.chatDao
                    .getAllChats()
                    .first()
                    .map { it.id.value }
            "messages" ->
                ctx.daos.messageDao
                    .getAllMessages()
                    .first()
                    .map { it.message.id.value }
            "memories" -> ctx.daos.memoriesDao.getAllIds()
            "documents" -> ctx.daos.documentDao.getAllIds()
            "api_credentials" ->
                ctx.daos.apiCredentialsDao
                    .getAll()
                    .map { it.id.value }
            "local_models" ->
                ctx.daos.localModelsDao
                    .getAll()
                    .map { it.id.value }
            "utility_models" ->
                ctx.daos.utilityModelsDao
                    .getAll()
                    .map { it.id.value }
            "tts_providers" ->
                ctx.daos.ttsProviderDao
                    .getTtsProvidersSync()
                    .map { it.id }
            "live_voice_providers" ->
                ctx.daos.liveVoiceProviderDao
                    .getLiveVoiceProvidersSync()
                    .map { it.id }
            "media_providers" ->
                ctx.daos.mediaProviderDao
                    .observeAllMediaProviders()
                    .first()
                    .map { it.id }
            "studio_albums" ->
                ctx.daos.studioAlbumDao
                    .observeAllAlbums()
                    .first()
                    .map { it.id }
            "studio_gallery" ->
                ctx.daos.studioMediaDao
                    .getAllMedia()
                    .first()
                    .map { it.id }
            "default_models" ->
                ctx.daos.defaultModelsDao
                    .getAll()
                    .map { it.modelType.name }
            else -> emptyList()
        }

    private companion object {
        const val PAGE_SIZE = 200L
    }
}
