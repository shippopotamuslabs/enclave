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

internal class StudioAlbumSyncHandler(
    private val ctx: FirestoreSyncContext,
) : EntitySyncAdapter<StudioAlbumEntity> {
    override suspend fun repairForSync(entity: StudioAlbumEntity): StudioAlbumEntity {
        val repaired = SensitiveEntityFieldEncryptors.encrypt(entity, ctx.sensitiveSelfHealer)
        if (repaired == entity) return entity
        persistRepaired(repaired)
        return repaired
    }

    private suspend fun persistRepaired(entity: StudioAlbumEntity) {
        ctx.daos.studioAlbumDao.insertAlbum(entity)
    }

    override val collectionPath = "studio_albums"

    override suspend fun claimPending() = ctx.daos.studioAlbumDao.claimPending()

    override suspend fun getClaimedForSync() = ctx.daos.studioAlbumDao.getClaimedForSync()

    override suspend fun markSynced(entities: List<StudioAlbumEntity>) = ctx.daos.studioAlbumDao.markSynced(entities.map { it.id })

    override fun getEntityId(entity: StudioAlbumEntity) = entity.id

    override suspend fun toFirestore(local: StudioAlbumEntity) =
        mapOf(
            "name" to local.name.encryptedForFirestore(ctx, collectionPath, local.id, "name"),
            "updated_at" to local.updatedAt,
            "deleted" to local.deleted,
        )

    override fun fromFirestore(doc: DocumentSnapshot) =
        StudioAlbumEntity(
            id = doc.id,
            name = doc.getEncryptedField(ctx, "name") ?: "",
            updatedAt = doc.getLong("updated_at") ?: 0L,
            deleted = doc.getBoolean("deleted") ?: false,
            pendingSync = 0,
        )

    override suspend fun upsertLocal(
        entity: StudioAlbumEntity,
        doc: DocumentSnapshot,
        overwrite: Boolean,
    ) {
        val existing = ctx.daos.studioAlbumDao.getAlbumById(entity.id)
        if (existing == null || entity.updatedAt >= existing.updatedAt) {
            ctx.daos.studioAlbumDao.insertAlbum(entity.copy(pendingSync = 0))
        } else {
            ctx.loggingPort.debug(
                FirestoreSyncContext.TAG,
                "StudioAlbum ${entity.id} local is newer or same age, skipping remote update",
            )
        }
    }

    override suspend fun deleteLocal(id: String) {
        ctx.daos.studioAlbumDao.softDeleteById(id)
    }

    override fun shouldDelete(entity: StudioAlbumEntity): Boolean = false
}
