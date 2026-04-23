package com.browntowndev.pocketcrew.feature.inference

import com.browntowndev.pocketcrew.domain.model.chat.ChatMessage
import com.browntowndev.pocketcrew.domain.model.inference.GenerationOptions
import com.browntowndev.pocketcrew.domain.model.inference.ModelType

/**
 * Internal interface for managing LLM conversation lifecycle within the inference module.
 * Encapsulates conversation lifecycle and ensures proper initialization.
 */
interface ConversationManager {
    /**
     * Returns the active conversation, initializing it if needed.
     * Thread-safe: concurrent calls will return the same conversation instance.
     *
     * @param modelType The type of model to get the configuration for.
     * @param options Per-request generation options (e.g., sampler overrides).
     * @return The active LiteRtConversation instance
     */
    suspend fun getConversation(
        modelType: ModelType, 
        options: GenerationOptions? = null,
        onLoadingStarted: suspend () -> Unit = {}
    ): LiteRtConversation

    /**
     * Closes the current conversation and releases resources.
     * After calling this, a new conversation will be created on next getConversation call.
     */
    suspend fun closeConversation()

    /**
     * Sets the historical messages for the conversation.
     * These will be injected as a preface when the conversation is initialized or updated.
     *
     * @param messages The list of historical messages.
     */
    suspend fun setHistory(messages: List<ChatMessage>)

    /**
     * Closes the underlying engine and releases all resources.
     * After calling this, the ConversationManager should not be used.
     */
    suspend fun closeEngine()

    /**
     * Cancels the current ongoing generation. 
     * This signals the underlying tool executor to abort any ongoing or pending tool calls
     * to safely interrupt the native C++ engine.
     */
    fun cancelCurrentGeneration()

    /**
     * Cancels the active LiteRT conversation's generation process.
     * This immediately signals the native C++ engine to stop producing tokens,
     * causing the active [LiteRtConversation.sendMessageAsync] flow to terminate
     * with a CancellationException via MessageCallback.onError.
     */
    fun cancelProcess()
}
