package com.browntowndev.pocketcrew.core.data.remote.firestore.handlers

import com.browntowndev.pocketcrew.core.data.local.*
import com.browntowndev.pocketcrew.core.data.remote.EntitySyncAdapter
import com.browntowndev.pocketcrew.core.data.remote.firestore.*
import com.browntowndev.pocketcrew.core.data.remote.firestore.mappers.*
import com.browntowndev.pocketcrew.core.data.security.SensitiveEntityFieldEncryptors
import com.browntowndev.pocketcrew.domain.model.chat.*
import com.browntowndev.pocketcrew.domain.model.config.*
import com.browntowndev.pocketcrew.domain.model.inference.*
import com.google.firebase.firestore.DocumentSnapshot

internal class DocumentSyncHandler(
    private val ctx: FirestoreSyncContext,
) : EntitySyncAdapter<DocumentEntity> {
    override suspend fun repairForSync(entity: DocumentEntity): DocumentEntity {
        val repaired = SensitiveEntityFieldEncryptors.encrypt(entity, ctx.sensitiveSelfHealer)
        if (repaired == entity) return entity
        persistRepaired(repaired)
        return repaired
    }

    private suspend fun persistRepaired(entity: DocumentEntity) {
        ctx.daos.documentDao.insertDocument(entity)
    }

    override val collectionPath = "documents"

    override suspend fun claimPending() = ctx.daos.documentDao.claimPending()

    override suspend fun getClaimedForSync() = ctx.daos.documentDao.getClaimedForSync()

    override suspend fun markSynced(entities: List<com.browntowndev.pocketcrew.core.data.local.DocumentEntity>) =
        ctx.daos.documentDao.markSynced(
            entities.map {
                it.id
            },
        )

    override fun getEntityId(entity: com.browntowndev.pocketcrew.core.data.local.DocumentEntity) = entity.id

    override suspend fun toFirestore(local: com.browntowndev.pocketcrew.core.data.local.DocumentEntity) =
        mapOf(
            "message_id" to local.messageId.value,
            "chat_id" to
                ctx.daos.messageDao
                    .getMessageChatIdById(local.messageId)
                    ?.value,
            "json" to local.json.encryptedForFirestore(ctx, collectionPath, local.id, "json"),
            "title" to local.title.encryptedForFirestore(ctx, collectionPath, local.id, "title"),
            "author" to local.author?.let { it.encryptedForFirestore(ctx, collectionPath, local.id, "author") },
            "target_format" to local.targetFormat?.name,
            "created_at" to local.createdAt,
            "updated_at" to local.updatedAt,
            "deleted" to local.deleted,
        )

    override fun fromFirestore(doc: DocumentSnapshot) = DocumentFirestoreMapper.fromDocument(ctx, doc)

    override suspend fun upsertLocal(
        entity: com.browntowndev.pocketcrew.core.data.local.DocumentEntity,
        doc: DocumentSnapshot,
        overwrite: Boolean,
    ) {
        val existing = ctx.daos.documentDao.getDocumentById(entity.id)
        if (existing == null || entity.updatedAt > existing.updatedAt) {
            ctx.daos.documentDao.insertDocument(entity.copy(pendingSync = 0))

            // Enqueue re-embedding for all blocks if content changed
            if (existing?.json != entity.json) {
                ctx.embeddingCoordinator.enqueueDocument(entity.id)
            }
        } else if (entity.updatedAt == existing.updatedAt) {
            val merged = entity.copy(pendingSync = 0)
            if (merged != existing.copy(pendingSync = 0)) {
                ctx.daos.documentDao.insertDocument(merged)
                if (existing.json != entity.json) {
                    ctx.embeddingCoordinator.enqueueDocument(entity.id)
                }
            }
        } else {
            ctx.loggingPort.debug(
                FirestoreSyncContext.TAG,
                "Document ${entity.id} local is newer or same age, skipping remote update",
            )
        }
    }

    override suspend fun deleteLocal(id: String) {
        ctx.daos.documentDao.deleteDocument(id)
    }
}
