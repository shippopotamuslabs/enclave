package com.browntowndev.pocketcrew.core.data.remote.firestore.handlers

import com.browntowndev.pocketcrew.core.data.local.*
import com.browntowndev.pocketcrew.core.data.remote.EntitySyncAdapter
import com.browntowndev.pocketcrew.core.data.remote.firestore.*
import com.browntowndev.pocketcrew.core.data.remote.firestore.mappers.*
import com.browntowndev.pocketcrew.core.data.remote.sha256
import com.browntowndev.pocketcrew.core.data.security.SensitiveEntityFieldEncryptors
import com.browntowndev.pocketcrew.domain.model.chat.*
import com.browntowndev.pocketcrew.domain.model.config.*
import com.browntowndev.pocketcrew.domain.model.inference.*
import com.browntowndev.pocketcrew.domain.model.memory.MemoryCategory
import com.google.firebase.firestore.DocumentSnapshot

internal class MemoriesSyncHandler(
    private val ctx: FirestoreSyncContext,
) : EntitySyncAdapter<MemoriesEntity> {
    override suspend fun repairForSync(entity: MemoriesEntity): MemoriesEntity {
        val repaired = SensitiveEntityFieldEncryptors.encrypt(entity, ctx.sensitiveSelfHealer)
        if (repaired == entity) return entity
        persistRepaired(repaired)
        return repaired
    }

    private suspend fun persistRepaired(entity: MemoriesEntity) {
        ctx.daos.memoriesDao.insertMemory(entity)
    }

    override val collectionPath = "memories"

    override suspend fun claimPending() = ctx.daos.memoriesDao.claimPending()

    override suspend fun getClaimedForSync() = ctx.daos.memoriesDao.getClaimedForSync()

    override suspend fun markSynced(entities: List<MemoriesEntity>) = ctx.daos.memoriesDao.markSynced(entities.map { it.id })

    override fun getEntityId(entity: MemoriesEntity) = entity.id

    override suspend fun toFirestore(local: MemoriesEntity) =
        mapOf(
            "category" to local.category.name,
            "content" to local.content.encryptedForFirestore(ctx, collectionPath, local.id, "content"),
            "created_at" to local.createdAt,
            "updated_at" to local.updatedAt,
            "deleted" to local.deleted,
        )

    override fun fromFirestore(doc: DocumentSnapshot): MemoriesEntity? {
        val content = doc.getEncryptedField(ctx, "content") ?: return null
        return MemoriesEntity(
            id = doc.id,
            category =
                com.browntowndev.pocketcrew.domain.model.memory.MemoryCategory.valueOf(
                    doc.getString("category") ?: "CONVERSATION",
                ),
            content = content,
            createdAt = doc.getLong("created_at") ?: 0L,
            updatedAt = doc.getLong("updated_at") ?: 0L,
            deleted = doc.getBoolean("deleted") ?: false,
        )
    }

    override suspend fun upsertLocal(
        entity: MemoriesEntity,
        doc: DocumentSnapshot,
        overwrite: Boolean,
    ) {
        val existing = ctx.daos.memoriesDao.getMemoryById(entity.id)
        if (existing == null || entity.updatedAt >= existing.updatedAt) {
            ctx.daos.memoriesDao.insertMemory(entity.copy(pendingSync = 0))

            val hash = entity.content.sha256()
            val existingMeta = ctx.daos.embeddingDao.getEmbeddingMetadata(entity.id)
            if (existingMeta?.contentHash != hash) {
                ctx.embeddingCoordinator.enqueueMemory(entity.id)
            }
        } else {
            ctx.loggingPort.debug(
                FirestoreSyncContext.TAG,
                "Memory ${entity.id} local is newer or same age, skipping remote update",
            )
        }
    }

    override suspend fun deleteLocal(id: String) {
        ctx.daos.memoriesDao.softDeleteById(id)
    }
}
