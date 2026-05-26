package com.browntowndev.enclave.core.data.remote.firestore.handlers

import com.browntowndev.enclave.core.data.local.*
import com.browntowndev.enclave.core.data.remote.EntitySyncAdapter
import com.browntowndev.enclave.core.data.remote.firestore.*
import com.browntowndev.enclave.core.data.remote.firestore.mappers.*
import com.browntowndev.enclave.core.data.remote.sha256
import com.browntowndev.enclave.core.data.security.SensitiveEntityFieldEncryptors
import com.browntowndev.enclave.domain.model.chat.*
import com.browntowndev.enclave.domain.model.config.*
import com.browntowndev.enclave.domain.model.inference.*
import com.google.firebase.firestore.DocumentSnapshot

internal class MessageSyncHandler(
    private val ctx: FirestoreSyncContext,
) : EntitySyncAdapter<MessageWithFiles> {
    override suspend fun repairForSync(entity: MessageWithFiles): MessageWithFiles {
        val repairedMessage = SensitiveEntityFieldEncryptors.encrypt(entity.message, ctx.sensitiveSelfHealer)
        if (repairedMessage == entity.message) return entity
        val repaired = entity.copy(message = repairedMessage)
        ctx.daos.messageDao.upsert(repairedMessage)
        return repaired
    }

    override val collectionPath: String = "messages"

    override suspend fun claimPending() = ctx.daos.messageDao.claimPending()

    override suspend fun getClaimedForSync() = ctx.daos.messageDao.getClaimedForSync()

    override suspend fun markSynced(entities: List<MessageWithFiles>) = ctx.daos.messageDao.markSynced(entities.map { it.message.id })

    override fun getEntityId(entity: MessageWithFiles): String = entity.message.id.value

    override suspend fun toFirestore(local: MessageWithFiles): Map<String, Any?> {
        val entity = local.message
        val msgData =
            mutableMapOf<String, Any?>(
                "chat_id" to entity.chatId.value,
                "content" to entity.content.encryptedForFirestore(ctx, collectionPath, entity.id.value, "content"),
                "role" to entity.role.apiValue,
                "message_state" to entity.messageState.apiValue,
                "updated_at" to entity.updatedAt,
                "deleted" to entity.deleted,
            )
        entity.createdAt?.let { msgData["created_at"] = it }
        entity.modelType?.let { msgData["model_type"] = it.name }
        entity.userMessageId?.let { msgData["user_message_id"] = it.value }
        entity.imageUrisJson?.let {
            msgData["image_uris_json"] = it.encryptedForFirestore(ctx, collectionPath, entity.id.value, "image_uris_json")
        }
        entity.thinkingDurationSeconds?.let { msgData["thinking_duration_seconds"] = it }
        entity.thinkingRaw?.let {
            msgData["thinking_raw"] = it.encryptedForFirestore(ctx, collectionPath, entity.id.value, "thinking_raw")
        }
        entity.thinkingStartTime?.let { msgData["thinking_start_time"] = it }
        entity.thinkingEndTime?.let { msgData["thinking_end_time"] = it }
        entity.pipelineStep?.let { msgData["pipeline_step"] = it.name }
        entity.artifactsJson?.let {
            msgData["artifacts_json"] = it.encryptedForFirestore(ctx, collectionPath, entity.id.value, "artifacts_json")
        }
        entity.generatedMediaUrisJson?.let {
            msgData["generated_media_uris_json"] =
                it.encryptedForFirestore(ctx, collectionPath, entity.id.value, "generated_media_uris_json")
        }
        entity.generatedMediaAssetIdsJson?.let {
            msgData["generated_media_asset_ids_json"] =
                it.encryptedForFirestore(ctx, collectionPath, entity.id.value, "generated_media_asset_ids_json")
        }
        entity.generatedMediaTypesJson?.let { msgData["generated_media_types_json"] = it }
        entity.generatedMediaPromptsJson?.let {
            msgData["generated_media_prompts_json"] =
                it.encryptedForFirestore(ctx, collectionPath, entity.id.value, "generated_media_prompts_json")
        }

        return msgData
    }

    override fun fromFirestore(doc: DocumentSnapshot): MessageWithFiles? =
        MessageFirestoreMapper.fromDocument(ctx, doc)?.let { MessageWithFiles(it, emptyList()) }

    override suspend fun upsertLocal(
        entity: MessageWithFiles,
        doc: DocumentSnapshot,
        overwrite: Boolean,
    ) {
        val existing = ctx.daos.messageDao.getMessageById(entity.message.id)
        if (overwrite || existing == null || entity.message.updatedAt > existing.message.updatedAt) {
            // Preserve local artifacts_json until Task 4 migration is complete
            val merged =
                entity.message.copy(
                    artifactsJson =
                        if (entity.message.artifactsJson.isNullOrBlank()) {
                            existing?.message?.artifactsJson
                        } else {
                            entity.message.artifactsJson
                        },
                    pendingSync = 0,
                )
            ctx.daos.messageDao.upsert(merged)

            val hash = entity.message.content.sha256()
            val existingMeta = ctx.daos.embeddingDao.getEmbeddingMetadata(entity.message.id.value)
            if (existingMeta?.contentHash != hash) {
                ctx.embeddingCoordinator.enqueueMessage(entity.message.id.value)
            }
        } else if (entity.message.updatedAt == existing.message.updatedAt) {
            val merged =
                entity.message.copy(
                    artifactsJson =
                        if (entity.message.artifactsJson.isNullOrBlank()) {
                            existing.message.artifactsJson
                        } else {
                            entity.message.artifactsJson
                        },
                    pendingSync = 0,
                )
            if (merged != existing.message.copy(pendingSync = 0)) {
                ctx.daos.messageDao.upsert(merged)
            }
        } else {
            ctx.loggingPort.debug(
                FirestoreSyncContext.TAG,
                "Message ${entity.message.id.value} local is newer or same age, skipping remote update",
            )
        }
    }

    override suspend fun deleteLocal(id: String) {
        val messageId = MessageId(id)
        ctx.daos.messageDao.softDeleteById(messageId)
        // Soft-delete linked documents too (documents are tied to messages via message_id).
        ctx.daos.documentDao.softDeleteByMessageId(messageId)
    }
}
