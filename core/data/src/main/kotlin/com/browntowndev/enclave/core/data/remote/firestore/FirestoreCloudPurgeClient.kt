package com.browntowndev.enclave.core.data.remote.firestore

import com.browntowndev.enclave.core.data.remote.EntitySyncAdapter
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await

internal class FirestoreCloudPurgeClient(
    private val ctx: FirestoreSyncContext,
    private val adapters: List<EntitySyncAdapter<*>>,
    private val listenerCoordinator: FirestoreListenerCoordinator,
    private val syncMutex: Mutex,
) {
    suspend fun deleteUserAccount(): Result<Unit> =
        syncMutex.withLock {
            runCatching {
                val userId = ctx.userIdProvider() ?: error("Not signed in")
                val collectionPaths = adapters.map { it.collectionPath }.distinct()
                for (path in collectionPaths) {
                    deleteAllDocumentsInFirestoreCollection(parentPath = "users/$userId", segment = path)
                }
                ctx.firestore
                    .document("users/$userId")
                    .delete()
                    .await()
                listenerCoordinator.detachAllListeners()
            }
        }

    suspend fun deleteAllChatsFromCloud(): Result<Unit> =
        withCloudPurge {
            val userId = ctx.userIdProvider() ?: return@withCloudPurge Result.failure(IllegalStateException("Not signed in"))
            val collections =
                listOf(
                    "chats",
                    "messages",
                    "documents",
                    "chat_summaries",
                    "message_transcript_segments",
                    "message_vision_analysis",
                    "message_files",
                    "web_search_sources",
                )
            for (collection in collections) {
                deleteAllDocumentsInFirestoreCollection(parentPath = "users/$userId", segment = collection)
            }
            Result.success(Unit)
        }

    suspend fun deleteAllMediaFromCloud(): Result<Unit> =
        withCloudPurge {
            val userId = ctx.userIdProvider() ?: return@withCloudPurge Result.failure(IllegalStateException("Not signed in"))
            val collections = listOf("studio_gallery", "studio_albums")
            for (collection in collections) {
                deleteAllDocumentsInFirestoreCollection(parentPath = "users/$userId", segment = collection)
            }
            Result.success(Unit)
        }

    suspend fun deleteAllMemoriesFromCloud(): Result<Unit> =
        withCloudPurge {
            val userId = ctx.userIdProvider() ?: return@withCloudPurge Result.failure(IllegalStateException("Not signed in"))
            deleteAllDocumentsInFirestoreCollection(parentPath = "users/$userId", segment = "memories")
            Result.success(Unit)
        }

    suspend fun deleteAllArtifactsFromCloud(): Result<Unit> =
        withCloudPurge {
            val userId = ctx.userIdProvider() ?: return@withCloudPurge Result.failure(IllegalStateException("Not signed in"))
            deleteAllDocumentsInFirestoreCollection(parentPath = "users/$userId", segment = "documents")
            Result.success(Unit)
        }

    private suspend fun <T> withCloudPurge(block: suspend () -> Result<T>): Result<T> =
        syncMutex.withLock {
            ctx.purgeInProgress.set(true)
            try {
                block()
            } catch (failure: Exception) {
                Result.failure(failure)
            } finally {
                ctx.purgeInProgress.set(false)
                ctx.cleanupOrphanedPlaceholderChats()
            }
        }

    private suspend fun deleteAllDocumentsInFirestoreCollection(
        parentPath: String,
        segment: String,
    ) {
        val collection = ctx.firestore.collection("$parentPath/$segment")
        while (true) {
            val snapshot = collection.limit(FirestoreSyncContext.FIRESTORE_DELETE_PAGE_SIZE).get().await()
            if (snapshot.documents.isEmpty()) break
            val batch = ctx.firestore.batch()
            snapshot.documents.forEach { doc -> batch.delete(doc.reference) }
            batch.commit().await()
        }
    }
}
