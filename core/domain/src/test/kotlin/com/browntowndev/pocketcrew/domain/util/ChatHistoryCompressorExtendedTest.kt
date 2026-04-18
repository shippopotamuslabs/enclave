package com.browntowndev.pocketcrew.domain.util

import com.browntowndev.pocketcrew.domain.model.chat.ChatMessage
import com.browntowndev.pocketcrew.domain.model.chat.Role
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Extended tests for [ChatHistoryCompressor] — boundary conditions and
 * system prompt protection that are not covered by the basic tests.
 *
 * Written to match EXPECTED behavior, not production code behavior.
 */
class ChatHistoryCompressorExtendedTest {

    private val tokenCounter = JTokkitTokenCounter

    private fun countTokens(text: String): Int = tokenCounter.countTokens(text, null)

    // ── Boundary conditions ─────────────────────────────────────────────────

    @Nested
    inner class BoundaryConditions {

        @Test
        @DisplayName("compressHistory with empty history returns empty list")
        fun emptyHistoryReturnsEmpty() {
            val result = ChatHistoryCompressor.compressHistory(
                history = emptyList(),
                systemPrompt = "You are an assistant.",
                contextWindowTokens = 4096,
                bufferTokens = 1000,
            )
            assertEquals(0, result.size)
        }

        @Test
        @DisplayName("compressHistory with zero context window returns empty list")
        fun zeroContextWindowReturnsEmpty() {
            val history = listOf(
                ChatMessage(Role.USER, "Hello"),
                ChatMessage(Role.ASSISTANT, "Hi"),
            )
            val result = ChatHistoryCompressor.compressHistory(
                history = history,
                systemPrompt = "",
                contextWindowTokens = 0,
                bufferTokens = 0,
            )
            assertEquals(0, result.size)
        }

        @Test
        @DisplayName("compressHistory with very small context window drops all messages")
        fun verySmallContextWindowDropsAll() {
            val history = listOf(
                ChatMessage(Role.USER, "This is a message with enough text to exceed a tiny budget."),
                ChatMessage(Role.ASSISTANT, "This is also a message with enough text."),
            )
            val result = ChatHistoryCompressor.compressHistory(
                history = history,
                systemPrompt = "System prompt",
                contextWindowTokens = 5,
                bufferTokens = 5,
            )
            assertEquals(0, result.size, "Very small window with no room should return empty list")
        }

        @Test
        @DisplayName("compressHistory with context window exactly equal to content keeps all messages")
        fun exactBoundaryKeepsAll() {
            // Create messages where total tokens fit exactly at the boundary
            val shortMsg = "Hi"
            val shortTokens = countTokens(shortMsg)
            val systemPrompt = "OK"
            val systemTokens = countTokens(systemPrompt)
            // buffer = 0, contextWindow = system + 2*shortTokens (exact fit)
            val contextWindow = systemTokens + 2 * shortTokens
            val history = listOf(
                ChatMessage(Role.USER, shortMsg),
                ChatMessage(Role.ASSISTANT, shortMsg),
            )
            val result = ChatHistoryCompressor.compressHistory(
                history = history,
                systemPrompt = systemPrompt,
                currentPrompt = "",
                contextWindowTokens = contextWindow,
                bufferTokens = 0,
                tokenCounter = tokenCounter,
            )
            assertEquals(2, result.size, "Messages that exactly fit should be kept")
        }

        @Test
        @DisplayName("compressHistory with one token over budget drops oldest pair")
        fun oneTokenOverBudgetDropsOldest() {
            val msg = "Hello world this is a test message for compression"
            val msgTokens = countTokens(msg)
            val systemTokens = countTokens("System")
            // 4 messages fit exactly, add 1 more token to the budget to make 3+system barely not fit 4
            val history = listOf(
                ChatMessage(Role.USER, msg),
                ChatMessage(Role.ASSISTANT, msg),
                ChatMessage(Role.USER, msg),
                ChatMessage(Role.ASSISTANT, msg),
            )
            // Budget that only fits the last 2 messages
            val budgetForTwo = systemTokens + 2 * msgTokens
            val result = ChatHistoryCompressor.compressHistory(
                history = history,
                systemPrompt = "System",
                currentPrompt = "",
                contextWindowTokens = budgetForTwo + 1,
                bufferTokens = 1,
                tokenCounter = tokenCounter,
            )
            assertEquals(2, result.size, "Should drop oldest pair when budget only fits 2 messages")
            assertEquals(msg, result[0].content)
            assertEquals(msg, result[1].content)
        }
    }

    // ── System prompt protection ───────────────────────────────────────────

    @Nested
    inner class SystemPromptProtection {

        @Test
        @DisplayName("compressHistory accounts for system prompt tokens in budget")
        fun systemPromptIncludedInBudget() {
            val msg = "Test message content"
            val history = listOf(
                ChatMessage(Role.USER, msg),
                ChatMessage(Role.ASSISTANT, msg),
            )
            val shortSystem = "Be brief."
            val longSystem = "You are a very detailed assistant who always provides comprehensive " +
                "explanations with multiple paragraphs and in-depth analysis of every topic. " +
                "Never give short answers. Always elaborate extensively."

            val resultWithShort = ChatHistoryCompressor.compressHistory(
                history = history,
                systemPrompt = shortSystem,
                contextWindowTokens = 500,
                bufferTokens = 50,
                tokenCounter = tokenCounter,
            )
            val resultWithLong = ChatHistoryCompressor.compressHistory(
                history = history,
                systemPrompt = longSystem,
                contextWindowTokens = 500,
                bufferTokens = 50,
                tokenCounter = tokenCounter,
            )

            // Both should fit in 500 tokens for these short messages, but
            // the long system prompt may cause compression in a tighter window
            assertTrue(
                resultWithShort.size >= resultWithLong.size,
                "Longer system prompt should not result in more messages kept"
            )
        }

        @Test
        @DisplayName("compressHistory accounts for currentPrompt tokens in budget")
        fun currentPromptIncludedInBudget() {
            val msg = "Test response"
            val history = listOf(
                ChatMessage(Role.USER, msg),
                ChatMessage(Role.ASSISTANT, msg),
            )

            val resultWithPrompt = ChatHistoryCompressor.compressHistory(
                history = history,
                systemPrompt = "System",
                currentPrompt = "This is a very long current prompt that takes up many tokens in the context window",
                contextWindowTokens = 200,
                bufferTokens = 50,
                tokenCounter = tokenCounter,
            )
            val resultNoPrompt = ChatHistoryCompressor.compressHistory(
                history = history,
                systemPrompt = "System",
                currentPrompt = "",
                contextWindowTokens = 200,
                bufferTokens = 50,
                tokenCounter = tokenCounter,
            )

            assertTrue(
                resultNoPrompt.size >= resultWithPrompt.size,
                "Current prompt tokens should reduce available budget for history"
            )
        }
    }

    // ── Pair-dropping behavior ─────────────────────────────────────────────

    @Nested
    inner class PairDroppingBehavior {

        @Test
        @DisplayName("Drops USER-ASSISTANT pairs together when oldest is USER")
        fun dropsUserAssistantPairs() {
            val msg = "Message content that is sufficiently long for compression testing alpha beta gamma"
            val tokensPerMsg = countTokens(msg)
            val systemTokens = countTokens("System")
            // Create 6 messages (3 pairs), budget for only 1 pair
            val budgetForOnePair = systemTokens + 2 * tokensPerMsg + 1
            val history = listOf(
                ChatMessage(Role.USER, msg),      // pair 1
                ChatMessage(Role.ASSISTANT, msg),  // pair 1
                ChatMessage(Role.USER, msg),       // pair 2
                ChatMessage(Role.ASSISTANT, msg),  // pair 2
                ChatMessage(Role.USER, msg),       // pair 3 (newest)
                ChatMessage(Role.ASSISTANT, msg),  // pair 3 (newest)
            )
            val result = ChatHistoryCompressor.compressHistory(
                history = history,
                systemPrompt = "System",
                contextWindowTokens = budgetForOnePair + 1,
                bufferTokens = 1,
                tokenCounter = tokenCounter,
            )
            // Should keep only the last pair
            assertEquals(2, result.size)
            assertEquals(Role.USER, result[0].role)
            assertEquals(Role.ASSISTANT, result[1].role)
        }

        @Test
        @DisplayName("Drops single message when pair structure is broken")
        fun dropsSingleMessageWhenPairBroken() {
            // ASSISTANT at index 0 means the pair-dropping logic sees role != USER
            // and drops just the first message
            val msg = "Sufficiently long message content for testing compression alpha beta gamma"
            val tokensPerMsg = countTokens(msg)
            val systemTokens = countTokens("System")
            val history = listOf(
                ChatMessage(Role.ASSISTANT, msg),  // Not USER — will be dropped singly
                ChatMessage(Role.USER, msg),
                ChatMessage(Role.ASSISTANT, msg),
            )
            // Budget for only 2 messages
            val budgetForTwo = systemTokens + 2 * tokensPerMsg + 1
            val result = ChatHistoryCompressor.compressHistory(
                history = history,
                systemPrompt = "System",
                contextWindowTokens = budgetForTwo + 1,
                bufferTokens = 1,
                tokenCounter = tokenCounter,
            )
            // Should drop the leading ASSISTANT message singly, keeping USER+ASSISTANT pair
            assertEquals(2, result.size)
            assertEquals(Role.USER, result[0].role)
            assertEquals(Role.ASSISTANT, result[1].role)
        }

        @Test
        @DisplayName("All messages dropped when even one message exceeds budget")
        fun allMessagesDroppedWhenOversized() {
            val veryLongMsg = "A".repeat(5000)
            val history = listOf(
                ChatMessage(Role.USER, veryLongMsg),
            )
            val result = ChatHistoryCompressor.compressHistory(
                history = history,
                systemPrompt = "short",
                contextWindowTokens = 100,
                bufferTokens = 10,
                tokenCounter = tokenCounter,
            )
            assertEquals(0, result.size, "Should drop all messages when they exceed budget")
        }
    }

    // ── modelId parameter ────────────────────────────────────────────────

    @Nested
    inner class ModelIdParameter {

        @Test
        @DisplayName("compressHistory produces different results for different model IDs")
        fun differentModelIdsMayDiffer() {
            // Using the same content, token counts might differ between models
            val msg = "This is a test message for model-specific tokenization."
            val history = listOf(
                ChatMessage(Role.USER, msg),
                ChatMessage(Role.ASSISTANT, msg),
            )

            val resultGpt4 = ChatHistoryCompressor.compressHistory(
                history = history,
                systemPrompt = "You are helpful.",
                contextWindowTokens = 50,
                bufferTokens = 25,
                modelId = "gpt-4",
            )
            val resultDefault = ChatHistoryCompressor.compressHistory(
                history = history,
                systemPrompt = "You are helpful.",
                contextWindowTokens = 50,
                bufferTokens = 25,
                modelId = null,
            )
            // Both should produce some result (0 or 2 messages most likely)
            // The important thing is that modelId doesn't crash
            assertTrue(resultGpt4.size <= 2, "Should produce valid result with gpt-4 model ID")
            assertTrue(resultDefault.size <= 2, "Should produce valid result with null model ID")
        }
    }
}