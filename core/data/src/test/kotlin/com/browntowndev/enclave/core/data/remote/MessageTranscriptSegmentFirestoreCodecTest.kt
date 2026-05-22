package com.browntowndev.enclave.core.data.remote

import com.browntowndev.enclave.core.data.local.MessageTranscriptKindEntity
import com.browntowndev.enclave.core.data.local.MessageTranscriptSegmentEntity
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.google.firebase.firestore.DocumentSnapshot
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("MessageTranscriptSegmentFirestoreCodec")
class MessageTranscriptSegmentFirestoreCodecTest {
    @Test
    @DisplayName("round trip preserves assistant text without thinking metadata")
    fun roundTripAssistantTextWithoutThinkingMetadata() {
        val entity =
            MessageTranscriptSegmentEntity(
                id = "seg-a",
                messageId = MessageId("msg-1"),
                ordinal = 0,
                kind = MessageTranscriptKindEntity.ASSISTANT_TEXT,
                text = "visible answer",
                thinkingStartTime = null,
                thinkingEndTime = null,
                thinkingDurationSeconds = null,
                pendingSync = 1,
                updatedAt = 777L,
                deleted = false,
            )
        val map = messageTranscriptSegmentToFirestoreMap(entity, "chat-z", entity.text)
        val doc = transcriptSegmentDoc(entity.id, map)

        val back = messageTranscriptSegmentFromFirestore(doc) { doc.getString("text") }

        assertEquals(entity.copy(pendingSync = 0), back)
    }

    @Test
    @DisplayName("round trip preserves thinking segment and optional timestamps")
    fun roundTripThinkingSegmentWithOptionalTimestamps() {
        val entity =
            MessageTranscriptSegmentEntity(
                id = "seg-b",
                messageId = MessageId("msg-2"),
                ordinal = 1,
                kind = MessageTranscriptKindEntity.THINKING,
                text = "chain of thought…",
                thinkingStartTime = 100L,
                thinkingEndTime = 200L,
                thinkingDurationSeconds = 42L,
                pendingSync = 1,
                updatedAt = 888L,
                deleted = false,
            )
        val map = messageTranscriptSegmentToFirestoreMap(entity, "chat-y", entity.text)
        val doc = transcriptSegmentDoc(entity.id, map)

        val back = messageTranscriptSegmentFromFirestore(doc) { doc.getString("text") }

        assertEquals(entity.copy(pendingSync = 0), back)
    }

    @Test
    @DisplayName("fromFirestore returns null when chat_id is blank")
    fun fromFirestoreReturnsNullWhenChatIdMissing() {
        val doc = mockk<DocumentSnapshot>(relaxed = true)
        every { doc.getString("chat_id") } returns "   "
        assertNull(messageTranscriptSegmentFromFirestore(doc) { "ignored" })
    }

    private fun transcriptSegmentDoc(
        id: String,
        data: Map<String, Any?>,
    ): DocumentSnapshot {
        val doc = mockk<DocumentSnapshot>()
        every { doc.id } returns id
        every { doc.getString("chat_id") } returns data["chat_id"] as String?
        every { doc.getString("message_id") } returns data["message_id"] as String?
        every { doc.getString("text") } returns data["text"] as String?
        every { doc.getString("kind") } returns data["kind"] as String?
        every { doc.getLong("ordinal") } returns (data["ordinal"] as Number).toLong()
        every { doc.getLong("updated_at") } returns data["updated_at"] as Long?
        every { doc.getBoolean("deleted") } returns data["deleted"] as Boolean?
        every { doc.contains("thinking_start_time") } returns data.containsKey("thinking_start_time")
        every { doc.getLong("thinking_start_time") } returns data["thinking_start_time"] as Long?
        every { doc.contains("thinking_end_time") } returns data.containsKey("thinking_end_time")
        every { doc.getLong("thinking_end_time") } returns data["thinking_end_time"] as Long?
        every { doc.contains("thinking_duration_seconds") } returns data.containsKey("thinking_duration_seconds")
        every { doc.getLong("thinking_duration_seconds") } returns data["thinking_duration_seconds"] as Long?
        return doc
    }
}
