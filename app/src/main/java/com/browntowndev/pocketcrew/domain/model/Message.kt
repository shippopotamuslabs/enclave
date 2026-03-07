package com.browntowndev.pocketcrew.domain.model

/**
 * Domain model representing a message within a chat conversation.
 * Immutable by design to ensure thread-safety and predictable state.
 *
 * @property id Unique identifier for the message
 * @property content The text content of the message
 * @property role The role of the message sender (USER, ASSISTANT, or SYSTEM)
 * @property chatId The ID of the chat this message belongs to
 */
data class Message(
    val id: Long = 0,
    val chatId: Long,
    val content: String,
    val role: Role = Role.USER,
)
