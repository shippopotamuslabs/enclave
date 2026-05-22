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

internal class MediaProviderSyncHandler(
    private val ctx: FirestoreSyncContext,
) : EntitySyncAdapter<MediaProviderEntity> {
    override suspend fun repairForSync(entity: MediaProviderEntity): MediaProviderEntity {
        val repaired = SensitiveEntityFieldEncryptors.encrypt(entity, ctx.sensitiveSelfHealer)
        if (repaired == entity) return entity
        persistRepaired(repaired)
        return repaired
    }

    private suspend fun persistRepaired(entity: MediaProviderEntity) {
        ctx.daos.mediaProviderDao.upsert(entity)
    }

    override val collectionPath = "media_providers"

    override suspend fun claimPending() = ctx.daos.mediaProviderDao.claimPending()

    override suspend fun getClaimedForSync() = ctx.daos.mediaProviderDao.getClaimedForSync()

    override suspend fun markSynced(entities: List<MediaProviderEntity>) = ctx.daos.mediaProviderDao.markSynced(entities.map { it.id })

    override fun getEntityId(entity: MediaProviderEntity) = entity.id

    override suspend fun toFirestore(local: MediaProviderEntity) =
        mapOf(
            "display_name" to local.displayName.encryptedForFirestore(ctx, collectionPath, local.id, "display_name"),
            "provider" to local.provider.name,
            "capability" to local.capability.name,
            "model_name" to local.modelName?.let { it.encryptedForFirestore(ctx, collectionPath, local.id, "model_name") },
            "base_url" to local.baseUrl?.let { it.encryptedForFirestore(ctx, collectionPath, local.id, "base_url") },
            "credential_alias" to local.credentialAlias,
            "updated_at" to local.updatedAt,
            "deleted" to local.deleted,
        )

    override fun fromFirestore(doc: DocumentSnapshot) =
        MediaProviderEntity(
            id = doc.id,
            displayName = doc.getEncryptedField(ctx, "display_name") ?: "",
            provider =
                com.browntowndev.enclave.domain.model.inference.ApiProvider
                    .valueOf(doc.getString("provider") ?: "OPENAI"),
            capability =
                com.browntowndev.enclave.domain.model.config.MediaCapability
                    .valueOf(doc.getString("capability") ?: "IMAGE"),
            modelName = doc.getEncryptedField(ctx, "model_name"),
            baseUrl = doc.getEncryptedField(ctx, "base_url"),
            credentialAlias = doc.getString("credential_alias") ?: "",
            updatedAt = doc.getLong("updated_at") ?: 0L,
            deleted = doc.getBoolean("deleted") ?: false,
        )

    override suspend fun upsertLocal(
        entity: MediaProviderEntity,
        doc: DocumentSnapshot,
        overwrite: Boolean,
    ) {
        val existing = ctx.daos.mediaProviderDao.getMediaProvider(entity.id)
        if (existing == null || entity.updatedAt >= existing.updatedAt) {
            ctx.daos.mediaProviderDao.upsert(entity.copy(pendingSync = 0))
        } else {
            ctx.loggingPort.debug(
                FirestoreSyncContext.TAG,
                "MediaProvider ${entity.id} local is newer or same age, skipping remote update",
            )
        }
    }

    override suspend fun deleteLocal(id: String) {
        ctx.daos.mediaProviderDao.softDeleteById(id)
    }
}
