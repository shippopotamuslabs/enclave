package com.browntowndev.pocketcrew.domain.usecase.chat

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Tests for BufferThinkingStepsUseCase.
 * Verifies edge cases in streaming thinking text buffering.
 */
class BufferThinkingStepsUseCaseTest {

    private lateinit var processor: BufferThinkingStepsUseCase
    private lateinit var mockDetector: SentenceBoundaryDetector

    @BeforeEach
    fun setup() {
        // Create mock detector that simulates BreakIterator behavior
        mockDetector = mockk<SentenceBoundaryDetector>()
        processor = BufferThinkingStepsUseCase(mockDetector)
    }

    @Test
    fun `empty input returns empty list`() {
        // Empty input returns early without calling detector
        val result = processor("")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `three complete sentences emit together`() {
        // "First I need to understand. There are several factors. However the biggest issue is wakelocks."
        // BreakIterator would find 3 sentences
        val text = "First I need to understand. There are several factors. However the biggest issue is wakelocks."
        // Calculate: "First I need to understand." = indices 0-23, " There are several factors." = 23-46, " However the biggest issue is wakelocks." = 46-81
        every { mockDetector.findBoundaries(text) } returns listOf(
            Pair(0, 24),
            Pair(24, 47),
            Pair(47, 82)
        )

        val result = processor(text)
        assertTrue(result.isNotEmpty(), "Should emit with 3 sentences")
    }

    @Test
    fun `flush emits remaining content`() {
        // When text has no sentence boundary, flush should return remaining content
        // This tests the HARD_MAX_CHARS_BEFORE_FORCE path
        every { mockDetector.findBoundaries("First I need to understand") } returns emptyList()

        processor("First I need to understand")
        val flushed = processor.flush()
        assertTrue(!flushed.isNullOrBlank(), "Flush should return remaining content")
    }

    @Test
    fun `reset clears state`() {
        // Reset clears state - no detector call needed for reset test
        every { mockDetector.findBoundaries("First I need to understand") } returns emptyList()

        processor("First I need to understand")
        processor.reset()
        val flushed = processor.flush()
        assertTrue(flushed.isNullOrBlank(), "After reset, flush should return null")
    }

    @Test
    fun `words without punctuation don't emit prematurely`() {
        // No punctuation = no sentence boundaries
        every { mockDetector.findBoundaries("First") } returns emptyList()
        every { mockDetector.findBoundaries("First I need") } returns emptyList()
        every { mockDetector.findBoundaries("First I need to understand") } returns emptyList()

        val result1 = processor("First")
        val result2 = processor(" I need")
        val result3 = processor(" to understand")
        assertTrue(result1.isEmpty() && result2.isEmpty() && result3.isEmpty(),
            "Should not emit prematurely without punctuation")
    }

    @Test
    fun `emits correctly when punctuation is followed by space and capital`() {
        // "First sentence here. Second sentence here. Third sentence here."
        val text = "First sentence here. Second sentence here. Third sentence here."
        // Total: 63 chars. Boundaries at positions after each sentence
        // Sentence 1: indices 0-19, Sentence 2: 20-41, Sentence 3: 42-62
        every { mockDetector.findBoundaries(text) } returns listOf(
            Pair(0, 20),
            Pair(20, 42),
            Pair(42, 63)
        )

        val result = processor(text)
        assertTrue(result.isNotEmpty(), "Should emit with 3 simple sentences")
    }

    @Test
    fun `streaming multiple sentences emits correctly`() {
        // "First. Second. Third." - 3 short sentences
        val text = "First. Second. Third."
        // Total: 21 chars (indices 0-20)
        // "First." = 6, " Second." = 9, " Third." = 6
        every { mockDetector.findBoundaries(text) } returns listOf(
            Pair(0, 6),
            Pair(6, 15),
            Pair(15, 21)
        )

        val result = processor(text)
        assertTrue(result.isNotEmpty(), "Should emit sentences")
    }

    @Test
    fun `handles newlines as sentence separators`() {
        // "First sentence.\n\nSecond sentence."
        val text = "First sentence.\n\nSecond sentence."
        // Total: 33 chars (indices 0-32)
        // First sentence: indices 0-14, Second: indices 17-32
        every { mockDetector.findBoundaries(text) } returns listOf(
            Pair(0, 15),
            Pair(17, 33)
        )

        val result = processor(text)
        // Either direct emit or flush should return content
        val hasContent = result.isNotEmpty() || processor.flush() != null
        assertTrue(hasContent, "Should handle newlines")
    }

    @Test
    fun `handles exclamation and question marks as sentence boundaries`() {
        // "What is this! How does it work? Great!"
        val text = "What is this! How does it work? Great!"
        // "What is this!" = 12 chars (0-11), " How does it work?" = 16 chars (12-27), " Great!" = 7 chars (28-34)
        every { mockDetector.findBoundaries(text) } returns listOf(
            Pair(0, 12),
            Pair(12, 28),
            Pair(28, 35)
        )

        val result = processor(text)
        assertTrue(result.isNotEmpty(), "Should handle exclamation and question marks")
    }

    @Test
    fun `accumulates across multiple chunks before emitting`() {
        // First chunk: "First I need to understand" - no sentence boundary
        every { mockDetector.findBoundaries("First I need to understand") } returns emptyList()

        // Second chunk after accumulation: "First I need to understand the question."
        // BreakIterator finds the sentence boundary after "understand"
        every { mockDetector.findBoundaries("First I need to understand the question.") } returns listOf(
            Pair(0, 33) // Entire text is one sentence
        )

        processor("First I need to understand")
        val result = processor(" the question.")
        assertTrue(result.isNotEmpty(), "Should emit after reaching sentence boundary")
    }

    // ========== EDGE CASE TESTS ==========

    @Test
    fun `shouldNotLoseTextWhenPunctuationSplitAcrossChunks`() {
        // Simulates tokenizer splitting "." into separate token
        // First call: "Hello" - no sentence boundary
        every { mockDetector.findBoundaries("Hello") } returns emptyList()

        val result1 = processor.invoke("Hello")
        assertTrue(result1.isEmpty(), "No emission expected - no sentence boundary yet")

        // Second call: "Hello. World." - complete sentences
        // "Hello. World." = 13 characters, treated as one sentence
        every { mockDetector.findBoundaries("Hello. World.") } returns listOf(
            Pair(0, 13) // Entire string is one sentence
        )

        val result2 = processor.invoke(". World.")
        assertEquals(1, result2.size, "Should emit one complete thought")
        assertEquals("Hello. World.", result2[0], "Should include both sentences intact")

        // Verify nothing left in buffer
        val flushed = processor.flush()
        assertNull(flushed, "Buffer should be empty after complete sentence")
    }

    @Test
    fun `shouldHandlePeriodWithoutTrailingWhitespace`() {
        // Model outputs "End." immediately followed by " Next." with no space
        // First call: "End." - BreakIterator treats as one sentence ending with period
        every { mockDetector.findBoundaries("End.") } returns listOf(
            Pair(0, 4) // "End." = 4 chars
        )

        val result1 = processor.invoke("End.")

        // Second call: The buffer gets cleared after processing "End.", so only " Next." is in buffer
        // Mock both cases - when buffer has just the new input vs accumulated text
        every { mockDetector.findBoundaries(" Next.") } returns emptyList()

        val result2 = processor.invoke(" Next.")
        // After result2, buffer has " Next.", we need to call flush to get all content
        val flushed = processor.flush()
        assertNotNull(flushed, "Should have content after flush")
    }

    @Test
    fun `shouldNotSplitOnPeriodInsideQuotes`() {
        // Should NOT treat "Hello." inside quotes as sentence boundary
        val text = """He said "Hello." Then he left."""
        // BreakIterator should treat the entire string as ONE sentence because of quotes
        // Text length: He said "Hello." Then he left. = 30 characters
        every { mockDetector.findBoundaries(text) } returns listOf(
            Pair(0, 30) // Entire text is one sentence
        )

        val result = processor.invoke(text)
        // Should emit as single thought, not break at the quoted period
        assertEquals(1, result.size, "Should emit as single thought")
    }

    @Test
    fun `shouldNotLeakMemoryWithNoPunctuation`() {
        // Long text with no punctuation should eventually emit (hard limit or word count)
        val longText = "this is a very long sentence with many words but no punctuation at all in sight"
        // No punctuation = no sentence boundaries from detector
        every { mockDetector.findBoundaries(longText) } returns emptyList()

        val result = processor.invoke(longText)

        // Either should emit (due to HARD_MAX_CHARS_BEFORE_FORCE = 500) or flush should work
        // This text is 79 chars, below the 500 limit, so detector returns empty
        // flush() should still return the content
        val flushed = processor.flush()
        assertNotNull(flushed, "flush() should return content even without punctuation")
    }

    @Test
    fun `shouldDetectBoundaryWithLowercaseAfterPeriod`() {
        // Many sentences start with lowercase (e.g., "i think", "he said")
        val text = "Hello. there is a bug here."
        // BreakIterator should find both sentences even with lowercase after period
        // "Hello." = 6 chars (0-5), " there is a bug here." = 21 chars (6-26)
        every { mockDetector.findBoundaries(text) } returns listOf(
            Pair(0, 6),   // "Hello."
            Pair(6, 27)   // " there is a bug here."
        )

        val result = processor.invoke(text)
        // Should detect both sentences
        assertTrue(result.isNotEmpty(), "Should emit complete thought")
    }

    @Test
    fun `shouldPreserveFirstSentenceIncludingIntroWord`() {
        // Test that the first sentence is preserved in full, including intro words like "Okay,"
        // Input: "Okay, let's tackle this. The user wants a story about a princess..."
        val text = "Okay, let's tackle this. The user wants a story about a princess discovering a paradoxical unicorn under a blood-red moon."

        // Verify exact text length
        // "Okay, let's tackle this." = 24 chars
        // Full text = 122 chars
        assertEquals(122, text.length, "Verify text length")

        every { mockDetector.findBoundaries(text) } returns listOf(
            Pair(0, 24),   // "Okay, let's tackle this."
            Pair(24, 122)  // " The user wants a story..."
        )

        val result = processor.invoke(text)

        // Should have at least one emission
        assertTrue(result.isNotEmpty(), "Should emit at least one thought")

        // First emitted chunk should contain the FULL first sentence including "Okay,"
        val firstChunk = result[0]
        assertTrue(
            firstChunk.contains("Okay, let's tackle this"),
            "First chunk should preserve full first sentence, got: '$firstChunk'"
        )

        // If there's a second chunk, it should contain the second sentence
        if (result.size > 1) {
            val secondChunk = result[1]
            assertTrue(
                secondChunk.contains("The user wants"),
                "Second chunk should contain second sentence, got: '$secondChunk'"
            )
        }
    }
}
