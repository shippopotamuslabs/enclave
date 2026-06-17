package com.browntowndev.enclave.core.data.remote.firestore.handlers

import com.browntowndev.enclave.core.data.local.LiveVoiceProviderEntity
import com.browntowndev.enclave.core.data.remote.EntitySyncAdapter
import com.browntowndev.enclave.core.data.remote.firestore.*
import com.browntowndev.enclave.core.data.security.SensitiveEntityFieldEncryptors
import com.browntowndev.enclave.domain.model.inference.ApiProvider
import com.google.firebase.firestore.DocumentSnapshot

internal class LiveVoiceProviderSyncHandler(
    private val ctx: FirestoreSyncContext,
) : EntitySyncAdapter<LiveVoiceProviderEntity> {
    override suspend fun repairForSync(entity: LiveVoiceProviderEntity): LiveVoiceProviderEntity {
        val repaired = SensitiveEntityFieldEncryptors.encrypt(entity, ctx.sensitiveSelfHealer)
        if (repaired == entity) return entity
        persistRepaired(repaired)
        return repaired
    }

    private suspend fun persistRepaired(entity: LiveVoiceProviderEntity) {
        ctx.daos.liveVoiceProviderDao.upsertLiveVoiceProvider(entity)
    }

    override val collectionPath = "live_voice_providers"

    override suspend fun claimPending() = ctx.daos.liveVoiceProviderDao.claimPending()

    override suspend fun getClaimedForSync() = ctx.daos.liveVoiceProviderDao.getClaimedForSync()

    override suspend fun markSynced(entities: List<LiveVoiceProviderEntity>) =
        ctx.daos.liveVoiceProviderDao.markSynced(entities.map { it.id })

    override fun getEntityId(entity: LiveVoiceProviderEntity) = entity.id

    override suspend fun toFirestore(local: LiveVoiceProviderEntity) =
        mapOf(
            "display_name" to local.displayName.encryptedForFirestore(ctx, collectionPath, local.id, "display_name"),
            "provider" to local.provider.name,
            "voice_name" to local.voiceName.encryptedForFirestore(ctx, collectionPath, local.id, "voice_name"),
            "model_name" to local.modelName?.let { it.encryptedForFirestore(ctx, collectionPath, local.id, "model_name") },
            "base_url" to local.baseUrl?.let { it.encryptedForFirestore(ctx, collectionPath, local.id, "base_url") },
            "credential_alias" to local.credentialAlias,
            "updated_at" to local.updatedAt,
            "deleted" to local.deleted,
        )

    override fun fromFirestore(doc: DocumentSnapshot) =
        LiveVoiceProviderEntity(
            id = doc.id,
            displayName = doc.getEncryptedField(ctx, "display_name") ?: "",
            provider = ApiProvider.valueOf(doc.getString("provider") ?: "OPENAI"),
            voiceName = doc.getEncryptedField(ctx, "voice_name") ?: "",
            modelName = doc.getEncryptedField(ctx, "model_name"),
            baseUrl = doc.getEncryptedField(ctx, "base_url"),
            credentialAlias = doc.getString("credential_alias") ?: "",
            updatedAt = doc.getLong("updated_at") ?: 0L,
            deleted = doc.getBoolean("deleted") ?: false,
        )

    override suspend fun upsertLocal(
        entity: LiveVoiceProviderEntity,
        doc: DocumentSnapshot,
        overwrite: Boolean,
    ) {
        val existing = ctx.daos.liveVoiceProviderDao.getLiveVoiceProvider(entity.id)
        if (existing == null || entity.updatedAt >= existing.updatedAt) {
            ctx.daos.liveVoiceProviderDao.upsertLiveVoiceProvider(entity.copy(pendingSync = 0))
        } else {
            ctx.loggingPort.debug(
                FirestoreSyncContext.TAG,
                "LiveVoiceProvider ${entity.id} local is newer or same age, skipping remote update",
            )
        }
    }

    override suspend fun deleteLocal(id: String) {
        ctx.daos.liveVoiceProviderDao.softDeleteById(id)
    }
}
