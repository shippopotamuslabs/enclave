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

internal class ApiModelConfigSyncHandler(
    private val ctx: FirestoreSyncContext,
) : EntitySyncAdapter<ApiModelConfigurationEntity> {
    override suspend fun repairForSync(entity: ApiModelConfigurationEntity): ApiModelConfigurationEntity {
        val repaired = SensitiveEntityFieldEncryptors.encrypt(entity, ctx.sensitiveSelfHealer)
        if (repaired == entity) return entity
        persistRepaired(repaired)
        return repaired
    }

    private suspend fun persistRepaired(entity: ApiModelConfigurationEntity) {
        ctx.daos.apiModelConfigurationsDao.upsert(entity)
    }

    override val collectionPath = "api_model_configs"

    override suspend fun claimPending() = ctx.daos.apiModelConfigurationsDao.claimPending()

    override suspend fun getClaimedForSync() = ctx.daos.apiModelConfigurationsDao.getClaimedForSync()

    override suspend fun markSynced(entities: List<ApiModelConfigurationEntity>) =
        ctx.daos.apiModelConfigurationsDao.markSynced(entities.map { it.id })

    override fun getEntityId(entity: ApiModelConfigurationEntity) = entity.id.value

    override suspend fun toFirestore(local: ApiModelConfigurationEntity) =
        mapOf(
            "api_credentials_id" to local.apiCredentialsId.value,
            "display_name" to local.displayName.encryptedForFirestore(ctx, collectionPath, local.id.value, "display_name"),
            "custom_headers" to
                local.customHeaders.encryptedForFirestore(
                    ctx,
                    collectionPath,
                    local.id.value,
                    "custom_headers",
                ),
            "max_tokens" to local.maxTokens,
            "context_window" to local.contextWindow,
            "temperature" to local.temperature,
            "top_p" to local.topP,
            "top_k" to local.topK,
            "min_p" to local.minP,
            "frequency_penalty" to local.frequencyPenalty,
            "presence_penalty" to local.presencePenalty,
            "system_prompt" to local.systemPrompt.encryptedForFirestore(ctx, collectionPath, local.id.value, "system_prompt"),
            "max_tokens_disabled" to local.maxTokensDisabled,
            "temperature_disabled" to local.temperatureDisabled,
            "top_p_disabled" to local.topPDisabled,
            "top_k_disabled" to local.topKDisabled,
            "min_p_disabled" to local.minPDisabled,
            "frequency_penalty_disabled" to local.frequencyPenaltyDisabled,
            "presence_penalty_disabled" to local.presencePenaltyDisabled,
            "reasoning_effort" to local.reasoningEffort,
            "openrouter_provider_sort" to local.openRouterProviderSort,
            "openrouter_allow_fallbacks" to local.openRouterAllowFallbacks,
            "openrouter_require_parameters" to local.openRouterRequireParameters,
            "openrouter_data_collection" to local.openRouterDataCollectionPolicy,
            "openrouter_zdr" to local.openRouterZeroDataRetention,
            "updated_at" to local.updatedAt,
            "deleted" to local.deleted,
        )

    override fun fromFirestore(doc: DocumentSnapshot) =
        ApiModelConfigurationEntity(
            id = ApiModelConfigurationId(doc.id),
            apiCredentialsId = ApiCredentialsId(doc.getString("api_credentials_id") ?: ""),
            displayName = doc.getEncryptedField(ctx, "display_name") ?: "",
            customHeaders = doc.getEncryptedField(ctx, "custom_headers") ?: "{}",
            maxTokens = doc.getLong("max_tokens")?.toInt() ?: 4096,
            contextWindow = doc.getLong("context_window")?.toInt() ?: 4096,
            temperature = doc.getDouble("temperature") ?: 0.7,
            topP = doc.getDouble("top_p") ?: 0.95,
            topK = doc.getLong("top_k")?.toInt(),
            minP = doc.getDouble("min_p") ?: 0.05,
            frequencyPenalty = doc.getDouble("frequency_penalty") ?: 0.0,
            presencePenalty = doc.getDouble("presence_penalty") ?: 0.0,
            systemPrompt = doc.getEncryptedField(ctx, "system_prompt") ?: "",
            maxTokensDisabled = doc.getBoolean("max_tokens_disabled") ?: false,
            temperatureDisabled = doc.getBoolean("temperature_disabled") ?: false,
            topPDisabled = doc.getBoolean("top_p_disabled") ?: false,
            topKDisabled = doc.getBoolean("top_k_disabled") ?: false,
            minPDisabled = doc.getBoolean("min_p_disabled") ?: false,
            frequencyPenaltyDisabled = doc.getBoolean("frequency_penalty_disabled") ?: false,
            presencePenaltyDisabled = doc.getBoolean("presence_penalty_disabled") ?: false,
            reasoningEffort = doc.getString("reasoning_effort"),
            openRouterProviderSort = doc.getString("openrouter_provider_sort"),
            openRouterAllowFallbacks = doc.getBoolean("openrouter_allow_fallbacks"),
            openRouterRequireParameters = doc.getBoolean("openrouter_require_parameters"),
            openRouterDataCollectionPolicy = doc.getString("openrouter_data_collection"),
            openRouterZeroDataRetention = doc.getBoolean("openrouter_zdr"),
            updatedAt = doc.getLong("updated_at") ?: 0L,
            deleted = doc.getBoolean("deleted") ?: false,
        )

    override suspend fun upsertLocal(
        entity: ApiModelConfigurationEntity,
        doc: DocumentSnapshot,
        overwrite: Boolean,
    ) {
        val existing = ctx.daos.apiModelConfigurationsDao.getById(entity.id)
        if (existing == null || entity.updatedAt >= existing.updatedAt) {
            ctx.daos.apiModelConfigurationsDao.upsert(entity.copy(pendingSync = 0))
        } else {
            ctx.loggingPort.debug(
                FirestoreSyncContext.TAG,
                "ApiModelConfig ${entity.id.value} local is newer or same age, skipping remote update",
            )
        }
    }

    override suspend fun deleteLocal(id: String) {
        ctx.daos.apiModelConfigurationsDao.softDeleteById(ApiModelConfigurationId(id))
    }
}
