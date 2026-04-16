package com.browntowndev.pocketcrew.domain.util

import com.browntowndev.pocketcrew.domain.model.chat.ChatMessage
import com.browntowndev.pocketcrew.domain.model.chat.Role

/**
 * Utility for compressing chat history to fit within a model's context window.
 */
object ChatHistoryCompressor {

    /**
     * Compresses the provided history list by dropping oldest messages until the 
     * total estimated tokens (including the system prompt and a safety buffer)
     * fits within the context window.
     *
     * Drops messages in pairs (User + Assistant) when possible to maintain
     * conversational flow.
     *
     * @param history The list of chat messages to compress.
     * @param systemPrompt The system prompt that will be prepended to the history.
     * @param contextWindowTokens The total context window size of the model.
     * @param bufferTokens Safety buffer to reserve for the model's new response.
     * @param charsPerToken Estimated characters per token for token count calculation.
     * @return A truncated list of messages that fits within the target budget.
     */
    fun compressHistory(
        history: List<ChatMessage>,
        systemPrompt: String,
        contextWindowTokens: Int,
        bufferTokens: Int = 1000,
        charsPerToken: Int = 4,
    ): List<ChatMessage> {
        if (history.isEmpty()) return emptyList()

        val maxAllowedTokens = contextWindowTokens - bufferTokens
        if (maxAllowedTokens <= 0) return emptyList()

        val systemPromptTokens = systemPrompt.length / charsPerToken
        
        var currentHistory = history.toMutableList()
        
        while (currentHistory.isNotEmpty()) {
            val totalChars = currentHistory.sumOf { it.content.length }
            val estimatedHistoryTokens = totalChars / charsPerToken
            val totalEstimatedTokens = systemPromptTokens + estimatedHistoryTokens

            if (totalEstimatedTokens <= maxAllowedTokens) {
                break
            }

            // Drop the oldest message. 
            // If the oldest is a USER message followed by an ASSISTANT, drop both to preserve turn pairs.
            // If the list is just one message, drop it.
            if (currentHistory.size >= 2 && 
                currentHistory[0].role == Role.USER && 
                currentHistory[1].role == Role.ASSISTANT) {
                currentHistory.removeAt(0)
                currentHistory.removeAt(0)
            } else {
                currentHistory.removeAt(0)
            }
        }

        return currentHistory
    }
}
