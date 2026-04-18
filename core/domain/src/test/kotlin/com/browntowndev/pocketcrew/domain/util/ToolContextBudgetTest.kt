package com.browntowndev.pocketcrew.domain.util

import com.browntowndev.pocketcrew.domain.model.chat.ChatMessage
import com.browntowndev.pocketcrew.domain.model.chat.Role
import com.browntowndev.pocketcrew.domain.model.inference.GenerationOptions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Nested

class ToolContextBudgetTest {

    private val tokenCounter = JTokkitTokenCounter
    private val modelId = "gpt-4"

    private fun makeOptions(
        contextWindow: Int = 4096,
        maxTokens: Int = 1024,
    ) = GenerationOptions(
        reasoningBudget = maxTokens,
        contextWindow = contextWindow,
    )

    // ---- evaluate() tests (local model path) ----

    @Nested
    inner class Evaluate {

        @Test
        fun `evaluate returns contextFull when tokens exceed threshold`() {
            val options = makeOptions(contextWindow = 1000, maxTokens = 100)
            // threshold = 1000 * 0.85 = 850 tokens
            // With 900 tokens used, we exceed the 850 threshold
            val result = ToolContextBudget.evaluate(
                contextWindowTokens = 1000,
                systemPromptTokens = 100,
                historyTokens = 600,
                transientToolResultTokens = 200,
                options = options,
                modelId = modelId,
                tokenCounter = tokenCounter,
            )
            assertTrue(result.contextFull)
            assertEquals(900, result.totalTokens)
        }

        @Test
        fun `evaluate returns not contextFull when tokens within threshold`() {
            val options = makeOptions(contextWindow = 10000, maxTokens = 1000)
            // With only 500 tokens used in a 10000-token window, we're well below threshold
            val result = ToolContextBudget.evaluate(
                contextWindowTokens = 10000,
                systemPromptTokens = 100,
                historyTokens = 300,
                transientToolResultTokens = 100,
                options = options,
                modelId = modelId,
                tokenCounter = tokenCounter,
            )
            assertFalse(result.contextFull)
            assertEquals(500, result.totalTokens)
        }

        @Test
        fun `evaluate provides truncation budget when context is full`() {
            val options = makeOptions(contextWindow = 1000, maxTokens = 100)
            // threshold = 1000 * 0.85 = 850. With 950 total, context is full.
            val result = ToolContextBudget.evaluate(
                contextWindowTokens = 1000,
                systemPromptTokens = 100,
                historyTokens = 800, // already over threshold alone
                transientToolResultTokens = 50,
                options = options,
                modelId = modelId,
                tokenCounter = tokenCounter,
            )
            assertTrue(result.contextFull)
            assertNotNull(result.truncateToTokens)
            assertTrue(result.truncateToTokens!! > 0)
        }

        @Test
        fun `evaluate provides truncation budget when approaching threshold`() {
            val options = makeOptions(contextWindow = 10000, maxTokens = 1000)
            // Even if not contextFull, if we're within buffer range, truncate
            val result = ToolContextBudget.evaluate(
                contextWindowTokens = 10000,
                systemPromptTokens = 5000,
                historyTokens = 3500,
                transientToolResultTokens = 200,
                options = options,
                modelId = modelId,
                tokenCounter = tokenCounter,
            )
            // Not contextFull but close enough to need truncation
            assertNotNull(result.truncateToTokens)
        }

        @Test
        fun `evaluate returns null truncation budget when plenty of room`() {
            val options = makeOptions(contextWindow = 100000, maxTokens = 1000)
            // With 100K context and only 500 tokens used, there's plenty of room
            val result = ToolContextBudget.evaluate(
                contextWindowTokens = 100000,
                systemPromptTokens = 100,
                historyTokens = 200,
                transientToolResultTokens = 50,
                options = options,
                modelId = modelId,
                tokenCounter = tokenCounter,
            )
            assertFalse(result.contextFull)
            assertNull(result.truncateToTokens)
        }

        @Test
        fun `evaluate distributes truncation budget across multiple results`() {
            val options = makeOptions(contextWindow = 1000, maxTokens = 100)
            val result = ToolContextBudget.evaluate(
                contextWindowTokens = 1000,
                systemPromptTokens = 100,
                historyTokens = 800,
                transientToolResultTokens = 50,
                options = options,
                modelId = modelId,
                tokenCounter = tokenCounter,
                toolResultCount = 3,
            )
            // Per-result budget should be availableTokens / 3
            assertNotNull(result.truncateToTokens)
            // The available tokens should be distributed across 3 results
            assertTrue(result.truncateToTokens!! > 0)
        }

        @Test
        fun `evaluate with zero context window and zero tokens returns not full`() {
            val options = makeOptions(contextWindow = 0, maxTokens = 0)
            val result = ToolContextBudget.evaluate(
                contextWindowTokens = 0,
                systemPromptTokens = 0,
                historyTokens = 0,
                transientToolResultTokens = 0,
                options = options,
                modelId = modelId,
                tokenCounter = tokenCounter,
            )
            // Zero tokens used and zero context window: threshold is 0, tokens is 0, so 0 > 0 is false
            assertFalse(result.contextFull)
            assertEquals(0, result.totalTokens)
        }

        @Test
        fun `evaluate minimum truncation budget is 100 tokens`() {
            val options = makeOptions(contextWindow = 500, maxTokens = 50)
            // Context is very full, leaving little room
            val result = ToolContextBudget.evaluate(
                contextWindowTokens = 500,
                systemPromptTokens = 50,
                historyTokens = 450, // well past 500 * 0.85 = 425 threshold
                transientToolResultTokens = 10,
                options = options,
                modelId = modelId,
                tokenCounter = tokenCounter,
                toolResultCount = 1,
            )
            assertTrue(result.contextFull)
            assertNotNull(result.truncateToTokens)
            // Minimum is 100 tokens per result
            assertTrue(result.truncateToTokens!! >= 100)
        }
    }

    // ---- isApiContextExceeded() tests (API model path) ----

    @Nested
    inner class IsApiContextExceeded {

        private val history = listOf(
            ChatMessage(Role.USER, "Hello, how are you today?"),
            ChatMessage(Role.ASSISTANT, "I am doing well, thank you for asking! How can I help you?"),
        )

        @Test
        fun `isApiContextExceeded returns true when tokens exceed threshold`() {
            val options = makeOptions(contextWindow = 50, maxTokens = 10)
            // With only 50 tokens of context, even small history exceeds threshold
            val result = ToolContextBudget.isApiContextExceeded(
                contextWindowTokens = 50,
                history = history,
                systemPrompt = "You are a helpful assistant.",
                currentPrompt = "Tell me about science.",
                toolResultPayloads = listOf("A very long tool result that contains lots of information"),
                options = options,
                modelId = modelId,
            )
            assertTrue(result)
        }

        @Test
        fun `isApiContextExceeded returns false when tokens within threshold`() {
            val options = makeOptions(contextWindow = 100000, maxTokens = 4096)
            // Large context window, small history — well within threshold
            val result = ToolContextBudget.isApiContextExceeded(
                contextWindowTokens = 100000,
                history = history,
                systemPrompt = "You are a helpful assistant.",
                currentPrompt = "Hi",
                toolResultPayloads = emptyList(),
                options = options,
                modelId = modelId,
            )
            assertFalse(result)
        }

        @Test
        fun `isApiContextExceeded accounts for tool result payloads`() {
            // Use a large context so the base history is well within threshold
            val largeOptions = makeOptions(contextWindow = 4096, maxTokens = 256)
            // Small history without tool results — within threshold
            val withoutResults = ToolContextBudget.isApiContextExceeded(
                contextWindowTokens = 4096,
                history = history,
                systemPrompt = null,
                currentPrompt = "Search",
                toolResultPayloads = emptyList(),
                options = largeOptions,
                modelId = modelId,
            )
            // Same context with many large tool results — should exceed threshold
            val largeResults = (1..200).map { "This is tool result number $it with lots of detailed content about various topics" }
            val withResults = ToolContextBudget.isApiContextExceeded(
                contextWindowTokens = 4096,
                history = history,
                systemPrompt = null,
                currentPrompt = "Search",
                toolResultPayloads = largeResults,
                options = largeOptions,
                modelId = modelId,
            )
            assertFalse(withoutResults)
            assertTrue(withResults)
        }
    }

    // ---- apiTruncationBudget() tests ----

    @Nested
    inner class ApiTruncationBudget {

        @Test
        fun `apiTruncationBudget returns null when context window is unknown`() {
            val options = makeOptions(contextWindow = 4096, maxTokens = 1024)
            val budget = ToolContextBudget.apiTruncationBudget(
                contextWindowTokens = null,
                history = emptyList(),
                systemPrompt = null,
                currentPrompt = "test",
                toolResultPayloads = emptyList(),
                toolResultCount = 1,
                options = options,
                modelId = modelId,
            )
            assertNull(budget)
        }

        @Test
        fun `apiTruncationBudget distributes across multiple results`() {
            val options = makeOptions(contextWindow = 4096, maxTokens = 1024)
            val budget = ToolContextBudget.apiTruncationBudget(
                contextWindowTokens = 4096,
                history = emptyList(),
                systemPrompt = "You are helpful.",
                currentPrompt = "Search",
                toolResultPayloads = emptyList(),
                toolResultCount = 3,
                options = options,
                modelId = modelId,
            )
            assertNotNull(budget)
            // Budget should be (usablePromptTokens - usedTokens) / 3
            assertTrue(budget!! > 0)
        }

        @Test
        fun `apiTruncationBudget returns limited tokens when context is nearly full`() {
            val options = makeOptions(contextWindow = 500, maxTokens = 100)
            val history = (1..20).map {
                ChatMessage(Role.USER, "This is message number $it with some content to fill up the context window significantly")
            }
            val budget = ToolContextBudget.apiTruncationBudget(
                contextWindowTokens = 500,
                history = history,
                systemPrompt = "You are a very helpful assistant.",
                currentPrompt = "Search",
                toolResultPayloads = emptyList(),
                toolResultCount = 1,
                options = options,
                modelId = modelId,
            )
            assertNotNull(budget)
            // Should still return at least 100 (minimum per-result)
            assertTrue(budget!! >= 100)
        }

        @Test
        fun `apiTruncationBudget minimum is 100 tokens per result`() {
            val options = makeOptions(contextWindow = 100, maxTokens = 50)
            val budget = ToolContextBudget.apiTruncationBudget(
                contextWindowTokens = 100,
                history = listOf(ChatMessage(Role.USER, "A".repeat(200))),
                systemPrompt = null,
                currentPrompt = "test",
                toolResultPayloads = emptyList(),
                toolResultCount = 1,
                options = options,
                modelId = modelId,
            )
            assertNotNull(budget)
            assertTrue(budget!! >= 100)
        }
    }

    // ---- localTruncationBudget() tests ----

    @Nested
    inner class LocalTruncationBudget {

        @Test
        fun `localTruncationBudget returns available tokens for tool results`() {
            // 1000 context - 100 system - 300 history - 50 tool results - 500 buffer = 150
            val budget = ToolContextBudget.localTruncationBudget(
                contextWindowTokens = 1000,
                systemPromptTokens = 100,
                historyTokens = 300,
                transientToolResultTokens = 50,
                bufferTokens = 500,
            )
            assertEquals(50, budget) // 1000 - 100 - 300 - 50 - 500 = 50
        }

        @Test
        fun `localTruncationBudget returns zero when context is full`() {
            val budget = ToolContextBudget.localTruncationBudget(
                contextWindowTokens = 1000,
                systemPromptTokens = 200,
                historyTokens = 800,
                transientToolResultTokens = 100,
                bufferTokens = 500,
            )
            // 1000 - 200 - 800 - 100 - 500 < 0, coerced to 0
            assertEquals(0, budget)
        }

        @Test
        fun `localTruncationBudget with default buffer`() {
            val budget = ToolContextBudget.localTruncationBudget(
                contextWindowTokens = 8000,
                systemPromptTokens = 100,
                historyTokens = 2000,
                transientToolResultTokens = 0,
            )
            // Uses default LOCAL_TOOL_RESULT_BUFFER_TOKENS
            assertTrue(budget > 0)
            assertTrue(budget < 8000)
        }

        @Test
        fun `localTruncationBudget handles exact fit`() {
            // Exactly zero available when used = context - buffer
            val budget = ToolContextBudget.localTruncationBudget(
                contextWindowTokens = 1000,
                systemPromptTokens = 100,
                historyTokens = 300,
                transientToolResultTokens = 100,
                bufferTokens = 500, // 1000 - 100 - 300 - 100 - 500 = 0
            )
            assertEquals(0, budget)
        }
    }

    // ---- isApiContextExceeded() edge case ----

    @Nested
    inner class EdgeCases {

        @Test
        fun `evaluate with zero tokens used is not context full`() {
            val options = makeOptions(contextWindow = 4096, maxTokens = 1024)
            val result = ToolContextBudget.evaluate(
                contextWindowTokens = 4096,
                systemPromptTokens = 0,
                historyTokens = 0,
                transientToolResultTokens = 0,
                options = options,
                modelId = modelId,
                tokenCounter = tokenCounter,
            )
            assertFalse(result.contextFull)
            assertEquals(0, result.totalTokens)
            assertNull(result.truncateToTokens)
        }

        @Test
        fun `countHistoryTokens returns correct sum`() {
            val history = listOf(
                ChatMessage(Role.USER, "Hello there"),
                ChatMessage(Role.ASSISTANT, "Hi, how can I help?"),
                ChatMessage(Role.USER, "Tell me about the solar system"),
            )
            val tokens = ToolContextBudget.countHistoryTokens(history, modelId, tokenCounter)
            assertTrue(tokens > 0, "Should have positive token count for non-empty history")

            // Verify it's approximately correct by checking individual messages
            val expectedTotal = history.sumOf { tokenCounter.countTokens(it.content, modelId) }
            assertEquals(expectedTotal, tokens)
        }

        @Test
        fun `countSystemPromptTokens returns correct count`() {
            val prompt = "You are a helpful AI assistant."
            val tokens = ToolContextBudget.countSystemPromptTokens(prompt, modelId, tokenCounter)
            assertEquals(tokenCounter.countTokens(prompt, modelId), tokens)
        }
    }
}