package com.browntowndev.enclave.domain.model.chat

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
 * @property isSyncing True if this is a placeholder awaiting sync — derived from created == Date(0), which is the marker set by ensurePlaceholderMessage.
 */
data class Chat(
    val id: ChatId,
    val name: String,
    val created: Date,
    val lastModified: Date,
    val pinned: Boolean = false,
    val isSyncing: Boolean = false,
)
