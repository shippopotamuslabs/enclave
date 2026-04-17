package com.browntowndev.pocketcrew.domain.usecase.chat

import com.browntowndev.pocketcrew.domain.model.chat.ChatMessage
import com.browntowndev.pocketcrew.domain.model.chat.Role
import com.browntowndev.pocketcrew.domain.port.inference.LlmInferencePort
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.model.inference.GenerationOptions
import com.browntowndev.pocketcrew.domain.util.TokenCounter
import kotlinx.coroutines.flow.toList

internal class RollingSummarizer(
    private val loggingPort: LoggingPort,
    private val tokenCounter: TokenCounter,
) {
    suspend fun summarize(
        messages: List<ChatMessage>,
        service: LlmInferencePort,
    ): String? {
        if (messages.size < 4) return null // Need enough context to summarize

        // Slice oldest 50% by tokens (protecting index 0 System Prompt if it exists)
        val systemPrompt = messages.firstOrNull { it.role == Role.SYSTEM }
        val history = messages.filter { it.role != Role.SYSTEM }
        val totalTokens = history.sumOf { tokenCounter.countTokens(it.content) }
        val targetTokens = totalTokens / 2

        var accumulatedTokens = 0
        var splitIndex = 0
        for (i in history.indices) {
            accumulatedTokens += tokenCounter.countTokens(history[i].content)
            if (accumulatedTokens >= targetTokens) {
                splitIndex = i + 1
                break
            }
        }

        val sliceToSummarize = history.subList(0, splitIndex)
        if (sliceToSummarize.isEmpty()) return null

        val prompt = """
            Please provide a comprehensive but concise summary of the following conversation history.
            This summary will be used as context for future turns.
            
            History to summarize:
            ${sliceToSummarize.joinToString("\n") { "${it.role.name}: ${it.content}" }}
            
            Summary:
        """.trimIndent()

        return try {
            val responseFlow = service.sendPrompt(prompt, GenerationOptions(reasoningBudget = 0, maxTokens = 1024))
            val result = responseFlow.toList()
                .filterIsInstance<com.browntowndev.pocketcrew.domain.port.inference.InferenceEvent.PartialResponse>()
                .joinToString("") { it.chunk }
            result.trim().takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            loggingPort.error("RollingSummarizer", "Failed to generate summary: ${e.message}")
            null
        }
    }
}
