package com.browntowndev.pocketcrew.domain.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class JTokkitTokenCounterTest {

    @Nested
    inner class BasicAccuracy {

        @Test
        @DisplayName("countTokens returns 0 for empty string")
        fun returnsZeroForEmptyString() {
            assertEquals(0, JTokkitTokenCounter.countTokens("", null))
        }

        @Test
        @DisplayName("countTokens returns positive count for non-empty string")
        fun returnsPositiveForNonEmpty() {
            val count = JTokkitTokenCounter.countTokens("Hello, world!", null)
            assertTrue(count > 0, "Token count should be positive for non-empty text")
        }

        @Test
        @DisplayName("countTokens is deterministic for the same input")
        fun isDeterministic() {
            val text = "The quick brown fox jumps over the lazy dog."
            val count1 = JTokkitTokenCounter.countTokens(text, null)
            val count2 = JTokkitTokenCounter.countTokens(text, null)
            assertEquals(count1, count2, "Same text should always produce the same token count")
        }

        @Test
        @DisplayName("countTokens uses cl100k_base as fallback when modelId is null")
        fun usesCl100kFallbackWhenNoModelId() {
            val text = "Hello world"
            val nullModel = JTokkitTokenCounter.countTokens(text, null)
            val unknownModel = JTokkitTokenCounter.countTokens(text, "unknown-model-xyz")
            assertEquals(nullModel, unknownModel,
                "Unknown model should fall back to cl100k_base, same as null modelId"
            )
        }

        @Test
        @DisplayName("countTokens accounts for typical English text at roughly 4 chars per token")
        fun typicalEnglishTextRatio() {
            // This tests the BPE approximation used in the codebase's *4 heuristic
            val text = "The quick brown fox jumps over the lazy dog. This is a typical English sentence."
            val tokenCount = JTokkitTokenCounter.countTokens(text, null)
            val charsPerToken = text.length.toDouble() / tokenCount
            // BPE for English typically gives 3-5 chars/token
            assertTrue(charsPerToken in 2.0..6.0,
                "Expected charsPerToken between 2 and 6, got $charsPerToken ($text.length chars / $tokenCount tokens)"
            )
        }

        @Test
        @DisplayName("countTokens handles unicode text")
        fun handlesUnicodeText() {
            val emojiText = "Hello 🎉🎊🎈 world"
            val count = JTokkitTokenCounter.countTokens(emojiText, null)
            assertTrue(count > 0, "Emoji text should have a positive token count")
        }

        @Test
        @DisplayName("countTokens is monotonically increasing for longer text")
        fun monotonicallyIncreasing() {
            val short = "Hello"
            val medium = "Hello, how are you doing today?"
            val long = "Hello, how are you doing today? I hope you're having a wonderful time!"

            val shortCount = JTokkitTokenCounter.countTokens(short, null)
            val mediumCount = JTokkitTokenCounter.countTokens(medium, null)
            val longCount = JTokkitTokenCounter.countTokens(long, null)

            assertTrue(shortCount < mediumCount, "Medium text should have more tokens than short")
            assertTrue(mediumCount < longCount, "Long text should have more tokens than medium")
        }

        @Test
        @DisplayName("countTokens with known GPT-4 model name returns consistent result")
        fun modelSpecificTokenCount() {
            val text = "This is a test."
            val gpt4Count = JTokkitTokenCounter.countTokens(text, "gpt-4")
            val defaultCount = JTokkitTokenCounter.countTokens(text, null)
            // GPT-4 uses cl100k_base, so counts should be identical
            assertEquals(gpt4Count, defaultCount,
                "GPT-4 model should use same encoding as default fallback"
            )
        }
    }
}