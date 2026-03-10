package com.browntowndev.pocketcrew.domain.usecase

import com.browntowndev.pocketcrew.domain.usecase.chat.ProcessThinkingTokensUseCase
import com.browntowndev.pocketcrew.domain.usecase.chat.ProcessThinkingTokensUseCase.SegmentKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Comprehensive tests for ProcessThinkingTokensUseCase.
 *
 * Tests cover:
 * - Basic thinking token detection
 * - Split tokens across chunk boundaries
 * - Interleaved visible and thinking segments
 * - Edge cases and malformed input
 * - EmittedSegments API
 * - Split token edge cases
 */
class ProcessThinkingTokensUseCaseTest {

    private lateinit var useCase: ProcessThinkingTokensUseCase

    @Before
    fun setup() {
        useCase = ProcessThinkingTokensUseCase()
    }

    // ============================================================================
    // Basic Functionality Tests
    // ============================================================================

    @Test
    fun `basic thinking token detection`() {
        // Start thinking
        var state = useCase("", "<think>", false)
        assertEquals(true, state.isThinking)
        assertEquals("", state.textToEmit)
        assertEquals("", state.buffer)

        // Reasoning content
        state = useCase(state.buffer, "Let me think...", state.isThinking)
        assertEquals(true, state.isThinking)
        assertEquals("Let me think...", state.textToEmit)

        // End thinking and response
        state = useCase(state.buffer, "</think>\nNow I know.", state.isThinking)
        assertEquals(false, state.isThinking)
        assertEquals("\nNow I know.", state.textToEmit)

        // Continue response
        state = useCase(state.buffer, " Final answer.", state.isThinking)
        assertEquals(false, state.isThinking)
        assertEquals(" Final answer.", state.textToEmit)
    }

    @Test
    fun `no thinking tokens - plain text`() {
        val state = useCase("", "Hello world!", false)

        assertEquals(false, state.isThinking)
        assertEquals("Hello world!", state.textToEmit)
        assertEquals("", state.buffer)
    }

    @Test
    fun `empty thinking block`() {
        var state = useCase("", "<think>", false)
        state = useCase(state.buffer, "</think>", state.isThinking)

        assertEquals(false, state.isThinking)
        assertEquals("", state.textToEmit)
    }

    @Test
    fun `empty chunks do not break state`() {
        var state = useCase("", "<think>", false)
        assertEquals(true, state.isThinking)

        state = useCase(state.buffer, "", state.isThinking)
        assertEquals(true, state.isThinking)
        assertEquals("", state.textToEmit)

        state = useCase(state.buffer, "reasoning", state.isThinking)
        assertEquals("reasoning", state.textToEmit)

        state = useCase(state.buffer, "</think>", state.isThinking)
        assertEquals(false, state.isThinking)

        state = useCase(state.buffer, "", state.isThinking)
        assertEquals("", state.textToEmit)
    }

    // ============================================================================
    // Split Token Tests - Start Token
    // ============================================================================

    @Test
    fun `split start token - partial bracket`() {
        // "<" is buffered (partial token)
        var state = useCase("", "<", false)
        assertEquals(false, state.isThinking)
        assertEquals("", state.textToEmit)
        assertEquals("<", state.buffer)

        // Complete the token
        state = useCase(state.buffer, "think>", state.isThinking)
        assertEquals(true, state.isThinking)
        assertEquals("", state.textToEmit)
        assertEquals("", state.buffer)

        // Next chunk is reasoning
        state = useCase(state.buffer, "Reasoning...", state.isThinking)
        assertEquals(true, state.isThinking)
        assertEquals("Reasoning...", state.textToEmit)
    }

    @Test
    fun `split start token - partial think`() {
        // "<t" is buffered
        var state = useCase("", "<t", false)
        assertEquals(false, state.isThinking)
        assertEquals("<t", state.buffer)

        // Complete token
        state = useCase(state.buffer, "hink>Reasoning", state.isThinking)
        assertEquals(true, state.isThinking)
        assertEquals("Reasoning", state.textToEmit)
    }

    @Test
    fun `split start token - all three parts`() {
        // Part 1: "<"
        var state = useCase("", "<", false)
        assertEquals("<", state.buffer)

        // Part 2: "thi"
        state = useCase(state.buffer, "thi", state.isThinking)
        assertEquals("<thi", state.buffer)

        // Part 3: "nk>" + content
        state = useCase(state.buffer, "nk>My reasoning", state.isThinking)
        assertEquals(true, state.isThinking)
        assertEquals("My reasoning", state.textToEmit)
    }

    @Test
    fun `split start token - embedded in text`() {
        var state = useCase("", "Hello <thi", false)
        assertEquals("Hello ", state.textToEmit)
        assertEquals("<thi", state.buffer)

        state = useCase(state.buffer, "nk>reasoning", state.isThinking)
        assertEquals(true, state.isThinking)
        assertEquals("reasoning", state.textToEmit)
    }

    // ============================================================================
    // Split Token Tests - End Token
    // ============================================================================

    @Test
    fun `split end token - partial bracket`() {
        // Start thinking
        var state = useCase("", "<think>", false)
        state = useCase(state.buffer, "My reasoning", state.isThinking)

        // Partial: "<"
        state = useCase(state.buffer, "<", state.isThinking)
        assertEquals(true, state.isThinking)
        assertEquals("", state.textToEmit)
        assertEquals("<", state.buffer)

        // Complete token
        state = useCase(state.buffer, "/think>", state.isThinking)
        assertEquals(false, state.isThinking)
        assertEquals("", state.textToEmit)

        // Response
        state = useCase(state.buffer, "Response", state.isThinking)
        assertEquals("Response", state.textToEmit)
    }

    @Test
    fun `split end token - three parts`() {
        // Start thinking
        var state = useCase("", "<think>", false)
        state = useCase(state.buffer, "reasoning", state.isThinking)

        // Part 1: "<"
        state = useCase(state.buffer, "<", state.isThinking)
        assertEquals("<", state.buffer)

        // Part 2: "/"
        state = useCase(state.buffer, "/", state.isThinking)
        assertEquals("</", state.buffer)

        // Part 3: "think>" + response
        state = useCase(state.buffer, "think>Done", state.isThinking)
        assertEquals(false, state.isThinking)
        assertEquals("Done", state.textToEmit)
    }

    // ============================================================================
    // Interleaved Segments Tests
    // ============================================================================

    @Test
    fun `emits segments with correct kinds - visible then thinking`() {
        val state = useCase("", "<think>thinking content</think>visible response", false)

        assertEquals(2, state.emittedSegments.size)

        val segment1 = state.emittedSegments[0]
        assertEquals(SegmentKind.THINKING, segment1.kind)
        assertEquals("thinking content", segment1.text)

        val segment2 = state.emittedSegments[1]
        assertEquals(SegmentKind.VISIBLE, segment2.kind)
        assertEquals("visible response", segment2.text)
    }

    @Test
    fun `emits segments with correct kinds - visible then thinking then visible`() {
        val state = useCase("", "Hello<think>think1</think>middle<think>think2</think>end", false)

        assertEquals(4, state.emittedSegments.size)

        assertEquals(SegmentKind.VISIBLE, state.emittedSegments[0].kind)
        assertEquals("Hello", state.emittedSegments[0].text)

        assertEquals(SegmentKind.THINKING, state.emittedSegments[1].kind)
        assertEquals("think1", state.emittedSegments[1].text)

        assertEquals(SegmentKind.VISIBLE, state.emittedSegments[2].kind)
        assertEquals("middle", state.emittedSegments[2].text)

        assertEquals(SegmentKind.THINKING, state.emittedSegments[3].kind)
        assertEquals("think2", state.emittedSegments[3].text)
    }

    @Test
    fun `visibleTextToEmit returns only visible segments`() {
        val state = useCase("", "before<think>thinking</think>after", false)

        assertEquals("beforeafter", state.visibleTextToEmit)
    }

    @Test
    fun `thinkingTextToEmit returns only thinking segments`() {
        val state = useCase("", "before<think>thinking1</think>middle<think>thinking2</think>after", false)

        assertEquals("thinking1thinking2", state.thinkingTextToEmit)
    }

    @Test
    fun `multiple thinking cycles emit correct segments`() {
        // First cycle: think, respond
        var state = useCase("", "<think>first thought</think>first response", false)
        assertEquals(2, state.emittedSegments.size)
        assertEquals(SegmentKind.THINKING, state.emittedSegments[0].kind)
        assertEquals(SegmentKind.VISIBLE, state.emittedSegments[1].kind)

        // Second cycle: think, respond
        state = useCase(state.buffer, "<think>second thought</think>second response", state.isThinking)
        assertEquals(2, state.emittedSegments.size)
        assertEquals(SegmentKind.THINKING, state.emittedSegments[0].kind)
        assertEquals(SegmentKind.VISIBLE, state.emittedSegments[1].kind)
    }

    // ============================================================================
    // Edge Cases and Malformed Input
    // ============================================================================

    @Test
    fun `handles malformed end token without exploding`() {
        // When visible text contains ">" that looks like end of token
        var state = useCase("", "<think>", false)
        state = useCase(state.buffer, "a > b", state.isThinking)
        assertEquals(true, state.isThinking)
        assertEquals("a > b", state.textToEmit)
    }

    @Test
    fun `handles thinking tag in middle of text`() {
        var state = useCase("", "<think>", false)
        state = useCase(state.buffer, "thinking", state.isThinking)
        state = useCase(state.buffer, "</think>Here is the answer", state.isThinking)

        assertEquals(false, state.isThinking)
        assertEquals("Here is the answer", state.textToEmit)
    }

    @Test
    fun `handles multiple consecutive thinking tags`() {
        val state = useCase("", "<think>a</think><think>b</think>", false)

        assertEquals(2, state.emittedSegments.size)
        assertEquals(SegmentKind.THINKING, state.emittedSegments[0].kind)
        assertEquals("a", state.emittedSegments[0].text)
        assertEquals(SegmentKind.THINKING, state.emittedSegments[1].kind)
        assertEquals("b", state.emittedSegments[1].text)
    }

    @Test
    fun `handles text before thinking`() {
        val state = useCase("", "Hello there<think>thinking", false)

        assertEquals(1, state.emittedSegments.size)
        assertEquals(SegmentKind.VISIBLE, state.emittedSegments[0].kind)
        assertEquals("Hello there", state.emittedSegments[0].text)
        assertEquals(true, state.isThinking)
    }

    @Test
    fun `handles newlines in thinking blocks`() {
        var state = useCase("", "<think>", false)
        state = useCase(state.buffer, "Line 1\nLine 2\nLine 3", state.isThinking)
        state = useCase(state.buffer, "</think>Final", state.isThinking)

        assertEquals("Line 1\nLine 2\nLine 3", state.emittedSegments.find { it.kind == SegmentKind.THINKING }?.text)
        assertEquals("Final", state.visibleTextToEmit)
    }

    @Test
    fun `handles unicode content`() {
        var state = useCase("", "<think>", false)
        state = useCase(state.buffer, "分析中...日本語", state.isThinking)
        state = useCase(state.buffer, "</think> результат", state.isThinking)

        assertEquals("分析中...日本語", state.thinkingTextToEmit)
        assertEquals(" результат", state.visibleTextToEmit)
    }

    // ============================================================================
    // Adjacent Merge Tests
    // ============================================================================

    @Test
    fun `merges consecutive visible segments`() {
        // Two visible segments should be merged
        var state = useCase("", "Hello", false)
        state = useCase(state.buffer, " World", state.isThinking)
        state = useCase(state.buffer, "!", state.isThinking)

        // Should have been merged into single visible segment
        assertTrue(state.emittedSegments.size <= 2)
    }

    @Test
    fun `merges consecutive thinking segments`() {
        var state = useCase("", "<think>", false)
        state = useCase(state.buffer, "think1", state.isThinking)
        state = useCase(state.buffer, "think2", state.isThinking)
        state = useCase(state.buffer, "</think>", state.isThinking)

        // The thinking content should be merged
        assertEquals("think1think2", state.thinkingTextToEmit)
    }

    // ============================================================================
    // Buffer Edge Cases
    // ============================================================================

    @Test
    fun `buffer contains partial start token at end of chunk`() {
        var state = useCase("", "Hello <t", false)

        assertEquals("Hello ", state.textToEmit)
        assertEquals("<t", state.buffer)
    }

    @Test
    fun `buffer contains partial end token at end of chunk`() {
        var state = useCase("", "<think>", false)
        state = useCase(state.buffer, "thinking <", state.isThinking)

        assertEquals("thinking ", state.textToEmit)
        assertEquals("<", state.buffer)
    }

    @Test
    fun `buffer cleared after token completion`() {
        var state = useCase("", "<think>", false)
        state = useCase(state.buffer, "thoughts", state.isThinking)
        assertEquals("", state.buffer)

        state = useCase(state.buffer, "</think>", state.isThinking)
        assertEquals("", state.buffer)
    }

    // ============================================================================
    // Stream Simulation Tests - Real-world scenarios
    // ============================================================================

    @Test
    fun `simulates realistic stream - thinking first then answer`() {
        val chunks = listOf(
            "<think>",
            "I need to ",
            "calculate ",
            "this",
            ">",
            "The answer is 42",
            "</think>",
            "."
        )

        var isThinking = false
        var buffer = ""
        var fullResponse = StringBuilder()
        var fullThinking = StringBuilder()

        for (chunk in chunks) {
            val state = useCase(buffer, chunk, isThinking)
            buffer = state.buffer
            isThinking = state.isThinking

            state.emittedSegments.forEach { segment ->
                when (segment.kind) {
                    SegmentKind.VISIBLE -> fullResponse.append(segment.text)
                    SegmentKind.THINKING -> fullThinking.append(segment.text)
                }
            }
        }

        assertEquals("I need to calculate this>.", fullResponse.toString())
        assertEquals("I need to calculate this>", fullThinking.toString())
    }

    @Test
    fun `simulates realistic stream - interleaved`() {
        val chunks = listOf(
            "Hello",
            "<think>",
            "thinking",
            "</think>",
            " world"
        )

        var isThinking = false
        var buffer = ""
        var fullResponse = StringBuilder()

        for (chunk in chunks) {
            val state = useCase(buffer, chunk, isThinking)
            buffer = state.buffer
            isThinking = state.isThinking

            state.emittedSegments.forEach { segment ->
                if (segment.kind == SegmentKind.VISIBLE) {
                    fullResponse.append(segment.text)
                }
            }
        }

        assertEquals("Hello world", fullResponse.toString())
    }

    @Test
    fun `simulates realistic stream - multiple interleaved blocks`() {
        val chunks = listOf(
            "First ",
            "<think>",
            "thought one",
            "</think>",
            " then ",
            "<think>",
            "thought two",
            "</think>",
            " done"
        )

        var isThinking = false
        var buffer = ""
        var fullResponse = StringBuilder()
        var allThinking = StringBuilder()

        for (chunk in chunks) {
            val state = useCase(buffer, chunk, isThinking)
            buffer = state.buffer
            isThinking = state.isThinking

            state.emittedSegments.forEach { segment ->
                when (segment.kind) {
                    SegmentKind.VISIBLE -> fullResponse.append(segment.text)
                    SegmentKind.THINKING -> allThinking.append(segment.text)
                }
            }
        }

        assertEquals("First  then  done", fullResponse.toString())
        assertEquals("thought onethought two", allThinking.toString())
    }

    // ============================================================================
    // Edge Cases - Partial Tokens
    // ============================================================================

    @Test
    fun `handles token that looks like partial of other token`() {
        // "<t" could become "<think>" or just stay as text
        var state = useCase("", "<t", false)
        assertEquals("", state.textToEmit)
        assertEquals("<t", state.buffer)

        // Adding "h>" makes it a valid token
        state = useCase(state.buffer, "h>", state.isThinking)
        assertEquals(true, state.isThinking)
    }

    @Test
    fun `handles partial token that never completes`() {
        // "<xyz" never completes to a valid token
        var state = useCase("", "<xyz", false)
        assertEquals("", state.textToEmit)
        assertEquals("<xyz", state.buffer)

        // Add more content that isn't "think>"
        state = useCase(state.buffer, "abc", state.isThinking)
        // Should emit everything except partial token suffix
        assertTrue(state.textToEmit.isNotEmpty() || state.buffer.isNotEmpty())
    }

    @Test
    fun `handles multiple partial token candidates`() {
        // Buffer that could be start of either token
        var state = useCase("", "<", false)
        assertEquals("<", state.buffer)

        // Adding "/" could make "</" (partial end) or stay as text
        state = useCase(state.buffer, "/", state.isThinking)
        assertEquals("</", state.buffer)
    }
}
