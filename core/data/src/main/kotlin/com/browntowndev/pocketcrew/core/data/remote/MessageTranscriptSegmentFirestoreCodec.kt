package com.browntowndev.pocketcrew.core.data.remote

import com.browntowndev.pocketcrew.core.data.local.MessageTranscriptSegmentEntity
import com.browntowndev.pocketcrew.domain.model.chat.MessageId
import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.DocumentSnapshot

/**
 * Firestore field mapping for [MessageTranscriptSegmentEntity].
 * Kept as top-level functions so round-trip behavior can be unit-tested without
 * reaching into [FirestoreDataSource] private adapters.
 */
internal fun messageTranscriptSegmentToFirestoreMap(
    local: MessageTranscriptSegmentEntity,
    chatId: String,
    textBlob: Blob,
): Map<String, Any?> {
    val data =
        mutableMapOf<String, Any?>(
            "message_id" to local.messageId.value,
            "chat_id" to chatId,
            "ordinal" to local.ordinal,
            "kind" to local.kind,
            "text" to textBlob,
            "updated_at" to local.updatedAt,
            "deleted" to local.deleted,
        )
    local.thinkingStartTime?.let { data["thinking_start_time"] = it }
    local.thinkingEndTime?.let { data["thinking_end_time"] = it }
    local.thinkingDurationSeconds?.let { data["thinking_duration_seconds"] = it }
    return data
}

@Suppress("ReturnCount", "ComplexCondition")
internal fun messageTranscriptSegmentFromFirestore(
    doc: DocumentSnapshot,
    decryptTextBlob: (Blob) -> String?,
): MessageTranscriptSegmentEntity? {
    val chatIdStr = doc.getString("chat_id")
    val messageIdStr = doc.getString("message_id")
    val text = doc.getBlob("text")?.let(decryptTextBlob)
    val kind = doc.getString("kind")
    val ordinalLong = doc.getLong("ordinal")

    if (chatIdStr.isNullOrBlank() || messageIdStr.isNullOrBlank() || text == null || kind == null || ordinalLong == null) {
        return null
    }

    return MessageTranscriptSegmentEntity(
        id = doc.id,
        messageId = MessageId(messageIdStr),
        ordinal = ordinalLong.toInt(),
        kind = kind,
        text = text,
        thinkingStartTime = if (doc.contains("thinking_start_time")) doc.getLong("thinking_start_time") else null,
        thinkingEndTime = if (doc.contains("thinking_end_time")) doc.getLong("thinking_end_time") else null,
        thinkingDurationSeconds =
            if (doc.contains("thinking_duration_seconds")) {
                doc.getLong("thinking_duration_seconds")
            } else {
                null
            },
        pendingSync = 0,
        updatedAt = doc.getLong("updated_at") ?: 0L,
        deleted = doc.getBoolean("deleted") ?: false,
    )
}
