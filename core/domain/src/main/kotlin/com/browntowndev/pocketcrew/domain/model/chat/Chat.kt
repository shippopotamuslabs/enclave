package com.browntowndev.pocketcrew.domain.model.chat

import java.util.Date

/**
 * Domain model representing a chat conversation.
 * Immutable by design to ensure thread-safety and predictable state.
 *
 * @property id Unique identifier for the chat
 * @property name Display name of the chat
 * @property created Timestamp when the chat was created
 * @property lastModified Timestamp of the last message in the chat
 * @property pinned Whether the chat is pinned to the top
 */
data class Chat(
    val id: Long = 0,
    val name: String,
    val created: Date,
    val lastModified: Date,
    val pinned: Boolean = false
)
