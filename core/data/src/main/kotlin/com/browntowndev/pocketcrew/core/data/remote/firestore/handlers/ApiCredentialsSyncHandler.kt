package com.browntowndev.pocketcrew.core.data.remote.firestore.handlers

import com.browntowndev.pocketcrew.core.data.local.*
import com.browntowndev.pocketcrew.core.data.remote.EntitySyncAdapter
import com.browntowndev.pocketcrew.core.data.remote.firestore.*
import com.browntowndev.pocketcrew.core.data.remote.firestore.mappers.*
import com.browntowndev.pocketcrew.core.data.security.SensitiveEntityFieldEncryptors
import com.browntowndev.pocketcrew.domain.model.chat.*
import com.browntowndev.pocketcrew.domain.model.config.*
import com.browntowndev.pocketcrew.domain.model.inference.*
import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.DocumentSnapshot

internal class ApiCredentialsSyncHandler(
    private val ctx: FirestoreSyncContext,
) : EntitySyncAdapter<ApiCredentialsEntity> {
    override suspend fun repairForSync(entity: ApiCredentialsEntity): ApiCredentialsEntity {
        val repaired = SensitiveEntityFieldEncryptors.encrypt(entity, ctx.sensitiveSelfHealer)
        if (repaired == entity) return entity
        persistRepaired(repaired)
        return repaired
    }

    private suspend fun persistRepaired(entity: ApiCredentialsEntity) {
        ctx.daos.apiCredentialsDao.upsert(entity)
    }

    override val collectionPath = "api_credentials"

    override suspend fun claimPending() = ctx.daos.apiCredentialsDao.claimPending()

    override suspend fun getClaimedForSync() = ctx.daos.apiCredentialsDao.getClaimedForSync()

    override suspend fun markSynced(entities: List<ApiCredentialsEntity>) = ctx.daos.apiCredentialsDao.markSynced(entities.map { it.id })

    override fun getEntityId(entity: ApiCredentialsEntity) = entity.id.value

    override suspend fun toFirestore(local: ApiCredentialsEntity) =
        mapOf(
            "display_name" to local.displayName.encryptedForFirestore(ctx, collectionPath, local.id.value, "display_name"),
            "provider" to local.provider.name,
            "base_url" to local.baseUrl?.let { it.encryptedForFirestore(ctx, collectionPath, local.id.value, "base_url") },
            "model_id" to local.modelId,
            "is_multimodal" to local.isMultimodal,
            "credential_alias" to local.credentialAlias,
            "encrypted_api_key" to (local.encryptedApiKey?.let { Blob.fromBytes(it) }),
            "api_key_signature" to local.apiKeySignature,
            "created_at" to local.createdAt,
            "updated_at" to local.updatedAt,
            "deleted" to local.deleted,
        )

    override fun fromFirestore(doc: DocumentSnapshot) =
        ApiCredentialsEntity(
            id = ApiCredentialsId(doc.id),
            displayName = doc.getEncryptedField(ctx, "display_name") ?: "",
            provider =
                com.browntowndev.pocketcrew.domain.model.inference.ApiProvider
                    .valueOf(doc.getString("provider") ?: "OPENAI"),
            baseUrl = doc.getEncryptedField(ctx, "base_url"),
            modelId = doc.getString("model_id") ?: "",
            isMultimodal = doc.getBoolean("is_multimodal") ?: false,
            credentialAlias = doc.getString("credential_alias") ?: "",
            encryptedApiKey = doc.getBlob("encrypted_api_key")?.toBytes(),
            apiKeySignature = doc.getString("api_key_signature"),
            createdAt = doc.getLong("created_at") ?: 0L,
            updatedAt = doc.getLong("updated_at") ?: 0L,
            deleted = doc.getBoolean("deleted") ?: false,
        )

    override suspend fun upsertLocal(
        entity: ApiCredentialsEntity,
        doc: DocumentSnapshot,
        overwrite: Boolean,
    ) {
        val existing = ctx.daos.apiCredentialsDao.getById(entity.id)
        if (existing == null || entity.updatedAt >= existing.updatedAt) {
            ctx.daos.apiCredentialsDao.upsert(entity.copy(pendingSync = 0))
        } else {
            ctx.loggingPort.debug(
                FirestoreSyncContext.TAG,
                "ApiCredentials ${entity.id.value} local is newer or same age, skipping remote update",
            )
        }
    }

    override suspend fun deleteLocal(id: String) {
        ctx.daos.apiCredentialsDao.softDeleteById(ApiCredentialsId(id))
    }
}
