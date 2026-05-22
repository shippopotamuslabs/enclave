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

internal class ChatSyncHandler(
    private val ctx: FirestoreSyncContext,
) : EntitySyncAdapter<ChatEntity> {
    override suspend fun repairForSync(entity: ChatEntity): ChatEntity {
        val repaired = SensitiveEntityFieldEncryptors.encrypt(entity, ctx.sensitiveSelfHealer)
        if (repaired == entity) return entity
        persistRepaired(repaired)
        return repaired
    }

    private suspend fun persistRepaired(entity: ChatEntity) {
        ctx.daos.chatDao.upsert(entity)
    }

    override val collectionPath: String = "chats"

    override suspend fun claimPending() = ctx.daos.chatDao.claimPending()

    override suspend fun getClaimedForSync() = ctx.daos.chatDao.getClaimedForSync()

    override suspend fun markSynced(entities: List<ChatEntity>) = ctx.daos.chatDao.markSynced(entities.map { it.id })

    override fun getEntityId(entity: ChatEntity): String = entity.id.value

    override suspend fun toFirestore(local: ChatEntity): Map<String, Any?> =
        mapOf(
            "name" to local.name.encryptedForFirestore(ctx, collectionPath, local.id.value, "name"),
            "created_at" to local.created.time,
            "last_modified" to local.lastModified.time,
            "pinned" to local.pinned,
            "updated_at" to local.updatedAt,
            "deleted" to local.deleted,
        )

    override fun fromFirestore(doc: DocumentSnapshot): ChatEntity? = ChatFirestoreMapper.fromDocument(ctx, doc)

    override suspend fun upsertLocal(
        entity: ChatEntity,
        doc: DocumentSnapshot,
        overwrite: Boolean,
    ) {
        val existing = ctx.daos.chatDao.getChatById(entity.id)
        if (existing == null || entity.updatedAt >= existing.updatedAt) {
            ctx.daos.chatDao.upsert(entity.copy(pendingSync = 0)) // 0 = clean
        } else {
            ctx.loggingPort.debug(
                FirestoreSyncContext.TAG,
                "Chat ${entity.id.value} local is newer or same age, skipping remote update",
            )
        }
    }

    override suspend fun deleteLocal(id: String) {
        ctx.daos.chatDao.softDeleteById(ChatId(id))
        // Soft-delete linked documents too (documents are tied to messages via message_id).
        ctx.daos.documentDao.softDeleteByChatId(ChatId(id))
    }
}
