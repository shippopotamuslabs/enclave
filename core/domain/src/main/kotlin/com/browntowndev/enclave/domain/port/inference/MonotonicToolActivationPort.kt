package com.browntowndev.enclave.domain.port.inference

import com.browntowndev.enclave.domain.model.chat.ChatId

/**
 * Session-scoped set of tools that have been intent-activated.
 * Once a tool name is added it is never removed for the lifetime of the session.
 */
interface MonotonicToolActivationPort {
    /**
     * Merges [newNames] into the persisted activated set for [chatId].
     * Returns the full union (previously activated ∪ newNames).
     * This set only grows; names are never removed within a session.
     */
    fun union(
        chatId: ChatId,
        newNames: Set<String>,
    ): Set<String>

    /**
     * Clears the record for [chatId].
     * Called when the chat session ends or a new conversation starts.
     */
    fun clearSession(chatId: ChatId)
}
