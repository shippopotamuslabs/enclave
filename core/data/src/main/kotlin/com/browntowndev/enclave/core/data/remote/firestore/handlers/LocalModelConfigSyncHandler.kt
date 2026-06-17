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
import kotlinx.coroutines.tasks.await

internal class LocalModelConfigSyncHandler(
    private val ctx: FirestoreSyncContext,
) : EntitySyncAdapter<LocalModelConfigurationEntity> {
    override suspend fun repairForSync(entity: LocalModelConfigurationEntity): LocalModelConfigurationEntity {
        val repaired = SensitiveEntityFieldEncryptors.encrypt(entity, ctx.sensitiveSelfHealer)
        if (repaired == entity) return entity
        persistRepaired(repaired)
        return repaired
    }

    private suspend fun persistRepaired(entity: LocalModelConfigurationEntity) {
        ctx.daos.localModelConfigurationsDao.upsert(entity)
    }

    override val collectionPath = "local_model_configs"

    override suspend fun claimPending() = ctx.daos.localModelConfigurationsDao.claimPending()

    override suspend fun getClaimedForSync() = ctx.daos.localModelConfigurationsDao.getClaimedForSync()

    override suspend fun markSynced(entities: List<LocalModelConfigurationEntity>) =
        ctx.daos.localModelConfigurationsDao.markSynced(
            entities.map {
                it.id
            },
        )

    override fun getEntityId(entity: LocalModelConfigurationEntity) = entity.id.value

    override suspend fun toFirestore(local: LocalModelConfigurationEntity) =
        mapOf(
            "local_model_id" to local.localModelId.value,
            "display_name" to local.displayName.encryptedForFirestore(ctx, collectionPath, local.id.value, "display_name"),
            "temperature" to local.temperature,
            "top_k" to local.topK,
            "top_p" to local.topP,
            "min_p" to local.minP,
            "repetition_penalty" to local.repetitionPenalty,
            "max_tokens" to local.maxTokens,
            "context_window" to local.contextWindow,
            "thinking_enabled" to local.thinkingEnabled,
            "reasoning_budget" to local.reasoningBudget,
            "is_system_preset" to local.isSystemPreset,
            "system_prompt" to
                local.systemPrompt?.encryptedForFirestore(ctx, collectionPath, local.id.value, "system_prompt"),
            "updated_at" to local.updatedAt,
            "deleted" to local.deleted,
        )

    override fun fromFirestore(doc: DocumentSnapshot) =
        LocalModelConfigurationEntity(
            id = LocalModelConfigurationId(doc.id),
            localModelId = LocalModelId(doc.getString("local_model_id") ?: ""),
            displayName = doc.getEncryptedField(ctx, "display_name") ?: "",
            temperature = doc.getDouble("temperature") ?: 0.7,
            topK = doc.getLong("top_k")?.toInt() ?: 40,
            topP = doc.getDouble("top_p") ?: 0.95,
            minP = doc.getDouble("min_p") ?: 0.05,
            repetitionPenalty = doc.getDouble("repetition_penalty") ?: 1.1,
            maxTokens = doc.getLong("max_tokens")?.toInt() ?: 4096,
            contextWindow = doc.getLong("context_window")?.toInt() ?: 4096,
            thinkingEnabled = doc.getBoolean("thinking_enabled") ?: false,
            reasoningBudget =
                doc.getLong("reasoning_budget")?.toInt()
                    ?: LocalModelConfiguration.defaultReasoningBudget(
                        doc.getLong("context_window")?.toInt() ?: 4096,
                    ),
            isSystemPreset = doc.getBoolean("is_system_preset") ?: false,
            systemPrompt = doc.getEncryptedField(ctx, "system_prompt"),
            updatedAt = doc.getLong("updated_at") ?: 0L,
            deleted = doc.getBoolean("deleted") ?: false,
        )

    override suspend fun upsertLocal(
        entity: LocalModelConfigurationEntity,
        doc: DocumentSnapshot,
        overwrite: Boolean,
    ) {
        val entityWithResolvedModel = entity.copy(localModelId = resolveLocalModelId(entity.localModelId))
        val existingById = ctx.daos.localModelConfigurationsDao.getById(entityWithResolvedModel.id)
        val existingByKey =
            ctx.daos.localModelConfigurationsDao.getByModelIdAndName(
                entityWithResolvedModel.localModelId,
                entityWithResolvedModel.displayName,
            )
        val existing = existingById ?: existingByKey

        if (existing == null || existing.id != entityWithResolvedModel.id || entityWithResolvedModel.updatedAt >= existing.updatedAt) {
            val newEntity = entityWithResolvedModel.copy(id = existing?.id ?: entityWithResolvedModel.id, pendingSync = 0)
            ctx.daos.localModelConfigurationsDao.upsert(newEntity)
        } else {
            ctx.loggingPort.debug(
                FirestoreSyncContext.TAG,
                "LocalModelConfig ${entity.id.value} local is newer or same age, skipping remote update",
            )
        }
    }

    override suspend fun deleteLocal(id: String) {
        ctx.daos.localModelConfigurationsDao.softDeleteById(LocalModelConfigurationId(id))
    }

    /**
     * After a data wipe this device may register a model under a new Room id (sha256 dedup)
     * while Firestore configs still reference the cloud document id in [local_model_id].
     */
    private suspend fun resolveLocalModelId(cloudModelId: LocalModelId): LocalModelId {
        if (cloudModelId.value.isBlank() || ctx.daos.localModelsDao.getById(cloudModelId) != null) {
            return cloudModelId
        }
        val userId = ctx.userIdProvider() ?: return cloudModelId
        val snapshot =
            ctx.firestore
                .document("users/$userId/local_models/${cloudModelId.value}")
                .get()
                .await()
        val sha256 =
            snapshot
                .takeIf { it.exists() }
                ?.getString("sha256")
                ?.takeIf { it.isNotBlank() }
        return sha256?.let {
            ctx.daos.localModelsDao
                .getBySha256(it)
                ?.id
        } ?: cloudModelId
    }
}
