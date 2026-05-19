package com.browntowndev.pocketcrew.core.data.remote.firestore.mappers

import com.browntowndev.pocketcrew.core.data.local.MessageEntity
import com.browntowndev.pocketcrew.core.data.remote.firestore.FirestoreSyncContext
import com.browntowndev.pocketcrew.core.data.remote.firestore.getEncryptedField
import com.browntowndev.pocketcrew.domain.model.chat.ChatId
import com.browntowndev.pocketcrew.domain.model.chat.MessageId
import com.browntowndev.pocketcrew.domain.model.chat.MessageState
import com.browntowndev.pocketcrew.domain.model.chat.Role
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.model.inference.PipelineStep
import com.google.firebase.firestore.DocumentSnapshot

internal object MessageFirestoreMapper {
    fun fromDocument(
        ctx: FirestoreSyncContext,
        doc: DocumentSnapshot,
    ): MessageEntity? {
        val chatIdStr = doc.getString("chat_id")
        if (chatIdStr.isNullOrBlank()) return null
        val content = doc.getEncryptedField(ctx, "content") ?: ""

        return MessageEntity(
            id = MessageId(doc.id),
            chatId = ChatId(chatIdStr),
            content = content,
            role = Role.fromApiValue(doc.getString("role") ?: "user"),
            userMessageId = doc.getString("user_message_id")?.takeIf { it.isNotBlank() }?.let { MessageId(it) },
            imageUrisJson = doc.getEncryptedField(ctx, "image_uris_json"),
            messageState = doc.getString("message_state")?.let { MessageState.fromApiValue(it) } ?: MessageState.COMPLETE,
            createdAt = doc.getLong("created_at") ?: 0L,
            modelType = doc.getString("model_type")?.let { ModelType.fromApiValue(it) },
            thinkingDurationSeconds = doc.getLong("thinking_duration_seconds"),
            thinkingRaw = doc.getEncryptedField(ctx, "thinking_raw"),
            thinkingStartTime = doc.getLong("thinking_start_time"),
            thinkingEndTime = doc.getLong("thinking_end_time"),
            pipelineStep =
                doc.getString("pipeline_step")?.let { stepStr ->
                    PipelineStep.entries.find { it.name == stepStr }
                },
            artifactsJson = doc.getEncryptedField(ctx, "artifacts_json"),
            generatedMediaUrisJson = doc.getEncryptedField(ctx, "generated_media_uris_json"),
            generatedMediaAssetIdsJson = doc.getEncryptedField(ctx, "generated_media_asset_ids_json"),
            generatedMediaTypesJson = doc.getString("generated_media_types_json"),
            generatedMediaPromptsJson = doc.getEncryptedField(ctx, "generated_media_prompts_json"),
            updatedAt = doc.getLong("updated_at") ?: 0L,
            deleted = doc.getBoolean("deleted") ?: false,
            pendingSync = 0,
        )
    }
}
