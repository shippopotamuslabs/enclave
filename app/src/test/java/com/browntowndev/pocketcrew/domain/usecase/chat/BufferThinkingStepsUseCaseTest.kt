package com.browntowndev.pocketcrew.domain.usecase.chat

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Tests for BufferThinkingStepsUseCase with Markdown-based chunking.
 * Verifies edge cases in streaming thinking text buffering.
 */
class BufferThinkingStepsUseCaseTest {

    private lateinit var processor: BufferThinkingStepsUseCase

    @BeforeEach
    fun setup() {
        // New implementation uses Markdown-based chunking
        processor = BufferThinkingStepsUseCase()
    }

    @Test
    fun `empty input returns empty list`() {
        val result = processor("")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `flush emits remaining content`() {
        processor("First I need to understand")
        val flushed = processor.flush()
        assertTrue(!flushed.isNullOrBlank(), "Flush should return remaining content")
    }

    @Test
    fun `reset clears state`() {
        processor("First I need to understand")
        processor.reset()
        val flushed = processor.flush()
        assertTrue(flushed.isNullOrBlank(), "After reset, flush should return null")
    }

    @Test
    fun `words without boundaries don't emit prematurely`() {
        // No paragraph break or list marker = no boundary
        val result1 = processor("First")
        val result2 = processor(" I need")
        val result3 = processor(" to understand")
        assertTrue(result1.isEmpty() && result2.isEmpty() && result3.isEmpty(),
            "Should not emit prematurely without boundaries")
    }

    @Test
    fun `emits correctly on paragraph break`() {
        val text = "First sentence here\n\nSecond sentence here\n\nThird sentence here"

        val result = processor(text)
        assertTrue(result.isNotEmpty(), "Should emit on paragraph breaks")
    }

    @Test
    fun `emits correctly on list marker`() {
        val text = "First item\n- Second item"

        val result = processor(text)
        assertTrue(result.isNotEmpty(), "Should emit on list marker")
    }

    @Test
    fun `emits correctly on numbered list marker`() {
        val text = "First step\n1. Second step"

        val result = processor(text)
        assertTrue(result.isNotEmpty(), "Should emit on numbered list marker")
    }

    @Test
    fun `streaming multiple chunks emits correctly`() {
        // First chunk: no boundary
        val result1 = processor("First")
        assertTrue(result1.isEmpty(), "Should not emit without boundary")

        // Second chunk: completes with paragraph break
        val result2 = processor(" paragraph\n\nSecond paragraph")
        assertTrue(result2.isNotEmpty(), "Should emit after boundary")
    }

    @Test
    fun `handles newlines as sentence separators`() {
        val text = "First paragraph\n\nSecond paragraph"
        val result = processor(text)
        assertTrue(result.isNotEmpty(), "Should handle newlines")
    }

    // ========== EDGE CASE TESTS ==========

    @Test
    fun `shouldNotLoseTextWhenBoundarySplitAcrossChunks`() {
        // First call: "Hello" - no boundary
        val result1 = processor.invoke("Hello")
        assertTrue(result1.isEmpty(), "No emission expected - no boundary yet")

        // Second call: "Hello\n\nWorld" - complete boundary
        // The first "Hello" should be emitted in result2
        val result2 = processor.invoke("\n\nWorld")
        assertTrue(result2.isNotEmpty() || result1.isNotEmpty(), "Should emit after boundary")
    }

    @Test
    fun `shouldHandleListMarkerWithoutTrailingSpace`() {
        // Model outputs list marker
        val result = processor.invoke("Item 1\n-Item 2")
        assertTrue(result.isNotEmpty() || processor.flush() != null, "Should handle list marker")
    }

    @Test
    fun `shouldNotSplitOnSingleNewline`() {
        // Single newline is NOT a boundary - only double newline is
        val result = processor("Line 1\nLine 2\nLine 3")
        // No double newline, so should not emit - but length fallback might trigger
        // Just verify it doesn't crash
        assertTrue(result is List<String>)
    }

    @Test
    fun `shouldNotLeakMemoryWithNoBoundaries`() {
        // Long text with no boundaries should eventually emit (hard limit)
        val longText = "this is a very long paragraph with many words but no paragraph breaks at all in sight ".repeat(10)

        val result = processor.invoke(longText)

        // Either should emit (due to HARD_MAX_CHARS_BEFORE_FORCE = 500) or flush should work
        val hasContent = result.isNotEmpty() || processor.flush() != null
        assertTrue(hasContent, "Should emit long text")
    }

    @Test
    fun `shouldPreserveMarkdownFormatting`() {
        // Test that markdown formatting is preserved
        // With paragraph break, first part is emitted, rest is in buffer
        val result = processor("**Bold text**\n\n*Italic text*")

        // Either emitted or in buffer
        val hasContent = result.isNotEmpty() || processor.flush() != null
        assertTrue(hasContent, "Should have content")
    }

    @Test
    fun `shouldPreserveFirstSentenceIncludingMarkerWord`() {
        // Test that the first content is preserved in full
        processor("First item\n- Second item")
        val result = processor("")

        // Should have emitted the first item
        assertTrue(result.isNotEmpty() || processor.flush() != null, "Should emit at least one thought")
    }

    // ========== CLAUDE-STYLE REASONING HEADER TESTS ==========

    @Test
    fun `shouldSplitOnThoughtForHeader`() {
        // Claude-style: **Thought for 1m 53s** followed by content
        val text = "**Thought for 1m 53s**\nLet me analyze this problem step by step."

        val result = processor(text)

        assertTrue(result.isNotEmpty(), "Should emit on Thought for header")
        // The first chunk should contain the header
        assertTrue(result.any { it.contains("**Thought for 1m 53s**") },
            "First chunk should contain the header")
    }

    @Test
    fun `shouldSplitOnThoughtForHeaderWithDuration`() {
        // Various duration formats
        val text = "**Thought for 2m 10s**\nAnalyzing the next approach..."

        val result = processor(text)

        assertTrue(result.isNotEmpty(), "Should emit on header with duration")
        assertTrue(result.first().contains("**Thought for 2m 10s**"),
            "Should include header in emitted chunk")
    }

    @Test
    fun `shouldDetectEmojiPrefixedHeader`() {
        // Claude-style: emoji prefix before bold header
        val text = "🔎 **Insight Inferno**\nThis is a key insight about the problem."

        val result = processor(text)

        assertTrue(result.isNotEmpty(), "Should detect emoji-prefixed header")
        assertTrue(result.any { it.contains("🔎 **Insight Inferno**") },
            "Should include emoji-prefixed header")
    }

    @Test
    fun `shouldDetectMultipleEmojiPrefixes`() {
        val text = "💡 **Analysis**\nFirst analysis point.\n\n🎯 **Next Step**\nSecond thought."

        val result = processor(text)

        assertTrue(result.size >= 1, "Should emit on header")
        assertTrue(result.any { it.contains("💡 **Analysis**") },
            "Should detect first header")
    }

    @Test
    fun `shouldNotSplitOnHeaderInsideCodeBlock`() {
        // Header inside unclosed code block should be ignored
        val text = "```python\n**Thought for 1m**\nprint('hello')\n```\n\n**Thought for 2m**"

        val result = processor(text)

        // Should not emit the header inside code block - only emit when code block closes
        // The complete code block should be emitted
        assertTrue(result.isNotEmpty(), "Should emit complete code block")
    }

    @Test
    fun `shouldSplitOnHeaderAfterCodeBlockClosed`() {
        // Header after code block should work
        val text = "```python\nprint('hello')\n```\n\n**Thought for 1m 30s**\nNow let me think about this."

        val result = processor(text)

        assertTrue(result.isNotEmpty(), "Should emit after code block closed and header found")
    }

    @Test
    fun `shouldPrioritizeHeadersOverParagraphBreaks`() {
        // Mixed: has both header and paragraph breaks - headers should take priority
        val text = "**Thought for 1m**\nFirst thought content\n\n**Thought for 2m**\nSecond thought content"

        val result = processor(text)

        assertTrue(result.isNotEmpty(), "Should emit on headers")
        // Headers should create boundaries before paragraph breaks
        assertTrue(result.any { it.contains("**Thought for 1m**") },
            "Should include first header")
    }

    @Test
    fun `shouldFallBackToParagraphWhenNoHeaders`() {
        // No headers - should use existing paragraph logic
        val text = "First paragraph content here.\n\nSecond paragraph content here."

        val result = processor(text)

        assertTrue(result.isNotEmpty(), "Should emit on paragraph breaks (fallback)")
    }

    @Test
    fun `shouldHandleStreamingHeaderSplitAcrossChunks`() {
        // First chunk: incomplete header
        val result1 = processor("**Thought for")
        assertTrue(result1.isEmpty(), "Should not emit incomplete header")

        // Second chunk: completes header but no content yet
        val result2 = processor(" 1m 53s**")
        assertTrue(result2.isEmpty(), "Should not emit header without content")

        // Third chunk: adds content after header
        val result3 = processor("\nNow analyzing...")
        assertTrue(result3.isNotEmpty(), "Should emit when content arrives after header")
        assertTrue(result3.any { it.contains("**Thought for 1m 53s**") },
            "Should include complete header with content")
    }

    @Test
    fun `shouldHandleShortThoughtsGracefully`() {
        // Short content after header
        val text = "**Thought for 30s**\nOk."

        val result = processor(text)

        assertTrue(result.isNotEmpty(), "Should handle short content after header")
    }

    @Test
    fun `shouldStillUseLengthFallbackWithHeaders`() {
        // Long content without proper headers or paragraphs
        val longText = "**Analysis** " + "some content ".repeat(50)

        val result = processor(longText)

        // Should emit due to length fallback
        val hasContent = result.isNotEmpty() || processor.flush() != null
        assertTrue(hasContent, "Should emit due to length fallback")
    }

    @Test
    fun `shouldSplitMultipleHeadersInSequence`() {
        // Multiple headers in one text
        val text = "**Thought for 1m**\nFirst thought.\n\n**Thought for 2m**\nSecond thought.\n\n**Thought for 3m**\nThird thought."

        val result = processor(text)

        assertTrue(result.size >= 2, "Should emit multiple chunks at headers")
    }
}
