package com.browntowndev.pocketcrew.core.data.remote.firestore

import com.browntowndev.pocketcrew.core.data.remote.EntitySyncAdapter
import com.browntowndev.pocketcrew.core.data.security.AadContext
import com.browntowndev.pocketcrew.domain.model.chat.Chat
import com.browntowndev.pocketcrew.domain.model.chat.Message
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

internal class FirestorePendingSyncEngine(
    private val ctx: FirestoreSyncContext,
    private val adapters: List<EntitySyncAdapter<*>>,
    private val syncStateStore: FirestoreSyncStateStore = FirestoreSyncStateStore(),
) {
    private suspend fun syncMetadata(): Map<String, Any?> =
        mapOf(
            "_sync_device_id" to syncStateStore.deviceId(),
            "_sync_client_mutation_id" to syncStateStore.newClientMutationId(),
            "_sync_written_at" to syncStateStore.currentTimeMillis(),
        )

    suspend fun syncAllLocalDataToCloud(userId: String): Result<Unit> =
        runCatching {
            adapters.forEach { it.claimPending() }
            syncPendingChangesInternal(userId).getOrThrow()
        }

    suspend fun syncPendingChanges(userId: String): Result<Unit> = syncPendingChangesInternal(userId)

    suspend fun syncEntitiesToCloud(
        userId: String,
        chats: List<Chat>,
        messages: List<Message>,
    ): Result<Unit> =
        runCatching {
            val metadata = syncMetadata()
            val allEntities = mutableListOf<Pair<String, Map<String, Any?>>>()
            chats.forEach { chat ->
                if (chat.id.value.isBlank()) {
                    ctx.loggingPort.error(
                        FirestoreSyncContext.TAG,
                        "Empty chat ID in syncEntitiesToCloud. Skipping.",
                    )
                    return@forEach
                }
                val data =
                    mutableMapOf<String, Any?>(
                        "name" to
                            ctx.localEncryption.encryptString(
                                chat.name,
                                AadContext("ChatEntity", "name", chat.id.value).toBytes(),
                            ),
                        "created_at" to chat.created.time,
                        "last_modified" to chat.lastModified.time,
                        "pinned" to chat.pinned,
                        "updated_at" to System.currentTimeMillis(),
                    ).apply { putAll(metadata) }
                allEntities.add("users/$userId/chats/${chat.id.value}" to data)
            }
            messages.forEach { msg ->
                if (msg.id.value.isBlank()) {
                    ctx.loggingPort.error(
                        FirestoreSyncContext.TAG,
                        "Empty message ID in syncEntitiesToCloud. Skipping.",
                    )
                    return@forEach
                }
                val msgChatId = msg.chatId
                if (msgChatId == null) {
                    ctx.loggingPort.error(
                        FirestoreSyncContext.TAG,
                        "Null chat ID for message ${msg.id.value} in syncEntitiesToCloud. Skipping.",
                    )
                    return@forEach
                }
                val data =
                    mutableMapOf<String, Any?>(
                        "chat_id" to msgChatId.value,
                        "content" to
                            ctx.localEncryption.encryptString(
                                msg.content.text,
                                AadContext("MessageEntity", "content", msg.id.value).toBytes(),
                            ),
                        "role" to msg.role.name,
                        "message_state" to msg.messageState.name,
                        "created_at" to msg.createdAt,
                        "model_type" to msg.modelType?.name,
                        "updated_at" to System.currentTimeMillis(),
                    ).apply { putAll(metadata) }
                allEntities.add("users/$userId/messages/${msg.id.value}" to data)
            }

            if (allEntities.isNotEmpty()) {
                allEntities.chunked(500).forEach { batchItems ->
                    ctx.firestore
                        .runBatch { batch ->
                            batchItems.forEach { (path, data) ->
                                batch.set(ctx.firestore.document(path), data, SetOptions.merge())
                            }
                        }.await()
                }
            }
        }.fold(
            onSuccess = { Result.success(Unit) },
            onFailure = { failure ->
                ctx.loggingPort.error(FirestoreSyncContext.TAG, "syncEntitiesToCloud failed", failure)
                Result.failure(failure)
            },
        )

    private suspend fun syncPendingChangesInternal(userId: String): Result<Unit> =
        runCatching {
            for (adapter in adapters) {
                syncAdapterPendingChanges(adapter, userId)
            }
        }.fold(
            onSuccess = { Result.success(Unit) },
            onFailure = { failure ->
                ctx.loggingPort.error(FirestoreSyncContext.TAG, "syncPendingChanges failed", failure)
                Result.failure(failure)
            },
        )

    private suspend fun <T> syncAdapterPendingChanges(
        adapter: EntitySyncAdapter<T>,
        userId: String,
    ) {
        adapter.claimPending()
        val pending = adapter.getClaimedForSync()
        if (pending.isEmpty()) return

        ctx.loggingPort.debug(
            FirestoreSyncContext.TAG,
            "Syncing ${pending.size} pending changes for ${adapter.collectionPath}",
        )
        pending.chunked(500).forEach { batchItems ->
            val metadata = syncMetadata()
            val upserts =
                batchItems
                    .filter { !adapter.shouldDelete(it) }
                    .map { entity ->
                        val repairedEntity = adapter.repairForSync(entity)
                        val rawData = adapter.toFirestore(repairedEntity)
                        val data =
                            if (rawData.isEmpty()) {
                                emptyMap()
                            } else {
                                ctx.firestoreSensitiveFieldRepairer
                                    .repairOutbound(
                                        collectionPath = adapter.collectionPath,
                                        entityId = adapter.getEntityId(repairedEntity),
                                        data = rawData,
                                    ).toMutableMap()
                                    .apply { putAll(metadata) }
                            }
                        repairedEntity to data
                    }
            val deletes = batchItems.filter { adapter.shouldDelete(it) }
            val writableUpserts =
                upserts.filter { (entity, data) ->
                    data.isNotEmpty() && adapter.getEntityId(entity).isNotBlank()
                }
            val writableDeletes =
                deletes.filter { entity ->
                    adapter.getEntityId(entity).isNotBlank()
                }

            ctx.firestore
                .runBatch { batch ->
                    upserts.forEach { (entity, data) ->
                        if (data.isNotEmpty()) {
                            val entityId = adapter.getEntityId(entity)
                            if (entityId.isBlank()) {
                                ctx.loggingPort.error(
                                    FirestoreSyncContext.TAG,
                                    "Empty entity ID for ${adapter.collectionPath} in syncAdapterPendingChanges. Skipping.",
                                )
                                return@forEach
                            }
                            val path = "users/$userId/${adapter.collectionPath}/$entityId"
                            batch.set(ctx.firestore.document(path), data, SetOptions.merge())
                        }
                    }
                    deletes.forEach { entity ->
                        val entityId = adapter.getEntityId(entity)
                        if (entityId.isBlank()) {
                            ctx.loggingPort.error(
                                FirestoreSyncContext.TAG,
                                "Empty entity ID for ${adapter.collectionPath} in syncAdapterPendingChanges. Skipping.",
                            )
                            return@forEach
                        }
                        val path = "users/$userId/${adapter.collectionPath}/$entityId"
                        batch.delete(ctx.firestore.document(path))
                    }
                }.await()
            val syncedEntities = writableUpserts.map { it.first } + writableDeletes
            if (syncedEntities.isNotEmpty()) {
                adapter.markSynced(syncedEntities)
            }
        }
    }
}
