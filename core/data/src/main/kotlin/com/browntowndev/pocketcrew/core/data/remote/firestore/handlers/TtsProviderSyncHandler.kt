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

internal class TtsProviderSyncHandler(
    private val ctx: FirestoreSyncContext,
) : EntitySyncAdapter<TtsProviderEntity> {
    override suspend fun repairForSync(entity: TtsProviderEntity): TtsProviderEntity {
        val repaired = SensitiveEntityFieldEncryptors.encrypt(entity, ctx.sensitiveSelfHealer)
        if (repaired == entity) return entity
        persistRepaired(repaired)
        return repaired
    }

    private suspend fun persistRepaired(entity: TtsProviderEntity) {
        ctx.daos.ttsProviderDao.upsertTtsProvider(entity)
    }

    override val collectionPath = "tts_providers"

    override suspend fun claimPending() = ctx.daos.ttsProviderDao.claimPending()

    override suspend fun getClaimedForSync() = ctx.daos.ttsProviderDao.getClaimedForSync()

    override suspend fun markSynced(entities: List<TtsProviderEntity>) = ctx.daos.ttsProviderDao.markSynced(entities.map { it.id })

    override fun getEntityId(entity: TtsProviderEntity) = entity.id

    override suspend fun toFirestore(local: TtsProviderEntity) =
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
        TtsProviderEntity(
            id = doc.id,
            displayName = doc.getEncryptedField(ctx, "display_name") ?: "",
            provider =
                com.browntowndev.pocketcrew.domain.model.inference.ApiProvider
                    .valueOf(doc.getString("provider") ?: "OPENAI"),
            voiceName = doc.getEncryptedField(ctx, "voice_name") ?: "",
            modelName = doc.getEncryptedField(ctx, "model_name"),
            baseUrl = doc.getEncryptedField(ctx, "base_url"),
            credentialAlias = doc.getString("credential_alias") ?: "",
            updatedAt = doc.getLong("updated_at") ?: 0L,
            deleted = doc.getBoolean("deleted") ?: false,
        )

    override suspend fun upsertLocal(
        entity: TtsProviderEntity,
        doc: DocumentSnapshot,
        overwrite: Boolean,
    ) {
        val existing = ctx.daos.ttsProviderDao.getTtsProvider(entity.id)
        if (existing == null || entity.updatedAt >= existing.updatedAt) {
            ctx.daos.ttsProviderDao.upsertTtsProvider(entity.copy(pendingSync = 0))
        } else {
            ctx.loggingPort.debug(
                FirestoreSyncContext.TAG,
                "TtsProvider ${entity.id} local is newer or same age, skipping remote update",
            )
        }
    }

    override suspend fun deleteLocal(id: String) {
        ctx.daos.ttsProviderDao.softDeleteById(id)
    }
}
