package com.browntowndev.enclave.core.data.remote.firestore.handlers

import com.browntowndev.enclave.core.data.local.UtilityModelEntity
import com.browntowndev.enclave.core.data.remote.EntitySyncAdapter
import com.browntowndev.enclave.core.data.remote.firestore.FirestoreSyncContext
import com.browntowndev.enclave.domain.model.config.UtilityModelId
import com.browntowndev.enclave.domain.model.config.UtilityType
import com.browntowndev.enclave.domain.model.inference.ModelFileFormat
import com.google.firebase.firestore.DocumentSnapshot

internal class UtilityModelSyncHandler(
    private val ctx: FirestoreSyncContext,
) : EntitySyncAdapter<UtilityModelEntity> {
    override val collectionPath = "utility_models"

    override suspend fun claimPending() = ctx.daos.utilityModelsDao.claimPending()

    override suspend fun getClaimedForSync() = ctx.daos.utilityModelsDao.getClaimedForSync()

    override suspend fun markSynced(entities: List<UtilityModelEntity>) =
        ctx.daos.utilityModelsDao.markSynced(entities.map { it.id })

    override fun getEntityId(entity: UtilityModelEntity): String = entity.id.value

    override suspend fun toFirestore(local: UtilityModelEntity): Map<String, Any?> =
        mapOf(
            "utility_type" to local.utilityType.apiValue,
            "model_file_format" to local.modelFileFormat.name,
            "huggingface_model_name" to local.huggingFaceModelName,
            "remote_filename" to local.remoteFilename,
            "local_filename" to local.localFilename,
            "sha256" to local.sha256,
            "size_in_bytes" to local.sizeInBytes,
            "updated_at" to local.updatedAt,
            "deleted" to local.deleted,
        )

    override fun fromFirestore(doc: DocumentSnapshot): UtilityModelEntity {
        val utilityType =
            UtilityType.fromApiValue(doc.getString("utility_type"))
                ?: error("Missing or unknown utility_type in ${doc.id}")
        return UtilityModelEntity(
            id = UtilityModelId(doc.id),
            utilityType = utilityType,
            modelFileFormat = ModelFileFormat.valueOf(doc.getString("model_file_format") ?: "GGUF"),
            huggingFaceModelName = doc.getString("huggingface_model_name") ?: "",
            remoteFilename = doc.getString("remote_filename") ?: "",
            localFilename = doc.getString("local_filename") ?: "",
            sha256 = doc.getString("sha256") ?: "",
            sizeInBytes = doc.getLong("size_in_bytes") ?: 0L,
            updatedAt = doc.getLong("updated_at") ?: 0L,
            deleted = doc.getBoolean("deleted") ?: false,
        )
    }

    override suspend fun upsertLocal(
        entity: UtilityModelEntity,
        doc: DocumentSnapshot,
        overwrite: Boolean,
    ) {
        val existingById = ctx.daos.utilityModelsDao.getById(entity.id)
        val existingByType = ctx.daos.utilityModelsDao.getByUtilityType(entity.utilityType)
        val existingBySha = ctx.daos.utilityModelsDao.getBySha256(entity.sha256)
        val existing = existingById ?: existingByType ?: existingBySha

        if (existing == null || existing.id != entity.id || entity.updatedAt >= existing.updatedAt) {
            val newEntity = entity.copy(id = existing?.id ?: entity.id, pendingSync = 0)
            ctx.daos.utilityModelsDao.upsert(newEntity)
        } else {
            ctx.loggingPort.debug(
                FirestoreSyncContext.TAG,
                "UtilityModel ${entity.id.value} local is newer or same age, skipping remote update",
            )
        }
    }

    override suspend fun deleteLocal(id: String) {
        ctx.daos.utilityModelsDao.softDeleteById(UtilityModelId(id))
    }
}
