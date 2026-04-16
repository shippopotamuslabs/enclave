package com.browntowndev.pocketcrew.domain.port.inference

import com.browntowndev.pocketcrew.domain.model.inference.GenerationOptions
import kotlinx.coroutines.flow.Flow

/**
 * Domain abstraction for conversation-based LLM inference.
 * This port defines the interface for sending messages and receiving streaming responses.
 *
 * Implementations (in the inference layer) will wrap LiteRT-LM or other inference runtimes.
 */
interface ConversationPort {
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
