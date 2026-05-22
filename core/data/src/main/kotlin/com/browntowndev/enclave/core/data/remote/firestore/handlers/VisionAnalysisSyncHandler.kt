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

internal class VisionAnalysisSyncHandler(
    private val ctx: FirestoreSyncContext,
) : EntitySyncAdapter<MessageVisionAnalysisEntity> {
    override suspend fun repairForSync(entity: MessageVisionAnalysisEntity): MessageVisionAnalysisEntity {
        val repaired = SensitiveEntityFieldEncryptors.encrypt(entity, ctx.sensitiveSelfHealer)
        if (repaired == entity) return entity
        persistRepaired(repaired)
        return repaired
    }

    private suspend fun persistRepaired(entity: MessageVisionAnalysisEntity) {
        ctx.daos.messageVisionAnalysisDao.upsert(entity)
    }

    override val collectionPath = "vision_analysis"

    override suspend fun claimPending() = ctx.daos.messageVisionAnalysisDao.claimPending()

    override suspend fun getClaimedForSync() = ctx.daos.messageVisionAnalysisDao.getClaimedForSync()

    override suspend fun markSynced(entities: List<MessageVisionAnalysisEntity>) =
        ctx.daos.messageVisionAnalysisDao.markSynced(entities.map { it.id })

    override fun getEntityId(entity: MessageVisionAnalysisEntity) = entity.id

    override suspend fun toFirestore(local: MessageVisionAnalysisEntity): Map<String, Any?> {
        val message =
            ctx.daos.messageDao
                .getMessageById(local.userMessageId)
                ?.message ?: return emptyMap()
        val chatId = message.chatId.value
        return mapOf(
            "user_message_id" to local.userMessageId.value,
            "chat_id" to chatId,
            "image_uri" to local.imageUri.encryptedForFirestore(ctx, collectionPath, local.id, "image_uri"),
            "prompt_text" to local.promptText.encryptedForFirestore(ctx, collectionPath, local.id, "prompt_text"),
            "analysis_text" to local.analysisText.encryptedForFirestore(ctx, collectionPath, local.id, "analysis_text"),
            "model_type" to local.modelType.name,
            "created_at" to local.createdAt,
            "updated_at" to local.updatedAt,
            "deleted" to local.deleted,
        )
    }

    override fun fromFirestore(doc: DocumentSnapshot): MessageVisionAnalysisEntity? {
        val chatIdStr = doc.getString("chat_id")
        if (chatIdStr.isNullOrBlank()) return null
        val userMessageIdStr = doc.getString("user_message_id")
        if (userMessageIdStr.isNullOrBlank()) return null
        val promptText = doc.getEncryptedField(ctx, "prompt_text") ?: return null
        val analysisText = doc.getEncryptedField(ctx, "analysis_text") ?: return null
        return MessageVisionAnalysisEntity(
            id = doc.id,
            userMessageId = MessageId(userMessageIdStr),
            imageUri = doc.getEncryptedField(ctx, "image_uri") ?: "",
            promptText = promptText,
            analysisText = analysisText,
            modelType = ModelType.valueOf(doc.getString("model_type") ?: "LLM"),
            createdAt = doc.getLong("created_at") ?: 0L,
            updatedAt = doc.getLong("updated_at") ?: 0L,
            deleted = doc.getBoolean("deleted") ?: false,
        )
    }

    override suspend fun upsertLocal(
        entity: MessageVisionAnalysisEntity,
        doc: DocumentSnapshot,
        overwrite: Boolean,
    ) {
        val existing = ctx.daos.messageVisionAnalysisDao.getById(entity.id)
        if (existing == null || entity.updatedAt >= existing.updatedAt) {
            ctx.daos.messageVisionAnalysisDao.upsert(entity.copy(pendingSync = 0))
        } else {
            ctx.loggingPort.debug(
                FirestoreSyncContext.TAG,
                "VisionAnalysis ${entity.id} local is newer or same age, skipping remote update",
            )
        }
    }

    override suspend fun deleteLocal(id: String) {
        ctx.daos.messageVisionAnalysisDao.softDeleteById(id)
    }
}
