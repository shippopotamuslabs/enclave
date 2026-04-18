package com.browntowndev.pocketcrew.domain.util

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Tests for the token-based truncation logic in [NativeToolResultFormatter].
 *
 * These tests verify that when [modelId] is provided, [tokenCounter] is used for
 * precise truncation decisions instead of the fallback character-based heuristic.
 * Written to match EXPECTED behavior, not production code behavior.
 */
class NativeToolResultFormatterExtendedTest {

    /** Deterministic token counter: each character = 1 token. */
    private object OneCharOneToken : TokenCounter {
        override fun countTokens(text: String, modelName: String?): Int = text.length
    }

    /** Token counter that doubles the character count (simulates a dense tokenizer). */
    private object DenseTokenCounter : TokenCounter {
        override fun countTokens(text: String, modelName: String?): Int = text.length * 2
    }

    // ── Token-based shouldTruncate decisions ──────────────────────────────

    @Nested
    inner class TokenBasedTruncationDecision {

        @Test
        @DisplayName("When modelId is provided, shouldTruncate uses tokenCounter (under token budget -> not truncated)")
        fun tokenBasedUnderBudgetNotTruncated() {
            val shortContent = "A".repeat(25)
            val resultJson = """{"results":[{"content":"$shortContent"}]}"""

            val truncated = NativeToolResultFormatter.truncateToolResult(
                resultJson = resultJson,
                contextWindowTokens = 2000,
                estimatedUsedTokens = 1500,
                bufferTokens = 100,
                tokenCounter = DenseTokenCounter,
                modelId = "test-model",
            )

            val payload = Json.parseToJsonElement(truncated).jsonObject
            val content = payload["results"]!!.jsonArray[0].jsonObject["content"]!!.jsonPrimitive.content
            assertFalse(content.contains("truncated"), "Content under token budget should not be truncated")
        }

        @Test
        @DisplayName("When modelId is provided, shouldTruncate uses tokenCounter (over token budget -> truncated)")
        fun tokenBasedOverBudgetTruncated() {
            val longContent = "X".repeat(200)
            val resultJson = """{"results":[{"content":"$longContent"}]}"""

            val truncated = NativeToolResultFormatter.truncateToolResult(
                resultJson = resultJson,
                contextWindowTokens = 1000,
                estimatedUsedTokens = 900,
                bufferTokens = 50,
                tokenCounter = DenseTokenCounter,
                modelId = "test-model",
            )

            val payload = Json.parseToJsonElement(truncated).jsonObject
            val content = payload["results"]!!.jsonArray[0].jsonObject["content"]!!.jsonPrimitive.content
            assertTrue(
                content.contains("truncated for context"),
                "Content over token budget should be truncated when modelId provided"
            )
        }

        @Test
        @DisplayName("When modelId is null, falls back to character-based truncation (chars/4 heuristic)")
        fun nullModelIdFallsBackToCharHeuristic() {
            val shortContent = "Short content here"
            val resultJson = """{"results":[{"content":"$shortContent"}]}"""

            val truncated = NativeToolResultFormatter.truncateToolResult(
                resultJson = resultJson,
                contextWindowTokens = 1000,
                estimatedUsedTokens = 100,
                bufferTokens = 100,
                tokenCounter = OneCharOneToken,
                modelId = null,
            )

            val payload = Json.parseToJsonElement(truncated).jsonObject
            val content = payload["results"]!!.jsonArray[0].jsonObject["content"]!!.jsonPrimitive.content
            assertEquals(shortContent, content, "Short content should not be truncated with null modelId")
        }

        @Test
        @DisplayName("Token-based truncation catches content that char heuristic would miss (dense tokenizer)")
        fun tokenCatchesWhatCharHeuristicMisses() {
            // Dense content: 50 chars but 100 tokens with DenseTokenCounter
            // available = 1000 - 990 - 5 = 5 tokens / 1 result = 5 maxTokensPerResult
            // 100 tokens > 5 -> token-based says truncate
            // maxCharsPerResult = max(100, 5*4) = 100 -> 50 chars < 100 -> char heuristic would NOT truncate
            // With modelId = "test-model", token-based wins -> SHOULD truncate
            val denseContent = "X".repeat(50)
            val resultJson = """{"results":[{"content":"$denseContent"}]}"""

            val truncated = NativeToolResultFormatter.truncateToolResult(
                resultJson = resultJson,
                contextWindowTokens = 1000,
                estimatedUsedTokens = 990,
                bufferTokens = 5,
                tokenCounter = DenseTokenCounter,
                modelId = "test-model",
            )

            val payload = Json.parseToJsonElement(truncated).jsonObject
            val content = payload["results"]!!.jsonArray[0].jsonObject["content"]!!.jsonPrimitive.content
            assertTrue(
                content.contains("truncated for context"),
                "Dense content should be truncated by token count even if char count is small"
            )
        }

        @Test
        @DisplayName("Short content with few tokens should NOT be truncated when modelId is provided")
        fun shortContentWithFewTokensNotTruncated() {
            val shortContent = "Hello world"
            val resultJson = """{"results":[{"content":"$shortContent"}]}"""

            val truncated = NativeToolResultFormatter.truncateToolResult(
                resultJson = resultJson,
                contextWindowTokens = 4096,
                estimatedUsedTokens = 100,
                bufferTokens = 100,
                tokenCounter = JTokkitTokenCounter,
                modelId = "gpt-4",
            )

            assertEquals(resultJson, truncated, "Short content should be preserved unchanged")
        }

        @Test
        @DisplayName("Token-based truncation with real JTokkit tokenizer on long content")
        fun truncatesAccuratelyWithRealTokenizer() {
            val longText = "The quick brown fox jumps over the lazy dog. ".repeat(50)
            val resultJson = """{"results":[{"content":"$longText"}]}"""

            val truncated = NativeToolResultFormatter.truncateToolResult(
                resultJson = resultJson,
                contextWindowTokens = 2048,
                estimatedUsedTokens = 1800,
                bufferTokens = 100,
                tokenCounter = JTokkitTokenCounter,
                modelId = "gpt-4",
            )

            val payload = Json.parseToJsonElement(truncated).jsonObject
            val content = payload["results"]!!.jsonArray[0].jsonObject["content"]!!.jsonPrimitive.content
            assertTrue(
                content.contains("truncated for context"),
                "Long text should be truncated with real tokenizer"
            )
        }
    }

    // ── Error and edge cases ──────────────────────────────────────────────

    @Nested
    inner class EdgeCases {

        @Test
        @DisplayName("truncateToolResult returns error when no tokens available")
        fun returnsErrorWhenNoTokensAvailable() {
            val resultJson = """{"results":[{"content":"any content"}]}"""

            val truncated = NativeToolResultFormatter.truncateToolResult(
                resultJson = resultJson,
                contextWindowTokens = 1000,
                estimatedUsedTokens = 950,
                bufferTokens = 100,
                tokenCounter = OneCharOneToken,
                modelId = "test-model",
            )

            assertEquals(
                """{"error": "cannot read page, context window too full"}""",
                truncated,
                "Should return error when available tokens <= 0"
            )
        }

        @Test
        @DisplayName("truncateToolResult passes through invalid JSON unchanged")
        fun passesThroughInvalidJson() {
            val invalidJson = "this is not json at all"

            val truncated = NativeToolResultFormatter.truncateToolResult(
                resultJson = invalidJson,
                contextWindowTokens = 4096,
                estimatedUsedTokens = 100,
                bufferTokens = 100,
                tokenCounter = OneCharOneToken,
                modelId = "test-model",
            )

            assertEquals(invalidJson, truncated, "Invalid JSON should pass through unchanged")
        }

        @Test
        @DisplayName("truncateToolResult preserves non-content fields when truncating")
        fun preservesNonContentFields() {
            val urlContent = "A".repeat(500)
            val resultJson = """{
                "results": [{
                    "title": "Important Title",
                    "url": "https://example.com/page",
                    "content": "$urlContent",
                    "score": 0.95
                }]
            }"""

            val truncated = NativeToolResultFormatter.truncateToolResult(
                resultJson = resultJson,
                contextWindowTokens = 1000,
                estimatedUsedTokens = 800,
                bufferTokens = 50,
                tokenCounter = OneCharOneToken,
                modelId = "test-model",
            )

            val payload = Json.parseToJsonElement(truncated).jsonObject
            val item = payload["results"]!!.jsonArray[0].jsonObject
            assertEquals("Important Title", item["title"]!!.jsonPrimitive.content)
            assertEquals("https://example.com/page", item["url"]!!.jsonPrimitive.content)
            assertEquals("0.95", item["score"]!!.jsonPrimitive.content)
            assertTrue(item["content"]!!.jsonPrimitive.content.contains("truncated for context"))
        }

        @Test
        @DisplayName("truncateToolResult handles empty results array")
        fun handlesEmptyResultsArray() {
            val resultJson = """{"results": []}"""

            val truncated = NativeToolResultFormatter.truncateToolResult(
                resultJson = resultJson,
                contextWindowTokens = 4096,
                estimatedUsedTokens = 100,
                bufferTokens = 100,
                tokenCounter = OneCharOneToken,
                modelId = "test-model",
            )

            assertEquals(resultJson, truncated, "Empty results array should pass through unchanged")
        }

        @Test
        @DisplayName("truncateToolResult handles raw_content field")
        fun handlesRawContentField() {
            val rawContent = "B".repeat(800)
            val resultJson = """{"results": [{"raw_content": "$rawContent"}]}"""

            val truncated = NativeToolResultFormatter.truncateToolResult(
                resultJson = resultJson,
                contextWindowTokens = 500,
                estimatedUsedTokens = 400,
                bufferTokens = 50,
                tokenCounter = OneCharOneToken,
                modelId = "test-model",
            )

            val payload = Json.parseToJsonElement(truncated).jsonObject
            val raw = payload["results"]!!.jsonArray[0].jsonObject["raw_content"]!!.jsonPrimitive.content
            assertTrue(raw.contains("truncated for context"), "raw_content should be truncated")
        }

        @Test
        @DisplayName("truncateToolResult truncates both content and raw_content in the same result")
        fun truncatesBothContentAndRawContent() {
            val longContent = "X".repeat(5000)
            val resultJson = """{"results":[{"content":"$longContent","raw_content":"$longContent"}]}"""

            val truncated = NativeToolResultFormatter.truncateToolResult(
                resultJson = resultJson,
                contextWindowTokens = 1000,
                estimatedUsedTokens = 100,
                bufferTokens = 100,
                tokenCounter = OneCharOneToken,
                modelId = "test-model",
            )

            val payload = Json.parseToJsonElement(truncated).jsonObject
            val item = payload["results"]!!.jsonArray[0].jsonObject
            assertTrue(item["content"]!!.jsonPrimitive.content.contains("truncated for context"))
            assertTrue(item["raw_content"]!!.jsonPrimitive.content.contains("truncated for context"))
        }

        @Test
        @DisplayName("truncateToolResult with LOCAL_TOOL_RESULT_BUFFER_TOKENS constant")
        fun usesLocalToolResultBuffer() {
            val longContent = "Z".repeat(8000)
            val resultJson = """{"results":[{"content":"$longContent"}]}"""

            val truncated = NativeToolResultFormatter.truncateToolResult(
                resultJson = resultJson,
                contextWindowTokens = 4096,
                estimatedUsedTokens = 2000,
                bufferTokens = ContextWindowPlanner.LOCAL_TOOL_RESULT_BUFFER_TOKENS,
                tokenCounter = JTokkitTokenCounter,
                modelId = "local-model",
            )

            val payload = Json.parseToJsonElement(truncated).jsonObject
            val content = payload["results"]!!.jsonArray[0].jsonObject["content"]!!.jsonPrimitive.content
            assertTrue(content.length < longContent.length || content.contains("truncated"))
        }
    }

    // ── Budget distribution across multiple results ─────────────────────────

    @Nested
    inner class BudgetDistribution {

        @Test
        @DisplayName("Token budget is divided equally across multiple results")
        fun budgetDividedAcrossResults() {
            val content = "Z".repeat(500)
            val resultJson = """{
                "results": [
                    {"content": "$content"},
                    {"content": "$content"},
                    {"content": "$content"}
                ]
            }"""

            val truncated = NativeToolResultFormatter.truncateToolResult(
                resultJson = resultJson,
                contextWindowTokens = 1000,
                estimatedUsedTokens = 800,
                bufferTokens = 50,
                tokenCounter = DenseTokenCounter,
                modelId = "test-model",
            )

            val payload = Json.parseToJsonElement(truncated).jsonObject
            val results = payload["results"]!!.jsonArray
            assertEquals(3, results.size)
            results.forEach { item ->
                val c = item.jsonObject["content"]!!.jsonPrimitive.content
                assertTrue(c.contains("truncated for context"), "Each result should be truncated")
            }
        }

        @Test
        @DisplayName("Results within token budget are not truncated even when distributed")
        fun resultsWithinBudgetNotTruncated() {
            val shortContent = "OK"
            val resultJson = """{
                "results": [
                    {"content": "$shortContent"},
                    {"content": "$shortContent"}
                ]
            }"""

            val truncated = NativeToolResultFormatter.truncateToolResult(
                resultJson = resultJson,
                contextWindowTokens = 4096,
                estimatedUsedTokens = 100,
                bufferTokens = 100,
                tokenCounter = OneCharOneToken,
                modelId = "test-model",
            )

            val payload = Json.parseToJsonElement(truncated).jsonObject
            val results = payload["results"]!!.jsonArray
            results.forEach { item ->
                val c = item.jsonObject["content"]!!.jsonPrimitive.content
                assertEquals("OK", c, "Short results should not be truncated")
            }
        }
    }

    // ── truncateForApiContext tests ─────────────────────────────────────────

    @Nested
    inner class TruncateForApiContext {

        @Test
        @DisplayName("truncateForApiContext returns original string when within token budget")
        fun returnsOriginalWhenWithinBudget() {
            val shortResult = """{"query": "test", "answer": "hello"}"""

            val result = NativeToolResultFormatter.truncateForApiContext(
                resultJson = shortResult,
                availableTokens = 1000,
                tokenCounter = OneCharOneToken,
                modelId = "test-model",
            )

            assertEquals(shortResult, result, "Short result should not be truncated")
        }

        @Test
        @DisplayName("truncateForApiContext returns error JSON when availableTokens <= 0")
        fun returnsErrorWhenNoTokensAvailable() {
            val result = NativeToolResultFormatter.truncateForApiContext(
                resultJson = """{"data": "irrelevant"}""",
                availableTokens = 0,
                tokenCounter = OneCharOneToken,
                modelId = "test-model",
            )

            assertEquals(
                """{"error": "cannot read page, context window too full"}""",
                result,
                "Should return error JSON when availableTokens is 0",
            )
        }

        @Test
        @DisplayName("truncateForApiContext returns error JSON when availableTokens is negative")
        fun returnsErrorWhenTokensNegative() {
            val result = NativeToolResultFormatter.truncateForApiContext(
                resultJson = """{"data": "irrelevant"}""",
                availableTokens = -50,
                tokenCounter = OneCharOneToken,
                modelId = "test-model",
            )

            assertEquals(
                """{"error": "cannot read page, context window too full"}""",
                result,
                "Should return error JSON when availableTokens is negative",
            )
        }

        @Test
        @DisplayName("truncateForApiContext truncates long string when over token budget")
        fun truncatesWhenOverBudget() {
            val longResult = "A".repeat(5000)

            val result = NativeToolResultFormatter.truncateForApiContext(
                resultJson = longResult,
                availableTokens = 100, // 100 tokens * 4 chars/token = 400 chars max
                tokenCounter = OneCharOneToken,
                modelId = "test-model",
            )

            assertTrue(
                result.contains("truncated for context"),
                "Over-budget result should be truncated",
            )
            assertTrue(
                result.length < longResult.length,
                "Truncated result should be shorter than original",
            )
        }

        @Test
        @DisplayName("truncateForApiContext uses tokenCounter to determine if truncation is needed")
        fun usesTokenCounterForDecision() {
            // 50 chars = 100 tokens with DenseTokenCounter, but availableTokens = 20
            val content = "X".repeat(50)

            val result = NativeToolResultFormatter.truncateForApiContext(
                resultJson = content,
                availableTokens = 20, // 20 tokens available, but content = 100 tokens
                tokenCounter = DenseTokenCounter,
                modelId = "test-model",
            )

            assertTrue(
                result.contains("truncated for context"),
                "Dense content (100 tokens) should be truncated when only 20 tokens available",
            )
        }

        @Test
        @DisplayName("truncateForApiContext preserves JSON structure for Tavily results")
        fun preservesJsonStructure() {
            val tavilyResult = """{"query": "test query", "results": [{"title": "Test", "url": "https://example.com", "content": "Some content"}]}"""

            val result = NativeToolResultFormatter.truncateForApiContext(
                resultJson = tavilyResult,
                availableTokens = 1000, // plenty of room
                tokenCounter = OneCharOneToken,
                modelId = "test-model",
            )

            assertEquals(tavilyResult, result, "JSON within budget should be preserved exactly")
        }

        @Test
        @DisplayName("truncateForApiContext uses char-based truncation when modelId is null")
        fun usesCharHeuristicWhenModelIdNull() {
            // With null modelId, the token counter still counts tokens for the
            // decision, but char-based heuristics are used for the max length.
            // 50 chars = 50 tokens with OneCharOneToken, availableTokens = 20 -> truncate
            val content = "X".repeat(50)

            val result = NativeToolResultFormatter.truncateForApiContext(
                resultJson = content,
                availableTokens = 20,
                tokenCounter = OneCharOneToken,
                modelId = null,
            )

            assertTrue(
                result.contains("truncated for context"),
                "Content over token budget should be truncated even with null modelId",
            )
        }

        @Test
        @DisplayName("truncateForApiContext truncation marker is appended correctly")
        fun truncationMarkerAppended() {
            val longResult = "B".repeat(8000)

            val result = NativeToolResultFormatter.truncateForApiContext(
                resultJson = longResult,
                availableTokens = 100,
                tokenCounter = OneCharOneToken,
                modelId = "gpt-4",
            )

            assertTrue(
                result.endsWith("... (truncated for context)"),
                "Truncated result should end with truncation marker",
            )
        }

        @Test
        @DisplayName("truncateForApiContext with availableTokens exactly matching content tokens is NOT truncated")
        fun exactMatchNotTruncated() {
            // 50 chars = 50 tokens with OneCharOneToken, availableTokens = 50
            val content = "X".repeat(50)

            val result = NativeToolResultFormatter.truncateForApiContext(
                resultJson = content,
                availableTokens = 50,
                tokenCounter = OneCharOneToken,
                modelId = "test-model",
            )

            assertEquals(content, result, "Content exactly at token budget should not be truncated")
        }

        @Test
        @DisplayName("truncateForApiContext truncates at minimum 100 chars when token budget is very small")
        fun minimumTruncationLength() {
            // availableTokens = 1 -> maxChars = max(100, 1*4) = 100
            val longResult = "C".repeat(9000)

            val result = NativeToolResultFormatter.truncateForApiContext(
                resultJson = longResult,
                availableTokens = 1,
                tokenCounter = OneCharOneToken,
                modelId = "test-model",
            )

            assertTrue(
                result.contains("truncated for context"),
                "Very small budget should still truncate with marker",
            )
            // The truncated portion should be at least 100 chars + the marker
            assertTrue(
                result.length < longResult.length,
                "Truncated result should be shorter than original",
            )
        }
    }
}