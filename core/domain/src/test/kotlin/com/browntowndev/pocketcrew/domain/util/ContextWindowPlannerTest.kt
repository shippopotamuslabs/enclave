package com.browntowndev.pocketcrew.domain.util

import com.browntowndev.pocketcrew.domain.model.chat.ChatMessage
import com.browntowndev.pocketcrew.domain.model.chat.Role
import com.browntowndev.pocketcrew.domain.model.inference.GenerationOptions
import com.browntowndev.pocketcrew.domain.model.inference.ToolDefinition
import com.browntowndev.pocketcrew.domain.model.inference.TavilyWebSearchParams
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Unit tests for [ContextWindowPlanner] — the central budget computation engine
 * used by all inference services (API + local) for context window management.
 *
 * Tests use [FakeTokenCounter] for deterministic, predictable results
 * and [JTokkitTokenCounter] for real-world accuracy verification.
 */
class ContextWindowPlannerTest {

    // ── Fakes ──────────────────────────────────────────────────────────────

    /** Deterministic token counter: 1 token per character, for exact boundary testing. */
    private object FakeTokenCounter : TokenCounter {
        override fun countTokens(text: String, modelName: String?): Int = text.length
    }

    private fun makeTool(name: String, desc: String) = ToolDefinition(
        name = name,
        description = desc,
        parametersClass = TavilyWebSearchParams::class,
    )

    // ── budgetFor ──────────────────────────────────────────────────────────

    @Nested
    inner class BudgetFor {

        @Test
        @DisplayName("budgetFor reserves output, reasoning, tool schema, media, and safety buffer")
        fun budgetCalculatesReservedTokensCorrectly() {
            val options = GenerationOptions(
                reasoningBudget = 512,
                toolingEnabled = true,
                availableTools = listOf(makeTool("search", "web search")),
                imageUris = listOf("file:///1.jpg", "file:///2.jpg"),
            )
            val budget = ContextWindowPlanner.budgetFor(
                contextWindowTokens = 8192,
                options = options,
                tokenCounter = FakeTokenCounter,
            )

            // outputReserve = min(1024 default, 8192/4=2048) = 1024
            // reasoningReserve = 512
            // toolSchemaTokens = tokens(tool schema JSON for TavilyWebSearchParams)
            // mediaReserve = 2 * 1024 = 2048
            // safetyBuffer = 256 (default)
            // We don't assert an exact value for reservedTokens since the
            // TavilyWebSearchParams schema size may change, just that it's > 0
            assertTrue(budget.reservedTokens > 0)
            assertTrue(budget.reservedTokens > 1024 + 512 + 2048 + 256) // must include schema
        }

        @Test
        @DisplayName("budgetFor clamps outputReserve between 1 and contextWindow/2")
        fun outputReserveIsClamped() {
            val smallWindow = GenerationOptions(reasoningBudget = 0, maxTokens = 200)
            val budget = ContextWindowPlanner.budgetFor(
                contextWindowTokens = 100,
                options = smallWindow,
                tokenCounter = FakeTokenCounter,
            )
            // maxTokens=200 clamped to min(200, 100/2=50) = 50
            assertEquals(50, budget.outputReserveTokens)
        }

        @Test
        @DisplayName("budgetFor defaults outputReserve to min(of 1024, window/4) when maxTokens is null")
        fun outputReserveDefaultWhenMaxTokensNull() {
            val budget = ContextWindowPlanner.budgetFor(
                contextWindowTokens = 4000,
                options = GenerationOptions(reasoningBudget = 0),
                tokenCounter = FakeTokenCounter,
            )
            // default = min(1024, 4000/4=1000) = 1000
            assertEquals(1000, budget.outputReserveTokens)
        }

        @Test
        @DisplayName("budgetFor thresholdTokens is contextWindow * thresholdRatio")
        fun thresholdTokensCalculatedCorrectly() {
            val budget = ContextWindowPlanner.budgetFor(
                contextWindowTokens = 10000,
                options = GenerationOptions(reasoningBudget = 0),
                thresholdRatio = 0.75,
                tokenCounter = FakeTokenCounter,
            )
            assertEquals(7500, budget.thresholdTokens)
        }

        @Test
        @DisplayName("budgetFor usablePromptTokens = contextWindow - reservedTokens, never negative")
        fun usablePromptTokensNeverNegative() {
            val budget = ContextWindowPlanner.budgetFor(
                contextWindowTokens = 100,
                options = GenerationOptions(reasoningBudget = 0, maxTokens = 300),
                tokenCounter = FakeTokenCounter,
            )
            // With maxTokens=300 but window only 100, outputReserve=50, totalReserved > 100
            // usablePromptTokens should be 0, not negative
            assertEquals(0, budget.usablePromptTokens)
        }
    }

    // ── estimatePromptTokens ──────────────────────────────────────────────

    @Nested
    inner class EstimatePromptTokens {

        @Test
        @DisplayName("estimatePromptTokens counts system, history, prompt, and tool result tokens")
        fun countsAllTokenSources() {
            val systemPrompt = "You are an assistant." // 22 chars → 22 tokens with FakeTokenCounter
            val history = listOf(
                ChatMessage(Role.USER, "Hello"),         // 5 tokens
                ChatMessage(Role.ASSISTANT, "Hi there!"), // 9 tokens
            )
            val currentPrompt = "What is the weather?" // 19 tokens
            val toolResults = listOf("Result 1", "Longer result 2") // 8 + 15 = 23 tokens, +2*30 overhead = 83

            val total = ContextWindowPlanner.estimatePromptTokens(
                history = history,
                systemPrompt = systemPrompt,
                currentPrompt = currentPrompt,
                toolResultPayloads = toolResults,
                tokenCounter = FakeTokenCounter,
            )

            assertEquals(22 + 5 + 9 + 19 + 8 + 30 + 15 + 30, total)
        }

        @Test
        @DisplayName("estimatePromptTokens handles empty history, no system prompt, and no tool results")
        fun handlesEmptyInputs() {
            val total = ContextWindowPlanner.estimatePromptTokens(
                history = emptyList(),
                systemPrompt = null,
                currentPrompt = "Hi",
                toolResultPayloads = emptyList(),
                tokenCounter = FakeTokenCounter,
            )
            assertEquals(2, total)
        }

        @Test
        @DisplayName("estimatePromptTokens adds TOOL_CALL_OVERHEAD_TOKENS per tool result")
        fun toolCallOverheadAdded() {
            val singleResult = ContextWindowPlanner.estimatePromptTokens(
                history = emptyList(),
                systemPrompt = null,
                currentPrompt = "",
                toolResultPayloads = listOf("abc"), // 3 tokens + 30 overhead
                tokenCounter = FakeTokenCounter,
            )
            assertEquals(3 + ContextWindowPlanner.TOOL_CALL_OVERHEAD_TOKENS, singleResult)

            val twoResults = ContextWindowPlanner.estimatePromptTokens(
                history = emptyList(),
                systemPrompt = null,
                currentPrompt = "",
                toolResultPayloads = listOf("a", "b"), // 1+30 + 1+30
                tokenCounter = FakeTokenCounter,
            )
            assertEquals(2 + 2 * ContextWindowPlanner.TOOL_CALL_OVERHEAD_TOKENS, twoResults)
        }
    }

    // ── shouldCompact ─────────────────────────────────────────────────────

    @Nested
    inner class ShouldCompact {

        @Test
        @DisplayName("shouldCompact returns true when estimated tokens exceed threshold")
        fun returnsTrueWhenOverThreshold() {
            val budget = ContextWindowPlanner.budgetFor(
                contextWindowTokens = 10000,
                options = GenerationOptions(reasoningBudget = 0),
                thresholdRatio = 0.75,
                tokenCounter = FakeTokenCounter,
            )
            assertTrue(ContextWindowPlanner.shouldCompact(7501, budget))
        }

        @Test
        @DisplayName("shouldCompact returns false when estimated tokens are within threshold")
        fun returnsFalseWhenWithinThreshold() {
            val budget = ContextWindowPlanner.budgetFor(
                contextWindowTokens = 10000,
                options = GenerationOptions(reasoningBudget = 0),
                thresholdRatio = 0.75,
                tokenCounter = FakeTokenCounter,
            )
            assertFalse(ContextWindowPlanner.shouldCompact(7500, budget))
        }

        @Test
        @DisplayName("shouldCompact returns false when estimated tokens are within usablePromptTokens but above threshold")
        fun usesMinimumOfThresholdAndUsable() {
            // When usablePromptTokens < thresholdTokens, shouldCompact uses the lower bound
            val budget = ContextWindowPlanner.budgetFor(
                contextWindowTokens = 100,
                options = GenerationOptions(reasoningBudget = 0, maxTokens = 300),
                tokenCounter = FakeTokenCounter,
            )
            // usablePromptTokens = 0 because reservedTokens > contextWindow
            assertFalse(ContextWindowPlanner.shouldCompact(0, budget))
            assertTrue(ContextWindowPlanner.shouldCompact(1, budget))
        }
    }

    // ── outputReserveFor ─────────────────────────────────────────────────

    @Nested
    inner class OutputReserveFor {

        @Test
        @DisplayName("outputReserveFor returns min of fallback when maxTokens is null")
        fun defaultFallbackWhenMaxTokensNull() {
            // default = min(1024, window/4), but at least 1
            assertEquals(1024, ContextWindowPlanner.outputReserveFor(8192, null))
            assertEquals(500, ContextWindowPlanner.outputReserveFor(2000, null))
            assertEquals(25, ContextWindowPlanner.outputReserveFor(100, null)) // 100/4=25
        }

        @Test
        @DisplayName("outputReserveFor is clamped to at most contextWindow/2")
        fun clampedToHalfWindow() {
            // 6000 > 8192/2 = 4096, so it's clamped
            assertEquals(4096, ContextWindowPlanner.outputReserveFor(8192, 6000))
        }

        @Test
        @DisplayName("outputReserveFor with very small window uses window/4 as fallback")
        fun smallWindowFallback() {
            // window=16 → fallback = min(1024, 16/4=4) = 4
            assertEquals(4, ContextWindowPlanner.outputReserveFor(16, null))
        }
    }

    // ── End-to-end scenarios ──────────────────────────────────────────────

    @Nested
    inner class EndToEndScenarios {

        @Test
        @DisplayName("Typical 4K local model: long history triggers compaction")
        fun localModel4KTriggersCompaction() {
            val tokenCounter = JTokkitTokenCounter
            val options = GenerationOptions(
                reasoningBudget = 0,
                contextWindow = 4096,
                availableTools = listOf(ToolDefinition.TAVILY_WEB_SEARCH),
            )
            val budget = ContextWindowPlanner.budgetFor(
                contextWindowTokens = 4096,
                options = options,
                modelId = "local-model",
                tokenCounter = tokenCounter,
            )

            // Build history that exceeds 75% of 4096 = 3072 tokens
            // Each pair is ~15-20 tokens with JTokkitTokenCounter, so we need ~200 pairs
            val longHistory = (1..200).flatMap {
                listOf(
                    ChatMessage(Role.USER, "Tell me about topic number $it in detail please"),
                    ChatMessage(Role.ASSISTANT, "Topic number $it is about many interesting things in the world of knowledge and science."),
                )
            }
            val estimatedTokens = ContextWindowPlanner.estimatePromptTokens(
                history = longHistory,
                systemPrompt = "You are a helpful assistant.",
                currentPrompt = "What else?",
                tokenCounter = tokenCounter,
                modelId = "local-model",
            )

            assertTrue(
                ContextWindowPlanner.shouldCompact(estimatedTokens, budget),
                "Long history should trigger compaction for 4K context window"
            )
        }

        @Test
        @DisplayName("Typical 128K API model: short history does not trigger compaction")
        fun apiModel128KNoCompaction() {
            val tokenCounter = JTokkitTokenCounter
            val options = GenerationOptions(reasoningBudget = 0)
            val budget = ContextWindowPlanner.budgetFor(
                contextWindowTokens = 128000,
                options = options,
                tokenCounter = tokenCounter,
            )

            val shortHistory = listOf(
                ChatMessage(Role.USER, "Hello"),
                ChatMessage(Role.ASSISTANT, "Hi! How can I help?"),
            )

            val estimatedTokens = ContextWindowPlanner.estimatePromptTokens(
                history = shortHistory,
                systemPrompt = "You are a helpful assistant.",
                currentPrompt = "What's the weather?",
                tokenCounter = tokenCounter,
            )

            assertFalse(
                ContextWindowPlanner.shouldCompact(estimatedTokens, budget),
                "Short history should not trigger compaction for 128K context window"
            )
        }

        @Test
        @DisplayName("TOOL_RESULT_THRESHOLD_RATIO (0.85) is a looser threshold than DEFAULT (0.75)")
        fun toolResultThresholdIsLooserThanDefault() {
            assertTrue(
                ContextWindowPlanner.TOOL_RESULT_THRESHOLD_RATIO > ContextWindowPlanner.DEFAULT_THRESHOLD_RATIO,
                "Tool result threshold should be looser than default compaction threshold"
            )
            assertEquals(0.85, ContextWindowPlanner.TOOL_RESULT_THRESHOLD_RATIO, 0.001)
        }
    }
}