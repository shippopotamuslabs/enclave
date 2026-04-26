package com.browntowndev.pocketcrew.feature.inference

import com.browntowndev.pocketcrew.domain.model.inference.GenerationOptions
import kotlinx.coroutines.flow.Flow

/**
 * Internal abstraction for conversation-based LLM inference within the inference module.
 * This interface defines the contract for sending messages and receiving streaming responses.
 *
 * Implementations wrap LiteRT-LM or other inference runtimes.
 */
interface LiteRtConversation {
    /**
     * Sends a message to the LLM and returns a flow of response segments.
     * @param message The message to send to the LLM.
     * @param options Per-request generation options (e.g., reasoning budget).
     * @return A flow emitting response segments from the streaming response.
     */
    fun sendMessageAsync(message: String, options: GenerationOptions? = null): Flow<ConversationResponse>

    /**
     * Cancels the current ongoing generation on the LiteRT Conversation.
     * This signals the native C++ engine to stop producing tokens immediately.
     * The active [sendMessageAsync] flow will receive a CancellationException via
     * its MessageCallback.onError callback, which the caller should handle gracefully.
     */
    fun cancelProcess()
}

/**
 * Represents a streaming response segment, which can contain user-facing text,
 * hidden reasoning (thought), or both.
 */
data class ConversationResponse(
    val text: String = "",
    val thought: String = ""
)
