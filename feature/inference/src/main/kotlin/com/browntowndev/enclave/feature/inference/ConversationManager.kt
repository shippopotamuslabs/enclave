package com.browntowndev.enclave.feature.inference

import com.browntowndev.enclave.domain.model.chat.ChatMessage
import com.browntowndev.enclave.domain.model.inference.GenerationOptions
import com.browntowndev.enclave.domain.model.inference.ModelType

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
        onLoadingStarted: suspend () -> Unit = {},
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
     * Snapshot of conversation context for local telemetry estimation.
     * History excludes the active user/assistant pair; the current prompt is passed separately.
     */
    fun telemetryContext(): LiteRtTelemetryContext

    /**
     * Closes the underlying engine and releases all resources.
     * After calling this, the ConversationManager should not be used.
     */
    suspend fun closeEngine()

    /**
     * Begins a new generation request and returns an ownership token. Each [sendPrompt] flow
     * must call this once; [endGenerationRequest] in `finally` only affects state when the
     * token still matches the active request.
     */
    fun beginGenerationRequest(): Int

    /**
     * Ends a generation request started by [beginGenerationRequest]. Cancellation is applied
     * only when [requestId] still owns the active request.
     */
    fun endGenerationRequest(requestId: Int)

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
