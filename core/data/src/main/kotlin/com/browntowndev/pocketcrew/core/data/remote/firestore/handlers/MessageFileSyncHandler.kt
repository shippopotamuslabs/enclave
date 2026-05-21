package com.browntowndev.pocketcrew.core.data.remote.firestore.handlers

import com.browntowndev.pocketcrew.core.data.local.*
import com.browntowndev.pocketcrew.core.data.remote.EntitySyncAdapter
import com.browntowndev.pocketcrew.core.data.remote.firestore.*
import com.browntowndev.pocketcrew.core.data.remote.firestore.mappers.*
import com.browntowndev.pocketcrew.core.data.security.SensitiveEntityFieldEncryptors
import com.browntowndev.pocketcrew.domain.model.artifact.MimeType
import com.browntowndev.pocketcrew.domain.model.chat.*
import com.browntowndev.pocketcrew.domain.model.config.*
import com.browntowndev.pocketcrew.domain.model.inference.*
import com.google.firebase.firestore.DocumentSnapshot

internal class MessageFileSyncHandler(
    private val ctx: FirestoreSyncContext,
) : EntitySyncAdapter<MessageFileEntity> {
    override suspend fun repairForSync(entity: MessageFileEntity): MessageFileEntity {
        val repaired = SensitiveEntityFieldEncryptors.encrypt(entity, ctx.sensitiveSelfHealer)
        if (repaired == entity) return entity
        persistRepaired(repaired)
        return repaired
    }

    private suspend fun persistRepaired(entity: MessageFileEntity) {
        ctx.daos.messageFileDao.insert(entity)
    }

    override val collectionPath = "message_files"

    override suspend fun claimPending() = ctx.daos.messageFileDao.claimPending()

    override suspend fun getClaimedForSync() = ctx.daos.messageFileDao.getClaimedForSync()

    override suspend fun markSynced(entities: List<MessageFileEntity>) = ctx.daos.messageFileDao.markSynced(entities.map { it.id })

    override fun getEntityId(entity: MessageFileEntity) = entity.id

    override suspend fun toFirestore(local: MessageFileEntity): Map<String, Any?> {
        val message =
            ctx.daos.messageDao
                .getMessageById(MessageId(local.messageId))
                ?.message ?: return emptyMap()
        val chatId = message.chatId.value
        return mapOf(
            "message_id" to local.messageId,
            "chat_id" to chatId,
            "name" to local.name.encryptedForFirestore(ctx, collectionPath, local.id, "name"),
            "mime_type" to local.mimeType.name,
            "remote_url" to local.remoteUrl?.let { it.encryptedForFirestore(ctx, collectionPath, local.id, "remote_url") },
            "sync_status" to local.syncStatus,
            "updated_at" to local.updatedAt,
            "deleted" to local.deleted,
        )
    }

    override fun fromFirestore(doc: DocumentSnapshot): MessageFileEntity? {
        val chatIdStr = doc.getString("chat_id")
        if (chatIdStr.isNullOrBlank()) return null
        val messageIdStr = doc.getString("message_id")
        if (messageIdStr.isNullOrBlank()) return null
        val name = doc.getEncryptedField(ctx, "name") ?: return null
        val remoteUrl = doc.getEncryptedField(ctx, "remote_url")

        val mimeTypeStr = doc.getString("mime_type") ?: MimeType.PLAIN_TEXT.name
        val mimeType = runCatching { MimeType.valueOf(mimeTypeStr) }.getOrDefault(MimeType.PLAIN_TEXT)

        return MessageFileEntity(
            id = doc.id,
            messageId = messageIdStr,
            name = name,
            content = "", // Content will be downloaded from Storage if remoteUrl exists
            mimeType = mimeType,
            remoteUrl = remoteUrl,
            syncStatus = doc.getString("sync_status") ?: "PENDING_UPLOAD",
            updatedAt = doc.getLong("updated_at") ?: 0L,
            deleted = doc.getBoolean("deleted") ?: false,
            pendingSync = 0,
        )
    }

    override suspend fun upsertLocal(
        entity: MessageFileEntity,
        doc: DocumentSnapshot,
        overwrite: Boolean,
    ) {
        val existing = ctx.daos.messageFileDao.getById(entity.id)
        if (existing == null || entity.updatedAt >= existing.updatedAt) {
            // Preserve local content if we already have it
            val merged =
                if (existing != null) {
                    entity.copy(
                        content = if (entity.content.isBlank()) existing.content else entity.content,
                        pendingSync = 0,
                    )
                } else {
                    entity.copy(pendingSync = 0)
                }
            ctx.daos.messageFileDao.insert(merged)
        } else {
            ctx.loggingPort.debug(
                FirestoreSyncContext.TAG,
                "MessageFile ${entity.id} local is newer or same age, skipping remote update",
            )
        }
    }

    override suspend fun deleteLocal(id: String) {
        ctx.daos.messageFileDao.softDeleteById(id)
    }
}
