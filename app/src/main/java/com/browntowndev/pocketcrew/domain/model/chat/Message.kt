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
 * @property modelType The model type used to generate this message (for Crew mode tracking)
 */
data class Message(
    val id: Long = 0,
    val chatId: Long,
    val content: Content,
    val role: Role = Role.USER,
    val messageState: MessageState = MessageState.PROCESSING,
    val createdAt: Long = System.currentTimeMillis(),
    val thinkingDurationSeconds: Long? = null,
    val thinkingRaw: String? = null,
    val thinkingSteps: List<String> = emptyList(),
    val modelType: ModelType? = null,
)
