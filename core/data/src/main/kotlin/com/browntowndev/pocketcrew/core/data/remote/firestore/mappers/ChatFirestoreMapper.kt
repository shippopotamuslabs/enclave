package com.browntowndev.pocketcrew.core.data.remote.firestore.mappers

import com.browntowndev.pocketcrew.core.data.local.ChatEntity
import com.browntowndev.pocketcrew.core.data.remote.firestore.FirestoreSyncContext
import com.browntowndev.pocketcrew.core.data.remote.firestore.getEncryptedField
import com.browntowndev.pocketcrew.domain.model.chat.ChatId
import com.google.firebase.firestore.DocumentSnapshot

internal object ChatFirestoreMapper {
    fun fromDocument(
        ctx: FirestoreSyncContext,
        doc: DocumentSnapshot,
    ): ChatEntity? {
        val name = doc.getEncryptedField(ctx, "name") ?: ""
        return ChatEntity(
            id = ChatId(doc.id),
            name = name,
            created = java.util.Date(doc.getLong("created_at") ?: 0L),
            lastModified = java.util.Date(doc.getLong("last_modified") ?: 0L),
            pinned = doc.getBoolean("pinned") ?: false,
            updatedAt = doc.getLong("updated_at") ?: 0L,
            deleted = doc.getBoolean("deleted") ?: false,
            pendingSync = 0,
        )
    }
}
