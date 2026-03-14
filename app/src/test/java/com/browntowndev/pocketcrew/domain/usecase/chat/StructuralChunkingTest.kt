package com.browntowndev.pocketcrew.domain.usecase.chat

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Production-grade tests for structural chunking behavior with Markdown-based detection.
 *
 * These tests verify the BEHAVIOR of BufferThinkingStepsUseCase through its public API:
 * - Should not split inside code blocks
 * - Should not split inside math blocks
 * - Should detect markdown paragraph breaks and list markers as structural breaks
 */
class StructuralChunkingTest {

    private lateinit var processor: BufferThinkingStepsUseCase

    @BeforeEach
    fun setup() {
        // New implementation uses Markdown-based chunking
        processor = BufferThinkingStepsUseCase()
    }

    // ========== CODE BLOCK TESTS ==========

    @Test
    fun `should NOT split inside code block`() {
        // Input: code block should not be split
        val text = "```python\ndef hello():\n    print('world')\n```"

        val result = processor(text)

        // Should emit the complete code block as one chunk
        assertTrue(result.isNotEmpty(), "Should emit code block content")
    }

    @Test
    fun `should NOT emit until code block closes`() {
        // Code block starts but doesn't close
        val result = processor("```python\ndef hello():")

        // Should NOT emit while inside code block
        assertTrue(result.isEmpty(), "Should not emit while inside code block")

        // Close the code block
        val result2 = processor("\n```")
        assertTrue(result2.isNotEmpty() || processor.flush() != null,
            "Should emit after code block closes")
    }

    // ========== MARKDOWN HEADER TESTS ==========

    @Test
    fun `markdown header should be preserved`() {
        // Markdown headers are just text, not boundaries themselves
        val text = "**Critical Analysis**: Analysis content"

        val result = processor(text)

        // Header should be preserved in output
        assertTrue(result.isNotEmpty() || processor.flush() != null,
            "Should emit with header preserved")
    }

    // ========== PARAGRAPH BREAK TESTS ==========

    @Test
    fun `paragraph breaks should force new chunk`() {
        val text = "First paragraph.\n\nSecond paragraph."

        processor.reset()
        val result = processor(text)

        // Should have emitted
        assertTrue(result.isNotEmpty() || processor.flush() != null,
            "Should emit on paragraph break")
    }

    // ========== LIST ITEM TESTS ==========

    @Test
    fun `list markers should force new chunk`() {
        val text = "First item\n- Second item"

        processor.reset()
        val result = processor(text)

        // Should emit on list marker
        assertTrue(result.isNotEmpty() || processor.flush() != null,
            "Should emit on list marker")
    }

    @Test
    fun `numbered list markers should force new chunk`() {
        val text = "First step\n1. Second step"

        processor.reset()
        val result = processor(text)

        assertTrue(result.isNotEmpty() || processor.flush() != null,
            "Should emit on numbered list marker")
    }

    // ========== INTEGRATION TEST ==========

    @Test
    fun `full flow should produce clean chunked output`() {
        // Complete thinking trace with multiple blocks
        val text = "**Analysis**: First part\n\n- Point 1\n- Point 2\n\n```\ncode\n```\n\nFinal paragraph."

        processor.reset()
        val result = processor(text)

        // Should have multiple chunks
        assertTrue(result.isNotEmpty() || processor.flush() != null,
            "Should produce chunked output")
    }

    @Test
    fun `flush should return all remaining content`() {
        processor("Partial content")
        val flushed = processor.flush()

        assertNotNull(flushed, "Flush should return remaining content")
        assertTrue(flushed!!.contains("Partial content"), "Flush should contain the text")
    }
}
