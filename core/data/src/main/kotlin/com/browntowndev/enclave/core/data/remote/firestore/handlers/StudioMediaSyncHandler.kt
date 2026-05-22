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

internal class StudioMediaSyncHandler(
    private val ctx: FirestoreSyncContext,
) : EntitySyncAdapter<StudioMediaEntity> {
    override suspend fun repairForSync(entity: StudioMediaEntity): StudioMediaEntity {
        val repaired = SensitiveEntityFieldEncryptors.encrypt(entity, ctx.sensitiveSelfHealer)
        if (repaired == entity) return entity
        persistRepaired(repaired)
        return repaired
    }

    private suspend fun persistRepaired(entity: StudioMediaEntity) {
        ctx.daos.studioMediaDao.insertMedia(entity)
    }

    override val collectionPath = "studio_gallery"

    override suspend fun claimPending() = ctx.daos.studioMediaDao.claimPending()

    override suspend fun getClaimedForSync() = ctx.daos.studioMediaDao.getClaimedForSync()

    override suspend fun markSynced(entities: List<StudioMediaEntity>) = ctx.daos.studioMediaDao.markSynced(entities.map { it.id })

    override fun getEntityId(entity: StudioMediaEntity) = entity.id

    override suspend fun toFirestore(local: StudioMediaEntity) =
        mapOf(
            "prompt" to local.prompt.encryptedForFirestore(ctx, collectionPath, local.id, "prompt"),
            "media_type" to local.mediaType,
            "created_at" to local.createdAt,
            "album_id" to local.albumId,
            "remote_url" to local.remoteUrl?.let { it.encryptedForFirestore(ctx, collectionPath, local.id, "remote_url") },
            "remote_thumbnail_url" to
                local.remoteThumbnailUrl?.let {
                    it.encryptedForFirestore(ctx, collectionPath, local.id, "remote_thumbnail_url")
                },
            "updated_at" to local.updatedAt,
            "deleted" to local.deleted,
        )

    override fun fromFirestore(doc: DocumentSnapshot): StudioMediaEntity {
        val remoteUrl = doc.getEncryptedField(ctx, "remote_url")
        val remoteThumbnailUrl = doc.getEncryptedField(ctx, "remote_thumbnail_url")
        val prompt = doc.getEncryptedField(ctx, "prompt") ?: ""

        return StudioMediaEntity(
            id = doc.id,
            prompt = prompt,
            mediaUri = remoteUrl ?: "",
            mediaType = doc.getString("media_type") ?: "IMAGE",
            createdAt = doc.getLong("created_at") ?: System.currentTimeMillis(),
            albumId = doc.getString("album_id"),
            remoteUrl = remoteUrl,
            remoteThumbnailUrl = remoteThumbnailUrl,
            syncStatus = if (remoteUrl != null) "UPLOADED" else "PENDING_UPLOAD",
            updatedAt = doc.getLong("updated_at") ?: 0L,
            deleted = doc.getBoolean("deleted") ?: false,
            pendingSync = 0,
        )
    }

    override suspend fun upsertLocal(
        entity: StudioMediaEntity,
        doc: DocumentSnapshot,
        overwrite: Boolean,
    ) {
        val existing = ctx.daos.studioMediaDao.getMediaById(entity.id)
        if (existing == null || entity.updatedAt > existing.updatedAt) {
            // Preserve local path if remote doesn't have a URL yet
            val merged =
                if (existing != null) {
                    entity.copy(
                        mediaUri = if (entity.mediaUri.isBlank()) existing.mediaUri else entity.mediaUri,
                        pendingSync = 0,
                    )
                } else {
                    entity.copy(pendingSync = 0)
                }
            ctx.daos.studioMediaDao.insertMedia(merged)
        } else if (entity.updatedAt == existing.updatedAt) {
            val merged =
                entity.copy(
                    mediaUri = if (entity.mediaUri.isBlank()) existing.mediaUri else entity.mediaUri,
                    pendingSync = 0,
                )
            if (merged != existing.copy(pendingSync = 0)) {
                ctx.daos.studioMediaDao.insertMedia(merged)
            }
        } else {
            ctx.loggingPort.debug(
                FirestoreSyncContext.TAG,
                "StudioMedia ${entity.id} local is newer or same age, skipping remote update",
            )
        }
    }

    override suspend fun deleteLocal(id: String) {
        ctx.daos.studioMediaDao.softDeleteById(id)
    }

    override fun shouldDelete(entity: StudioMediaEntity): Boolean = false
}
