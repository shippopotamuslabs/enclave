package com.browntowndev.pocketcrew.domain.port.inference

import kotlinx.coroutines.flow.Flow

/**
 * Domain abstraction for conversation-based LLM inference.
 * This port defines the interface for sending messages and receiving streaming responses.
 *
 * Implementations (in the inference layer) will wrap LiteRT-LM or other inference runtimes.
 */
interface ConversationPort {
    /**
     * Sends a message to the LLM and returns a flow of text chunks.
     * @param message The message to send to the LLM.
     * @return A flow emitting text chunks from the streaming response.
     */
    fun sendMessageAsync(message: String): Flow<String>
}
