package com.browntowndev.pocketcrew.domain.port.inference
import com.browntowndev.pocketcrew.domain.model.chat.ChatMessage

/**
 * Domain port for managing LLM conversation lifecycle.
 * Encapsulates conversation lifecycle and ensures proper initialization.
 *
 * Each ConversationManager instance is bound to a specific Engine and must be
 * injected with the corresponding engine qualifier.
 */
interface ConversationManagerPort {
    /**
     * Returns the active conversation, initializing it if needed.
     * Thread-safe: concurrent calls will return the same conversation instance.
     *
     * @return The active ConversationPort instance
     */
    fun getConversation(): ConversationPort

    /**
     * Closes the current conversation and releases resources.
     * After calling this, a new conversation will be created on next getConversation call.
     */
    fun closeConversation()

    /**
     * Sets the historical messages for the conversation.
     * These will be injected as a preface when the conversation is initialized or updated.
     *
     * @param messages The list of historical messages.
     */
    fun setHistory(messages: List<ChatMessage>)

    /**
     * Closes the underlying engine and releases all resources.
     * After calling this, the ConversationManager should not be used.
     */
    fun closeEngine()
}
