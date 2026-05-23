package com.browntowndev.enclave.core.data.remote.firestore.handlers

import com.browntowndev.enclave.core.data.local.*
import com.browntowndev.enclave.core.data.remote.EntitySyncAdapter
import com.browntowndev.enclave.core.data.remote.firestore.*
import com.browntowndev.enclave.core.data.remote.firestore.mappers.*
import com.browntowndev.enclave.domain.model.chat.*
import com.browntowndev.enclave.domain.model.config.*
import com.browntowndev.enclave.domain.model.inference.*
import com.google.firebase.firestore.DocumentSnapshot

internal class LocalModelSyncHandler(
    private val ctx: FirestoreSyncContext,
) : EntitySyncAdapter<LocalModelEntity> {
    override val collectionPath = "local_models"

    override suspend fun claimPending() = ctx.daos.localModelsDao.claimPending()

    override suspend fun getClaimedForSync() = ctx.daos.localModelsDao.getClaimedForSync()

    override suspend fun markSynced(entities: List<LocalModelEntity>) = ctx.daos.localModelsDao.markSynced(entities.map { it.id })

    override fun getEntityId(entity: LocalModelEntity) = entity.id.value

    override suspend fun toFirestore(local: LocalModelEntity) =
        mapOf(
            "model_file_format" to local.modelFileFormat.name,
            "display_name" to local.displayName,
            "huggingface_model_name" to local.huggingFaceModelName,
            "remote_filename" to local.remoteFilename,
            "local_filename" to local.localFilename,
            "sha256" to local.sha256,
            "size_in_bytes" to local.sizeInBytes,
            "is_multimodal" to local.isMultimodal,
            "mmproj_remote_filename" to local.mmprojRemoteFilename,
            "mmproj_local_filename" to local.mmprojLocalFilename,
            "mmproj_sha256" to local.mmprojSha256,
            "mmproj_size_in_bytes" to local.mmprojSizeInBytes,
            "updated_at" to local.updatedAt,
            "deleted" to local.deleted,
        )

    override fun fromFirestore(doc: DocumentSnapshot) =
        LocalModelEntity(
            id = LocalModelId(doc.id),
            modelFileFormat = ModelFileFormat.valueOf(doc.getString("model_file_format") ?: "GGUF"),
            displayName = doc.getString("display_name") ?: "",
            huggingFaceModelName = doc.getString("huggingface_model_name") ?: "",
            remoteFilename = doc.getString("remote_filename") ?: "",
            localFilename = doc.getString("local_filename") ?: "",
            sha256 = doc.getString("sha256") ?: "",
            sizeInBytes = doc.getLong("size_in_bytes") ?: 0L,
            isMultimodal = doc.getBoolean("is_multimodal") ?: false,
            mmprojRemoteFilename = doc.getString("mmproj_remote_filename"),
            mmprojLocalFilename = doc.getString("mmproj_local_filename"),
            mmprojSha256 = doc.getString("mmproj_sha256"),
            mmprojSizeInBytes = doc.getLong("mmproj_size_in_bytes"),
            updatedAt = doc.getLong("updated_at") ?: 0L,
            deleted = doc.getBoolean("deleted") ?: false,
        )

    override suspend fun upsertLocal(
        entity: LocalModelEntity,
        doc: DocumentSnapshot,
        overwrite: Boolean,
    ) {
        val existingById = ctx.daos.localModelsDao.getById(entity.id)
        val existingByKey = ctx.daos.localModelsDao.getBySha256(entity.sha256)
        val existing = existingById ?: existingByKey

        if (existing == null || existing.id != entity.id || entity.updatedAt >= existing.updatedAt) {
            val newEntity = entity.copy(id = existing?.id ?: entity.id, pendingSync = 0)
            ctx.daos.localModelsDao.upsert(newEntity)
        } else {
            ctx.loggingPort.debug(
                FirestoreSyncContext.TAG,
                "LocalModel ${entity.id.value} local is newer or same age, skipping remote update",
            )
        }
    }

    override suspend fun deleteLocal(id: String) {
        ctx.daos.localModelsDao.softDeleteById(LocalModelId(id))
    }
}
