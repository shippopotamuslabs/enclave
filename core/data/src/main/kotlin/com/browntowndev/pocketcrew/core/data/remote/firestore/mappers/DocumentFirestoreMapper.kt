package com.browntowndev.pocketcrew.core.data.remote.firestore.mappers

import com.browntowndev.pocketcrew.core.data.local.DocumentEntity
import com.browntowndev.pocketcrew.core.data.remote.firestore.FirestoreSyncContext
import com.browntowndev.pocketcrew.core.data.remote.firestore.getEncryptedField
import com.browntowndev.pocketcrew.domain.model.artifact.MimeType
import com.browntowndev.pocketcrew.domain.model.chat.MessageId
import com.google.firebase.firestore.DocumentSnapshot

internal object DocumentFirestoreMapper {
    fun fromDocument(
        ctx: FirestoreSyncContext,
        doc: DocumentSnapshot,
    ): DocumentEntity? {
        val json = doc.getEncryptedField(ctx, "json") ?: return null
        val title = doc.getEncryptedField(ctx, "title") ?: return null
        val messageIdStr = doc.getString("message_id") ?: return null
        val messageId = MessageId(messageIdStr)
        val targetFormatStr = doc.getString("target_format")
        val targetFormat =
            targetFormatStr?.let {
                runCatching { MimeType.valueOf(it) }.getOrNull()
            }
        return DocumentEntity(
            id = doc.id,
            messageId = messageId,
            json = json,
            title = title,
            author = doc.getEncryptedField(ctx, "author"),
            targetFormat = targetFormat,
            createdAt = doc.getLong("created_at") ?: 0L,
            updatedAt = doc.getLong("updated_at") ?: 0L,
            deleted = doc.getBoolean("deleted") ?: false,
            pendingSync = 0,
        )
    }
}
