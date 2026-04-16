package com.browntowndev.pocketcrew.domain.util

import com.browntowndev.pocketcrew.domain.model.chat.ChatMessage
import com.browntowndev.pocketcrew.domain.model.chat.Role
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ChatHistoryCompressorTest {

    @Test
    fun `compressHistory should return full history if within budget`() {
        val history = listOf(
            ChatMessage(Role.USER, "Hello"),
            ChatMessage(Role.ASSISTANT, "Hi there!")
        )
        val result = ChatHistoryCompressor.compressHistory(
            history = history,
            systemPrompt = "You are an assistant.",
            contextWindowTokens = 2048,
            bufferTokens = 1000
        )
        assertEquals(2, result.size)
        assertEquals(history, result)
    }

    @Test
    fun `compressHistory should drop oldest messages to fit budget`() {
        // 1000 chars = ~250 tokens
        val history = listOf(
            ChatMessage(Role.USER, "A".repeat(1000)), // 250
            ChatMessage(Role.ASSISTANT, "B".repeat(1000)), // 250
            ChatMessage(Role.USER, "C".repeat(1000)), // 250
            ChatMessage(Role.ASSISTANT, "D".repeat(1000)) // 250
        )
        // Total history = 1000 tokens
        // System prompt = 0
        // Window = 1500, Buffer = 1000 -> Budget = 500
        val result = ChatHistoryCompressor.compressHistory(
            history = history,
            systemPrompt = "",
            contextWindowTokens = 1500,
            bufferTokens = 1000
        )
        
        // Should drop first two (USER + ASSISTANT pair) to fit within 500 tokens
        assertEquals(2, result.size)
        assertEquals("C".repeat(1000), result[0].content)
        assertEquals("D".repeat(1000), result[1].content)
    }

    @Test
    fun `compressHistory should drop pairs to maintain turn structure`() {
        val history = listOf(
            ChatMessage(Role.USER, "1".repeat(800)),
            ChatMessage(Role.ASSISTANT, "2".repeat(800)),
            ChatMessage(Role.USER, "3".repeat(800))
        )
        // Each is 200 tokens. Total 600 tokens.
        // Window = 1400, Buffer = 1000 -> Budget = 400
        val result = ChatHistoryCompressor.compressHistory(
            history = history,
            systemPrompt = "",
            contextWindowTokens = 1400,
            bufferTokens = 1000
        )
        
        // Dropping oldest pair (1 and 2) leaves only message 3 (200 tokens).
        assertEquals(1, result.size)
        assertEquals("3".repeat(800), result[0].content)
    }

    @Test
    fun `compressHistory should return empty if nothing fits`() {
        val history = listOf(
            ChatMessage(Role.USER, "X".repeat(5000))
        )
        val result = ChatHistoryCompressor.compressHistory(
            history = history,
            systemPrompt = "",
            contextWindowTokens = 1000,
            bufferTokens = 1000
        )
        assertEquals(0, result.size)
    }
}
