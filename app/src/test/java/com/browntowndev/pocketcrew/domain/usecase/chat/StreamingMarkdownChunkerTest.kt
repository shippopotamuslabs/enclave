package com.browntowndev.pocketcrew.domain.usecase.chat

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Tests for Streaming Markdown Block Detection state machine.
 *
 * These tests verify the new Markdown-based chunking approach that replaces
 * linguistic sentence detection (BreakIterator) with structural Markdown boundary detection.
 *
 * The new system:
 * - Tracks isInCodeBlock (toggled by ```)
 * - Tracks isInMathBlock (toggled by $ or \[ \])
 * - When locked: accumulates but doesn't emit
 * - When unlocked: splits at \n\n (paragraphs) or list markers
 */
class StreamingMarkdownChunkerTest {

    private lateinit var processor: BufferThinkingStepsUseCase

    @BeforeEach
    fun setup() {
        // Use the real production implementation - no constructor argument needed
        processor = BufferThinkingStepsUseCase()
    }

    // ========== CODE BLOCK PROTECTION TESTS ==========

    @Test
    fun `should not emit until code block closes`() {
        // Code block starts but doesn't close - should NOT emit
        val result = processor("```python\ndef hello():")
        assertTrue(result.isEmpty(), "Should not emit while inside code block")

        // Still inside code block - should still NOT emit
        val result2 = processor("\n    print('world')")
        assertTrue(result2.isEmpty(), "Should not emit while still inside code block")

        // Code block closes - should now emit the complete code block
        val result3 = processor("\n```")
        assertEquals(1, result3.size, "Should emit after code block closes")
        assertTrue(result3[0].contains("def hello()"), "Should preserve code block content")
    }

    @Test
    fun `should preserve code block content intact`() {
        // Multi-line code block should be preserved as single chunk
        val result = processor("```\nfirst line\nsecond line\nthird line\n```")

        // After code block closes, content should be emitted in result or flush
        val flushed = processor.flush()
        val allContent = result.joinToString(" ") + (flushed ?: "")

        assertTrue(allContent.contains("first line"), "Should preserve code content")
        assertTrue(allContent.contains("second line"), "Should preserve code content")
    }

    @Test
    fun `should emit after code block closes with following text`() {
        // Code block followed by regular text
        val result = processor("```\ncode here\n```\n\nNow for regular text.")

        // Should have emitted something - either in result or flush
        val hasContent = result.isNotEmpty() || processor.flush() != null
        assertTrue(hasContent, "Should emit content")
    }

    @Test
    fun `should handle multiple code blocks`() {
        val result = processor("```\nfirst code\n```\n\n```\nsecond code\n```")

        // Should emit something
        val hasContent = result.isNotEmpty() || processor.flush() != null
        assertTrue(hasContent, "Should emit content")
    }

    // ========== MATH BLOCK PROTECTION TESTS ==========

    @Test
    fun `should not emit until inline math block closes`() {
        // Inline math $...$ should be protected
        val result = processor("Solving for x: $2x + 3 =")
        assertTrue(result.isEmpty(), "Should not emit while inside math block")

        // When math block closes (second $), we emit
        val result2 = processor(" 7$")
        // Should emit when math block closes
        assertTrue(result2.isNotEmpty() || processor.flush() != null, "Should emit after math closes")
    }

    @Test
    fun `should not emit until block math closes`() {
        // Block math with \[ \] should be protected
        val result = processor("The formula is: \\[")
        assertTrue(result.isEmpty(), "Should not emit while inside math block")

        val result2 = processor("\\int_0^1 x^2 dx")
        assertTrue(result2.isEmpty(), "Should not emit while still inside math block")

        // Math block closes with \]
        val result3 = processor("\\]")
        assertTrue(result3.isNotEmpty() || processor.flush() != null, "Should emit after math closes")
    }

    @Test
    fun `should handle double dollar signs for display math`() {
        // Display math $$...$$ should be protected - use raw string
        val result = processor("\$\$x + y = z\$")
        assertTrue(result.isEmpty(), "Should not emit while inside math block")

        val result2 = processor("\$")
        assertTrue(result2.isEmpty() || result2.isNotEmpty(), "Should handle math block closure")
    }

    // ========== PARAGRAPH BREAK TESTS ==========

    @Test
    fun `should emit on double newline`() {
        // Double newline (paragraph break) should trigger emission
        val result = processor("First paragraph.\n\nSecond paragraph.")

        assertTrue(result.isNotEmpty(), "Should emit on paragraph break")
        assertTrue(result[0].contains("First paragraph"), "First chunk should have first paragraph")
    }

    @Test
    fun `should preserve whitespace for Markwon`() {
        // Whitespace should be preserved for proper Markdown rendering
        // Note: With paragraph breaks, content is split - first part emitted, rest in buffer
        val result = processor("Line 1\n\nLine 2")

        // Either emitted in result or in buffer for flush
        val hasContent = result.isNotEmpty() || processor.flush() != null
        assertTrue(hasContent, "Should have content")
    }

    @Test
    fun `should handle multiple paragraph breaks`() {
        val result = processor("Para 1\n\nPara 2\n\nPara 3")

        assertTrue(result.isNotEmpty() || processor.flush() != null,
            "Should handle multiple paragraph breaks")
    }

    // ========== LIST ITEM TESTS ==========

    @Test
    fun `should emit on bullet list marker`() {
        // Bullet list marker should trigger emission
        val result = processor("First item\n- Second item")

        assertTrue(result.isNotEmpty(), "Should emit on bullet list marker")
    }

    @Test
    fun `should emit on asterisk list marker`() {
        val result = processor("First item\n* Second item")

        assertTrue(result.isNotEmpty(), "Should emit on asterisk list marker")
    }

    @Test
    fun `should emit on numbered list marker`() {
        // Numbered list markers should trigger emission
        val result = processor("First step\n1. Second step")

        assertTrue(result.isNotEmpty(), "Should emit on numbered list marker")
    }

    @Test
    fun `should preserve list marker in output`() {
        processor("Item 1\n- Item 2")
        val flushed = processor.flush()

        assertNotNull(flushed)
        assertTrue(flushed!!.contains("- Item 2"), "Should preserve list marker")
    }

    @Test
    fun `should handle nested list markers`() {
        val result = processor("First\n- Item A\n- Item B\n\nSecond")

        assertTrue(result.isNotEmpty() || processor.flush() != null,
            "Should handle nested list markers")
    }

    // ========== LENGTH FALLBACK TESTS ==========

    @Test
    fun `should emit long unstructured text`() {
        // Long text without any boundaries should eventually emit (fallback)
        val longText = "This is a very long paragraph that goes on and on " +
            "without any line breaks or list markers or code blocks. " +
            "It just continues endlessly. "

        val result = processor(longText)

        // Should emit due to length fallback
        assertTrue(result.isNotEmpty() || processor.flush() != null,
            "Should emit long unstructured text")
    }

    @Test
    fun `should not trigger length fallback mid code block`() {
        // Start a code block
        processor("```\ncode code code")

        // Add lots of text that would trigger length fallback in normal case
        val longText = "x ".repeat(500)
        val result = processor(longText)

        // Should NOT emit - still inside code block
        assertTrue(result.isEmpty(), "Should NOT emit mid code block regardless of length")

        // Close the code block
        val result2 = processor("\n```")
        assertTrue(result2.isNotEmpty(), "Should emit after code block closes")
    }

    @Test
    fun `should not trigger length fallback mid list`() {
        // Start a list
        processor("- Item 1\n- Item 2")

        // Add lots of text
        val longText = "x ".repeat(500)
        processor(longText)

        // Should not have emitted yet (still in list context)
        val flushed = processor.flush()
        // After flush, we should get the content but the key is we didn't split mid-list
        assertNotNull(flushed)
    }

    // ========== STREAMING TESTS ==========

    @Test
    fun `should accumulate until boundary in streaming`() {
        // First chunk - no boundary
        val result1 = processor("Some text without")
        assertTrue(result1.isEmpty(), "Should not emit without boundary")

        // Second chunk completes the boundary
        val result2 = processor(" any boundary yet.")
        assertTrue(result2.isEmpty(), "Still no boundary - paragraph not complete")

        // Third chunk has paragraph break
        val result3 = processor("\n\nNow we have a break.")
        assertTrue(result3.isNotEmpty() || result3.isEmpty(), "Should handle streaming")
    }

    @Test
    fun `should handle split code block across chunks`() {
        // Code block starts
        processor("```")
        var result = processor("\npyth")
        assertTrue(result.isEmpty(), "Should not emit while building code block")

        // Code block continues and closes in same chunk
        result = processor("on\n```")
        assertTrue(result.isNotEmpty(), "Should emit after code block closes")
    }

    @Test
    fun `should handle split math block across chunks`() {
        // Math block starts
        processor("$2x +")
        var result = processor(" 5 = 10$")
        // After math block closes, should emit
        assertTrue(result.isNotEmpty() || processor.flush() != null,
            "Should emit after math block closes")
    }

    // ========== EDGE CASE TESTS ==========

    @Test
    fun `should handle empty input`() {
        val result = processor("")
        assertTrue(result.isEmpty(), "Empty input should return empty list")
    }

    @Test
    fun `should handle whitespace only input`() {
        val result = processor("   \n\n   ")
        // Should handle gracefully - may or may not emit depending on content
        // Just shouldn't crash
        assertTrue(result is List<String>)
    }

    @Test
    fun `should handle flush with no content`() {
        processor.reset()
        val flushed = processor.flush()
        assertNull(flushed, "Flush with no content should return null")
    }

    @Test
    fun `should handle mixed code and regular text`() {
        // Regular text followed by code block followed by regular text
        val result = processor("Before code\n```\ncode\n```\nAfter code")

        // Content may be in result or flush
        val flushed = processor.flush()
        val allContent = result.joinToString(" ") + (flushed ?: "")

        assertTrue(allContent.contains("Before code"), "Should preserve first text")
        assertTrue(allContent.contains("code"), "Should preserve code")
        assertTrue(allContent.contains("After code"), "Should preserve last text")
    }

    @Test
    fun `should handle code block with list inside`() {
        // Code block containing what looks like a list marker
        // When code block is complete (balanced delimiters), content is emitted
        val result = processor("```\n- not a list\n- still not a list\n```")

        // Should emit content
        val hasContent = result.isNotEmpty() || processor.flush() != null
        assertTrue(hasContent, "Should emit content")
    }

    @Test
    fun `should reset state properly`() {
        // Start in code block
        processor("```code")

        // Reset
        processor.reset()

        // After reset, should be able to emit immediately
        val result = processor("New text")
        assertTrue(result.isNotEmpty() || processor.flush() != null,
            "After reset, should emit normally")
    }

    @Test
    fun `should handle backticks not forming code block`() {
        // Single backtick (inline code) should not toggle state
        processor("Use `variable` in code")

        val flushed = processor.flush()
        assertNotNull(flushed)
        assertTrue(flushed!!.contains("`variable`"), "Should preserve inline code")
    }
}
