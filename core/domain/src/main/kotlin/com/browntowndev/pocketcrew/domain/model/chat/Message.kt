package com.browntowndev.pocketcrew.domain.model.chat

import com.browntowndev.pocketcrew.domain.model.MessageState
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.model.inference.PipelineStep

/**
 * Domain model representing a message within a chat conversation.
 * Immutable by design to ensure thread-safety and predictable state.
 *
 * @property id Unique identifier for the message
 * @property content The content of the message (text + pipeline step for Crew mode)
 * @property role The role of the message sender (USER, ASSISTANT, or SYSTEM)
 * @property chatId The ID of the chat this message belongs to
 * @property messageState Current state of the message in the generation pipeline
 * @property thinkingDurationSeconds Duration of thinking in seconds (computed from timestamps)
 * @property thinkingRaw Raw thinking text as markdown (no chunking)
 * @property thinkingStartTime Timestamp when thinking started
 * @property thinkingEndTime Timestamp when thinking ended
 * @property modelType The model type used to generate this message (for Crew mode tracking)
 */
data class Message(
    val id: MessageId,
    val chatId: ChatId,
    val content: Content,
    val role: Role = Role.USER,
    val messageState: MessageState = MessageState.PROCESSING,
    val createdAt: Long = System.currentTimeMillis(),
    val thinkingDurationSeconds: Long? = null,
    val thinkingRaw: String? = null,
    val thinkingStartTime: Long? = null,
    val thinkingEndTime: Long? = null,
    val modelType: ModelType? = null,
    val tavilySources: List<TavilySource> = emptyList(),
) {
    /**
     * Computed duration in seconds from timestamps.
     * Returns null if either timestamp is missing.
     */
    val computedDurationSeconds: Long?
        get() = if (thinkingStartTime != null && thinkingEndTime != null) {
            (thinkingEndTime - thinkingStartTime) / 1000
        } else null
}
