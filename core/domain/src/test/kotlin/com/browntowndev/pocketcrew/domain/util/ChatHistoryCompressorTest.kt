package com.browntowndev.pocketcrew.domain.util

import com.browntowndev.pocketcrew.domain.model.chat.ChatMessage
import com.browntowndev.pocketcrew.domain.model.chat.Role
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ChatHistoryCompressorTest {

    private val tokenCounter = JTokkitTokenCounter

    private fun countTokens(text: String): Int = tokenCounter.countTokens(text, null)

    @Test
    fun `compressHistory should return full history if within budget`() {
        val history = listOf(
            ChatMessage(Role.USER, "Hello"),
            ChatMessage(Role.ASSISTANT, "Hi there!")
        )
        val result = ChatHistoryCompressor.compressHistory(
            history = history,
            systemPrompt = "You are an assistant.",
            contextWindowTokens = 4096,
            bufferTokens = 1000
        )
        assertEquals(2, result.size)
        assertEquals(history, result)
    }

    @Test
    fun `compressHistory should drop oldest messages to fit budget`() {
        val msgContent = "alpha bravo charlie delta echo foxtrot golf hotel india juliet kilo lima mike november"
        val tokensPerMsg = countTokens(msgContent)
        val history = listOf(
            ChatMessage(Role.USER, msgContent),
            ChatMessage(Role.ASSISTANT, msgContent),
            ChatMessage(Role.USER, msgContent),
            ChatMessage(Role.ASSISTANT, msgContent),
        )
        // Total tokens = 4 * tokensPerMsg
        // Set budget so only the last 2 messages fit:
        // budget = 2 * tokensPerMsg + 1 (just enough for 2 messages)
        val budget = 2 * tokensPerMsg + 1
        val result = ChatHistoryCompressor.compressHistory(
            history = history,
            systemPrompt = "",
            contextWindowTokens = budget + 1, // +1 for buffer subtraction
            bufferTokens = 1,
        )
        
        // Should drop first two (USER + ASSISTANT pair) to fit within budget
        assertEquals(2, result.size)
    }

    @Test
    fun `compressHistory should drop pairs to maintain turn structure`() {
        val msgContent = "alpha bravo charlie delta echo foxtrot golf hotel india juliet kilo lima mike november"
        val tokensPerMsg = countTokens(msgContent)
        val history = listOf(
            ChatMessage(Role.USER, msgContent),
            ChatMessage(Role.ASSISTANT, msgContent),
            ChatMessage(Role.USER, msgContent)
        )
        // Total = 3 * tokensPerMsg. Budget for only 1 message.
        val budget = tokensPerMsg + 1
        val result = ChatHistoryCompressor.compressHistory(
            history = history,
            systemPrompt = "",
            contextWindowTokens = budget + 1,
            bufferTokens = 1,
        )
        
        // Dropping oldest pair (1 and 2) leaves only message 3
        assertEquals(1, result.size)
        assertEquals(msgContent, result[0].content)
    }

    @Test
    fun `compressHistory should return empty if nothing fits`() {
        val msgContent = "alpha bravo charlie delta echo foxtrot golf hotel india juliet kilo lima mike november"
        val history = listOf(
            ChatMessage(Role.USER, msgContent)
        )
        // Budget = 0, nothing fits
        val result = ChatHistoryCompressor.compressHistory(
            history = history,
            systemPrompt = "",
            contextWindowTokens = 10,
            bufferTokens = 10
        )
        assertEquals(0, result.size)
    }
}