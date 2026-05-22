package com.browntowndev.enclave.core.data.remote.firestore.handlers

import com.browntowndev.enclave.core.data.local.*
import com.browntowndev.enclave.core.data.remote.EntitySyncAdapter
import com.browntowndev.enclave.core.data.remote.firestore.*
import com.browntowndev.enclave.core.data.remote.firestore.mappers.*
import com.browntowndev.enclave.core.data.security.SensitiveEntityFieldEncryptors
import com.browntowndev.enclave.domain.model.chat.*
import com.browntowndev.enclave.domain.model.config.*
import com.browntowndev.enclave.domain.model.inference.*
import com.google.firebase.firestore.DocumentSnapshot

internal class ChatSummarySyncHandler(
    private val ctx: FirestoreSyncContext,
) : EntitySyncAdapter<ChatSummaryEntity> {
    override suspend fun repairForSync(entity: ChatSummaryEntity): ChatSummaryEntity {
        val repaired = SensitiveEntityFieldEncryptors.encrypt(entity, ctx.sensitiveSelfHealer)
        if (repaired == entity) return entity
        persistRepaired(repaired)
        return repaired
    }

    private suspend fun persistRepaired(entity: ChatSummaryEntity) {
        ctx.daos.chatSummaryDao.insertOrUpdateSummary(entity)
    }

    override val collectionPath: String = "chat_summaries"

    override suspend fun claimPending() = ctx.daos.chatSummaryDao.claimPending()

    override suspend fun getClaimedForSync() = ctx.daos.chatSummaryDao.getClaimedForSync()

    override suspend fun markSynced(entities: List<ChatSummaryEntity>) = ctx.daos.chatSummaryDao.markSynced(entities.map { it.id })

    override fun getEntityId(entity: ChatSummaryEntity): String = entity.chatId.value

    override suspend fun toFirestore(local: ChatSummaryEntity): Map<String, Any?> =
        mapOf(
            "chat_id" to local.chatId.value,
            "content" to local.content.encryptedForFirestore(ctx, collectionPath, local.chatId.value, "content"),
            "last_summarized_message_id" to local.lastSummarizedMessageId?.value,
            "created_at" to local.createdAt,
            "updated_at" to local.updatedAt,
            "deleted" to local.deleted,
        )

    override fun fromFirestore(doc: DocumentSnapshot): ChatSummaryEntity? {
        val chatIdStr = doc.getString("chat_id") ?: return null
        val content = doc.getEncryptedField(ctx, "content") ?: return null
        return ChatSummaryEntity(
            chatId = ChatId(chatIdStr),
            content = content,
            lastSummarizedMessageId = doc.getString("last_summarized_message_id")?.let { MessageId(it) },
            createdAt = doc.getLong("created_at") ?: 0L,
            updatedAt = doc.getLong("updated_at") ?: 0L,
            deleted = doc.getBoolean("deleted") ?: false,
            pendingSync = 0,
        )
    }

    override suspend fun upsertLocal(
        entity: ChatSummaryEntity,
        doc: DocumentSnapshot,
        overwrite: Boolean,
    ) {
        val existing = ctx.daos.chatSummaryDao.getSummaryByChatIdIncludingDeletedSync(entity.chatId)
        if (existing == null || entity.updatedAt >= existing.updatedAt) {
            val merged =
                if (existing != null) {
                    entity.copy(id = existing.id, pendingSync = 0)
                } else {
                    entity.copy(pendingSync = 0)
                }
            ctx.daos.chatSummaryDao.insertOrUpdateSummary(merged)
        } else {
            ctx.loggingPort.debug(
                FirestoreSyncContext.TAG,
                "ChatSummary ${entity.chatId.value} local is newer or same age, skipping remote update",
            )
        }
    }

    override suspend fun deleteLocal(id: String) {
        ctx.daos.chatSummaryDao.deleteSummaryForChat(ChatId(id))
    }
}
