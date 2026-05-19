package com.browntowndev.pocketcrew.core.data.remote.firestore.handlers

import com.browntowndev.pocketcrew.core.data.local.*
import com.browntowndev.pocketcrew.core.data.remote.EntitySyncAdapter
import com.browntowndev.pocketcrew.core.data.remote.firestore.*
import com.browntowndev.pocketcrew.core.data.remote.firestore.mappers.*
import com.browntowndev.pocketcrew.domain.model.chat.*
import com.browntowndev.pocketcrew.domain.model.config.*
import com.browntowndev.pocketcrew.domain.model.inference.*
import com.google.firebase.firestore.DocumentSnapshot

internal class DefaultModelSyncHandler(
    private val ctx: FirestoreSyncContext,
) : EntitySyncAdapter<DefaultModelEntity> {
    override val collectionPath = "default_models"

    override suspend fun claimPending() = ctx.daos.defaultModelsDao.claimPending()

    override suspend fun getClaimedForSync() = ctx.daos.defaultModelsDao.getClaimedForSync()

    override suspend fun markSynced(entities: List<DefaultModelEntity>) =
        ctx.daos.defaultModelsDao.markSynced(entities.map { it.modelType })

    override fun getEntityId(entity: DefaultModelEntity) = entity.modelType.name

    override suspend fun toFirestore(local: DefaultModelEntity) =
        mapOf(
            "local_config_id" to local.localConfigId?.value,
            "api_config_id" to local.apiConfigId?.value,
            "tts_provider_id" to local.ttsProviderId?.value,
            "media_provider_id" to local.mediaProviderId?.value,
            "updated_at" to local.updatedAt,
            "deleted" to local.deleted,
        )

    override fun fromFirestore(doc: DocumentSnapshot) =
        DefaultModelEntity(
            modelType = ModelType.valueOf(doc.id),
            localConfigId = doc.getString("local_config_id")?.let { LocalModelConfigurationId(it) },
            apiConfigId = doc.getString("api_config_id")?.let { ApiModelConfigurationId(it) },
            ttsProviderId = doc.getString("tts_provider_id")?.let { TtsProviderId(it) },
            mediaProviderId = doc.getString("media_provider_id")?.let { MediaProviderId(it) },
            updatedAt = doc.getLong("updated_at") ?: 0L,
            deleted = doc.getBoolean("deleted") ?: false,
        )

    override suspend fun upsertLocal(
        entity: DefaultModelEntity,
        doc: DocumentSnapshot,
        overwrite: Boolean,
    ) {
        val existing = ctx.daos.defaultModelsDao.getDefault(entity.modelType)
        if (existing == null || entity.updatedAt >= existing.updatedAt) {
            ctx.daos.defaultModelsDao.upsert(entity.copy(pendingSync = 0))
        } else {
            ctx.loggingPort.debug(
                FirestoreSyncContext.TAG,
                "DefaultModel ${entity.modelType.name} local is newer or same age, skipping remote update",
            )
        }
    }

    override suspend fun deleteLocal(id: String) {
        ctx.daos.defaultModelsDao.softDeleteById(ModelType.valueOf(id))
    }
}
