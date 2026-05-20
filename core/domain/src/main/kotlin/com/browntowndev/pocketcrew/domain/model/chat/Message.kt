package com.browntowndev.pocketcrew.domain.model.chat

import com.browntowndev.pocketcrew.domain.model.chat.MessageState
import com.browntowndev.pocketcrew.domain.model.inference.ModelType

/**
 * Domain model representing a message within a chat conversation.
 * Immutable by design to ensure thread-safety and predictable state.
 *
 * @property id Unique identifier for the message
 * @property content The content of the message (text + pipeline step for Crew mode)
 * @property role The role of the message sender (USER, ASSISTANT, or SYSTEM)
 * @property chatId The ID of the chat this message belongs to. Null means the message has not
 *   yet been assigned to a chat — [com.browntowndev.pocketcrew.domain.usecase.chat.CreateUserMessageUseCase]
 *   will create a new chat on first save.
 * @property messageState Current state of the message in the generation pipeline
 * @property thinkingDurationSeconds Duration of thinking in seconds (computed from timestamps)
 * @property thinkingRaw Raw thinking text as markdown (no chunking)
 * @property thinkingStartTime Timestamp when thinking started
 * @property thinkingEndTime Timestamp when thinking ended
 * @property modelType The model type used to generate this message (for Crew mode tracking)
 * @property transcriptSegments Ordered CoT / answer runs for interleaved transcript UI (empty if not recorded)
 */
data class Message(
    val id: MessageId,
    val chatId: ChatId?,
    val content: Content,
    val role: Role = Role.USER,
    val messageState: MessageState = MessageState.PROCESSING,
    val createdAt: Long? = null,
    val thinkingDurationSeconds: Long? = null,
    val thinkingRaw: String? = null,
    val thinkingStartTime: Long? = null,
    val thinkingEndTime: Long? = null,
    val modelType: ModelType? = null,
    val webSearchSources: List<WebSearchSource> = emptyList(),
    val transcriptSegments: List<MessageTranscriptSegment> = emptyList(),
    val updatedAt: Long? = null,
) {
    /**
     * Computed duration in seconds from timestamps.
     * Returns null if either timestamp is missing or the span is non-positive.
     * Sub-second spans round up to one second so denormalized UI does not read "0s".
     */
    val computedDurationSeconds: Long?
        get() {
            val start = thinkingStartTime ?: return null
            val end = thinkingEndTime ?: return null
            if (end < start) return null
            val ms = end - start
            return when {
                ms <= 0L -> null
                ms < 1000L -> 1L
                else -> ms / 1000
            }
        }
}
