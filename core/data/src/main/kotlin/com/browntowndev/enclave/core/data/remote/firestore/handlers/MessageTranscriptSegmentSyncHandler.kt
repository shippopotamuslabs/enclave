package com.browntowndev.enclave.core.data.remote.firestore.handlers

import com.browntowndev.enclave.core.data.local.*
import com.browntowndev.enclave.core.data.remote.EntitySyncAdapter
import com.browntowndev.enclave.core.data.remote.firestore.*
import com.browntowndev.enclave.core.data.remote.firestore.mappers.*
import com.browntowndev.enclave.core.data.remote.messageTranscriptSegmentFromFirestore
import com.browntowndev.enclave.core.data.remote.messageTranscriptSegmentToFirestoreMap
import com.browntowndev.enclave.core.data.security.SensitiveEntityFieldEncryptors
import com.browntowndev.enclave.domain.model.chat.*
import com.browntowndev.enclave.domain.model.config.*
import com.browntowndev.enclave.domain.model.inference.*
import com.google.firebase.firestore.DocumentSnapshot

internal class MessageTranscriptSegmentSyncHandler(
    private val ctx: FirestoreSyncContext,
) : EntitySyncAdapter<MessageTranscriptSegmentEntity> {
    override suspend fun repairForSync(entity: MessageTranscriptSegmentEntity): MessageTranscriptSegmentEntity {
        val repaired = SensitiveEntityFieldEncryptors.encrypt(entity, ctx.sensitiveSelfHealer)
        if (repaired == entity) return entity
        persistRepaired(repaired)
        return repaired
    }

    private suspend fun persistRepaired(entity: MessageTranscriptSegmentEntity) {
        ctx.daos.messageTranscriptSegmentDao.upsert(entity)
    }

    override val collectionPath = "message_transcript_segments"

    override suspend fun claimPending() = ctx.daos.messageTranscriptSegmentDao.claimPending()

    override suspend fun getClaimedForSync() = ctx.daos.messageTranscriptSegmentDao.getClaimedForSync()

    override suspend fun markSynced(entities: List<MessageTranscriptSegmentEntity>) =
        ctx.daos.messageTranscriptSegmentDao.markSynced(entities.map { it.id })

    override fun getEntityId(entity: MessageTranscriptSegmentEntity) = entity.id

    override suspend fun toFirestore(local: MessageTranscriptSegmentEntity): Map<String, Any?> {
        val message =
            ctx.daos.messageDao
                .getMessageById(local.messageId)
                ?.message ?: return emptyMap()
        val chatId = message.chatId.value
        return messageTranscriptSegmentToFirestoreMap(
            local,
            chatId,
            local.text.encryptedForFirestore(ctx, collectionPath, local.id, "text"),
        )
    }

    override fun fromFirestore(doc: DocumentSnapshot): MessageTranscriptSegmentEntity? =
        messageTranscriptSegmentFromFirestore(doc) { doc.getEncryptedField(ctx, "text") }

    override suspend fun upsertLocal(
        entity: MessageTranscriptSegmentEntity,
        doc: DocumentSnapshot,
        overwrite: Boolean,
    ) {
        val existing = ctx.daos.messageTranscriptSegmentDao.getById(entity.id)
        if (existing == null || entity.updatedAt > existing.updatedAt) {
            ctx.daos.messageTranscriptSegmentDao.upsert(entity.copy(pendingSync = 0))
        } else if (entity.updatedAt == existing.updatedAt) {
            val merged = entity.copy(pendingSync = 0)
            if (merged != existing.copy(pendingSync = 0)) {
                ctx.daos.messageTranscriptSegmentDao.upsert(merged)
            }
        } else {
            ctx.loggingPort.debug(
                FirestoreSyncContext.TAG,
                "MessageTranscriptSegment ${entity.id} local is newer or same age, skipping remote update",
            )
        }
    }

    override suspend fun deleteLocal(id: String) {
        ctx.daos.messageTranscriptSegmentDao.softDeleteById(id)
    }
}
