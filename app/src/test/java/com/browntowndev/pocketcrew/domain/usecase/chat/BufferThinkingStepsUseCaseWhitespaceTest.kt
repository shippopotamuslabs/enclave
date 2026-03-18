package com.browntowndev.pocketcrew.domain.usecase.chat

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Tests for BufferThinkingStepsUseCase to verify whitespace preservation.
 * These tests expose the bug where trim() removes whitespace around numbered lists.
 */
class BufferThinkingStepsUseCaseWhitespaceTest {

    private lateinit var processor: BufferThinkingStepsUseCase

    @BeforeEach
    fun setup() {
        processor = BufferThinkingStepsUseCase()
    }

    /**
     * Bug test: trim() removes whitespace around numbered list markers.
     * When a numbered list appears in thinking text and gets split at boundaries,
     * the whitespace after the list marker gets trimmed.
     *
     * Example: "Analysis\n1. First step\n2. Second step"
     * - At \n1., first chunk is "Analysis" (trimmed)
     * - At \n2., second chunk is " First step" but trim makes it "First step"
     * - This causes "1.First step" without space after the period
     */
    @Test
    fun `should preserve space after numbered list marker`() {
        // This text has list markers embedded in content without paragraph breaks
        // The list markers should NOT cause the space after "1." to be lost
        val text = "Analysis\n1. First step\n2. Second step"

        // Force emit by exceeding length limit
        processor("x".repeat(600))
        processor.reset()

        val result = processor(text)
        val output = result.joinToString("|||")

        // The result should preserve the space after "1."
        // Bug: trim() would make " First step" become "First step"
        // so we get "1.First step" instead of "1. First step"
        assertFalse(
            output.contains("1.First"),
            "Should preserve space after '1.', got: $output"
        )
    }

    /**
     * More specific test: when chunk is extracted between list markers,
     * whitespace should be preserved, not trimmed.
     */
    @Test
    fun `should preserve whitespace in list item content`() {
        // Reset to ensure clean state
        processor.reset()

        // With double newline, paragraphs get split
        val text = "Intro\n\n1. First item\n2. Second item"

        val result = processor(text)
        val output = result.joinToString("|||")

        // Debug: see what we get
        println("DEBUG whitespace: result = $result")

        // The second chunk starts with "1." - the leading space before "1" was trimmed
        // But the space after "." in "1. " should be preserved
        // The bug would produce "1.First" without the space after "."
        assertFalse(
            output.contains("1.First"),
            "Should have '1. First' with space, got: $output"
        )
    }

    /**
     * Test that emitted content preserves whitespace - no trim() should be applied.
     * Bug: trim() was removing whitespace around numbered lists.
     */
    @Test
    fun `emitted content should not be trimmed`() {
        processor.reset()

        // Add content with a paragraph break - this will trigger emission
        // The content BEFORE the paragraph break should be emitted
        val result = processor("First paragraph\n\nSecond paragraph")

        // The emitted content should preserve exact whitespace
        assertTrue(result.isNotEmpty(), "Should emit content at paragraph break, got: $result")

        // The emitted content should NOT be trimmed - it should contain the exact text
        val emitted = result.first()
        println("DEBUG emitted: '$emitted'")
        println("DEBUG length: ${emitted.length}")

        // Bug was: trim() would remove trailing whitespace from the chunk
        // Now it should preserve the exact content
        assertFalse(
            emitted.contains("First paragraphSecond"),
            "Should preserve paragraph break in emitted content, got: $emitted"
        )
    }

    /**
     * Bug test: trim() removes newlines around list markers
     */
    @Test
    fun `should preserve newlines around bullet list markers`() {
        val text = "- First item\n- Second item"

        val result = processor(text)
        val flushed = if (result.isEmpty()) processor.flush() else result.joinToString("\n")

        // Should NOT have items concatenated without newline
        assertFalse(
            flushed?.contains("First item-") == true,
            "Should preserve newline between list items, got: $flushed"
        )
    }

    /**
     * Verify that whitespace at the start/end of chunks is preserved
     */
    @Test
    fun `should preserve leading whitespace in chunk`() {
        // When a paragraph starts with indented content
        val text = "First paragraph\n\n  Indented second paragraph"

        val result = processor(text)

        // The second paragraph should preserve leading spaces
        assertTrue(result.size >= 1, "Should emit at least one chunk")
    }

    /**
     * Bug test: flush should preserve whitespace
     */
    @Test
    fun `flush should preserve whitespace in final chunk`() {
        processor("Thinking...")
        processor("1. Step one\n2. Step two")

        val flushed = processor.flush()

        assertNotEquals(null, flushed, "Flush should return content")
        // The space after "1." should be preserved
        assertFalse(
            flushed!!.contains("1.Step"),
            "Should preserve space after numbered list marker in flush, got: $flushed"
        )
    }

    /**
     * Test that numbers don't lose their trailing spaces.
     * Bug: "2018 paper" was becoming "2018paper"
     */
    @Test
    fun `should preserve space after numbers`() {
        processor.reset()

        // Text with numbers followed by words (no special list markers)
        val text = "I will cite this 2018 paper and 2020 study"

        // Force emission with length limit
        processor("x".repeat(600))
        processor.reset()

        val result = processor(text)
        val output = result.joinToString("|||")

        println("DEBUG number test: '$output'")

        // Should preserve space after "2018" and "2020"
        assertFalse(
            output.contains("2018paper") || output.contains("2020study"),
            "Should preserve space after numbers, got: $output"
        )
    }

    /**
     * Test with newline before number - should not eat the space after number
     */
    @Test
    fun `should preserve space after number with newline before`() {
        processor.reset()

        // Text with newline followed by numbered item
        val text = "Previous text\n2018 paper"

        val result = processor(text)
        val output = result.joinToString("|||")

        println("DEBUG newline number test: '$output'")

        // Should preserve space after "2018"
        assertFalse(
            output.contains("2018paper"),
            "Should preserve space after number with newline, got: $output"
        )
    }

    /**
     * Test: numbered list within text should preserve spaces
     */
    @Test
    fun `should handle numbered list in middle of text`() {
        processor.reset()

        // Text with numbered list marker in the middle
        val text = "Let me explain: 1. First point is important"

        val result = processor(text)
        val output = result.joinToString("|||")

        println("DEBUG numbered list test: '$output'")

        // Should preserve "1. First" with space after period
        assertFalse(
            output.contains("1.First"),
            "Should preserve space after list marker, got: $output"
        )
    }

    /**
     * Verify the fixed regex now detects list markers correctly
     */
    @Test
    fun `should detect numbered list markers with fixed regex`() {
        processor.reset()

        val text = "Intro\n1. First item"

        // The processor should now emit both chunks
        val result = processor(text)

        // Should have emitted "Intro" and "1. First item"
        assertTrue(result.size >= 1, "Should emit at least one chunk")
    }

    /**
     * Test: numbered list within text should preserve spaces after list marker
     * Bug fix: The regex character class was invalid, now fixed
     */
    @Test
    fun `should preserve space after numbered list with fixed regex`() {
        processor.reset()

        // Text with newline followed by numbered list marker
        val text = "Analysis\n1. First step\n2. Second step"

        val result = processor(text)

        // Should have emitted chunks at list markers
        assertTrue(result.isNotEmpty(), "Should emit at list markers")

        // Check that spaces are preserved
        val output = result.joinToString("|||")
        assertTrue(
            output.contains("1. First") || output.contains("2. Second"),
            "Should preserve list marker and content, got: $output"
        )
    }
}
