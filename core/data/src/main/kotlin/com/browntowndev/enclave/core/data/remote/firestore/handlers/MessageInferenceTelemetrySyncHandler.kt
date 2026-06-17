package com.browntowndev.enclave.core.data.remote.firestore.handlers

import com.browntowndev.enclave.core.data.local.MessageInferenceTelemetryEntity
import com.browntowndev.enclave.core.data.mapper.telemetry.coalesceTokenFieldsWith
import com.browntowndev.enclave.core.data.remote.EntitySyncAdapter
import com.browntowndev.enclave.core.data.remote.firestore.FirestoreSyncContext
import com.browntowndev.enclave.core.data.remote.messageInferenceTelemetryFromFirestore
import com.browntowndev.enclave.core.data.remote.messageInferenceTelemetryToFirestoreMap
import com.google.firebase.firestore.DocumentSnapshot

internal class MessageInferenceTelemetrySyncHandler(
    private val ctx: FirestoreSyncContext,
) : EntitySyncAdapter<MessageInferenceTelemetryEntity> {
    override suspend fun repairForSync(entity: MessageInferenceTelemetryEntity): MessageInferenceTelemetryEntity = entity

    override val collectionPath = "message_inference_telemetry"

    override suspend fun claimPending() = ctx.daos.messageInferenceTelemetryDao.claimPending()

    override suspend fun getClaimedForSync() = ctx.daos.messageInferenceTelemetryDao.getClaimedForSync()

    override suspend fun markSynced(entities: List<MessageInferenceTelemetryEntity>) =
        ctx.daos.messageInferenceTelemetryDao.markSynced(entities.map { it.id })

    override fun getEntityId(entity: MessageInferenceTelemetryEntity) = entity.id

    override suspend fun toFirestore(local: MessageInferenceTelemetryEntity): Map<String, Any?> =
        messageInferenceTelemetryToFirestoreMap(local)

    override fun fromFirestore(doc: DocumentSnapshot): MessageInferenceTelemetryEntity? = messageInferenceTelemetryFromFirestore(doc)

    override suspend fun upsertLocal(
        entity: MessageInferenceTelemetryEntity,
        doc: DocumentSnapshot,
        overwrite: Boolean,
    ) {
        val existing = ctx.daos.messageInferenceTelemetryDao.getById(entity.id)
        if (existing == null || entity.updatedAt > existing.updatedAt) {
            ctx.daos.messageInferenceTelemetryDao.upsert(entity.copy(pendingSync = 0))
        } else if (entity.updatedAt == existing.updatedAt) {
            val merged = entity.coalesceTokenFieldsWith(existing)
            if (merged != existing.copy(pendingSync = 0)) {
                ctx.daos.messageInferenceTelemetryDao.upsert(merged)
            }
        } else {
            ctx.loggingPort.debug(
                FirestoreSyncContext.TAG,
                "MessageInferenceTelemetry ${entity.id} local is newer or same age, skipping remote update",
            )
        }
    }

    override suspend fun deleteLocal(id: String) {
        ctx.daos.messageInferenceTelemetryDao.softDeleteById(id)
    }
}
