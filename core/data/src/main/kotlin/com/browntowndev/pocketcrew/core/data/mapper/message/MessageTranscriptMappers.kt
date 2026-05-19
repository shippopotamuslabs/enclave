package com.browntowndev.pocketcrew.core.data.mapper.message

import com.browntowndev.pocketcrew.core.data.local.MessageTranscriptKindEntity
import com.browntowndev.pocketcrew.core.data.local.MessageTranscriptSegmentEntity
import com.browntowndev.pocketcrew.domain.model.chat.MessageId
import com.browntowndev.pocketcrew.domain.model.chat.MessageTranscriptSegment

fun MessageTranscriptSegmentEntity.toDomain(): MessageTranscriptSegment =
    when (kind) {
        MessageTranscriptKindEntity.THINKING ->
            MessageTranscriptSegment.Thinking(
                ordinal = ordinal,
                text = text,
                thinkingDurationSeconds = thinkingDurationSeconds,
                thinkingStartTime = thinkingStartTime,
                thinkingEndTime = thinkingEndTime,
            )
        else ->
            MessageTranscriptSegment.AssistantText(
                ordinal = ordinal,
                text = text,
            )
    }

fun MessageTranscriptSegment.toEntity(
    messageId: MessageId,
    id: String,
): MessageTranscriptSegmentEntity =
    when (this) {
        is MessageTranscriptSegment.Thinking ->
            MessageTranscriptSegmentEntity(
                id = id,
                messageId = messageId,
                ordinal = ordinal,
                kind = MessageTranscriptKindEntity.THINKING,
                text = text,
                thinkingStartTime = thinkingStartTime,
                thinkingEndTime = thinkingEndTime,
                thinkingDurationSeconds = thinkingDurationSeconds,
                pendingSync = 0,
                updatedAt = 0L,
                deleted = false,
            )
        is MessageTranscriptSegment.AssistantText ->
            MessageTranscriptSegmentEntity(
                id = id,
                messageId = messageId,
                ordinal = ordinal,
                kind = MessageTranscriptKindEntity.ASSISTANT_TEXT,
                text = text,
                thinkingStartTime = null,
                thinkingEndTime = null,
                thinkingDurationSeconds = null,
                pendingSync = 0,
                updatedAt = 0L,
                deleted = false,
            )
    }
