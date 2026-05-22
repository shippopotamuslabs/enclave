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

internal class WebSearchSourceSyncHandler(
    private val ctx: FirestoreSyncContext,
) : EntitySyncAdapter<WebSearchSourceEntity> {
    override suspend fun repairForSync(entity: WebSearchSourceEntity): WebSearchSourceEntity {
        val repaired = SensitiveEntityFieldEncryptors.encrypt(entity, ctx.sensitiveSelfHealer)
        if (repaired == entity) return entity
        persistRepaired(repaired)
        return repaired
    }

    private suspend fun persistRepaired(entity: WebSearchSourceEntity) {
        ctx.daos.webSearchSourceDao.upsert(entity)
    }

    override val collectionPath = "web_search_sources"

    override suspend fun claimPending() = ctx.daos.webSearchSourceDao.claimPending()

    override suspend fun getClaimedForSync() = ctx.daos.webSearchSourceDao.getClaimedForSync()

    override suspend fun markSynced(entities: List<WebSearchSourceEntity>) = ctx.daos.webSearchSourceDao.markSynced(entities.map { it.id })

    override fun getEntityId(entity: WebSearchSourceEntity) = entity.id

    override suspend fun toFirestore(local: WebSearchSourceEntity): Map<String, Any?> {
        val message =
            ctx.daos.messageDao
                .getMessageById(local.messageId)
                ?.message ?: return emptyMap()
        val chatId = message.chatId.value
        return mapOf(
            "message_id" to local.messageId.value,
            "chat_id" to chatId,
            "title" to local.title.encryptedForFirestore(ctx, collectionPath, local.id, "title"),
            "url" to local.url.encryptedForFirestore(ctx, collectionPath, local.id, "url"),
            "content" to local.content.encryptedForFirestore(ctx, collectionPath, local.id, "content"),
            "score" to local.score,
            "extracted" to local.extracted,
            "updated_at" to local.updatedAt,
            "deleted" to local.deleted,
        )
    }

    override fun fromFirestore(doc: DocumentSnapshot): WebSearchSourceEntity? {
        val chatIdStr = doc.getString("chat_id")
        val messageIdStr = doc.getString("message_id")
        if (chatIdStr.isNullOrBlank() || messageIdStr.isNullOrBlank()) return null
        val title = doc.getEncryptedField(ctx, "title") ?: return null
        val url = doc.getEncryptedField(ctx, "url") ?: return null
        val content = doc.getEncryptedField(ctx, "content") ?: return null
        return WebSearchSourceEntity(
            id = doc.id,
            messageId = MessageId(messageIdStr),
            title = title,
            url = url,
            content = content,
            score = doc.getDouble("score") ?: 0.0,
            extracted = doc.getBoolean("extracted") ?: false,
            updatedAt = doc.getLong("updated_at") ?: 0L,
            deleted = doc.getBoolean("deleted") ?: false,
        )
    }

    override suspend fun upsertLocal(
        entity: WebSearchSourceEntity,
        doc: DocumentSnapshot,
        overwrite: Boolean,
    ) {
        val existing = ctx.daos.webSearchSourceDao.getById(entity.id)
        if (existing == null || entity.updatedAt > existing.updatedAt) {
            ctx.daos.webSearchSourceDao.upsert(entity.copy(pendingSync = 0))
        } else if (entity.updatedAt == existing.updatedAt) {
            val merged = entity.copy(pendingSync = 0)
            if (merged != existing.copy(pendingSync = 0)) {
                ctx.daos.webSearchSourceDao.upsert(merged)
            }
        } else {
            ctx.loggingPort.debug(
                FirestoreSyncContext.TAG,
                "WebSearchSource ${entity.id} local is newer or same age, skipping remote update",
            )
        }
    }

    override suspend fun deleteLocal(id: String) {
        ctx.daos.webSearchSourceDao.softDeleteById(id)
    }
}
