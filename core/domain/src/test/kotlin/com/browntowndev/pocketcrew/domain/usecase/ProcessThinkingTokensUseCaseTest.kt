package com.browntowndev.pocketcrew.domain.usecase

import com.browntowndev.pocketcrew.domain.usecase.chat.ProcessThinkingTokensUseCase
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals

/**
 * Tests for ProcessThinkingTokensUseCase - basic behavior verification.
 */
class ProcessThinkingTokensUseCaseTest {

    private lateinit var useCase: ProcessThinkingTokensUseCase

    @BeforeEach
    fun setup() {
        useCase = ProcessThinkingTokensUseCase()
    }

    @Test
    fun `start token enters thinking mode`() {
        val state = useCase("", "<think>", false)
        assertEquals(true, state.isThinking)
    }

    @Test
    fun `end token exits thinking mode`() {
        val state = useCase("", "</think>", false)
        assertEquals(false, state.isThinking)
    }

    @Test
    fun `complete thinking block emits thinking content`() {
        val state = useCase("", "<think>thinking content</think>", false)
        assertEquals(false, state.isThinking)
        assertEquals("thinking content", state.thinkingTextToEmit)
    }

    @Test
    fun `visible text is emitted correctly`() {
        val state = useCase("", "Hello World", false)
        assertEquals("Hello World", state.visibleTextToEmit)
    }

    @Test
    fun `visible text after thinking is emitted`() {
        val state = useCase("", "<think>think</think>Hello", false)
        assertEquals("Hello", state.visibleTextToEmit)
    }

    @Test
    fun `thinking text before visible is emitted`() {
        val state = useCase("", "<think>thinking</think>Hello", false)
        assertEquals("thinking", state.thinkingTextToEmit)
    }

    @Test
    fun `thinking then visible both emitted`() {
        val state = useCase("", "<think>think</think>response", false)
        assertEquals("think", state.thinkingTextToEmit)
        assertEquals("response", state.visibleTextToEmit)
    }

    @Test
    fun `multiple thinking blocks are tracked separately`() {
        val state = useCase("", "<think>first</think><think>second</think>", false)
        // Both thinking blocks should be in the thinking text
        assertEquals("firstsecond", state.thinkingTextToEmit)
    }

    @Test
    fun `empty thinking block is handled`() {
        val state = useCase("", "<think></think>", false)
        assertEquals("", state.thinkingTextToEmit)
        assertEquals(false, state.isThinking)
    }

    @Test
    fun `textToEmit returns all text combined`() {
        val state = useCase("", "<think>think</think>response", false)
        assertEquals("thinkresponse", state.textToEmit)
    }

    @Test
    fun `visible and thinking text are separate`() {
        val state = useCase("", "Hello<think>thinking</think>World", false)
        assertEquals("HelloWorld", state.visibleTextToEmit)
        assertEquals("thinking", state.thinkingTextToEmit)
    }

    // =========================================================================
    // [THINK] / [/THINK] Variant Tests
    // =========================================================================

    @Test
    fun `THINK_bracket start token enters thinking mode`() {
        val state = useCase("", "[THINK]", false)
        assertEquals(true, state.isThinking)
    }

    @Test
    fun `slash_THINK_bracket end token exits thinking mode`() {
        val state = useCase("", "[/THINK]", true)
        assertEquals(false, state.isThinking)
    }

    @Test
    fun `complete thinking block with THINK_bracket emits thinking content`() {
        val state = useCase("", "[THINK]thinking content[/THINK]", false)
        assertEquals(false, state.isThinking)
        assertEquals("thinking content", state.thinkingTextToEmit)
    }

    @Test
    fun `THINK_bracket followed by visible text`() {
        val state = useCase("", "[THINK]think[/THINK]Hello", false)
        assertEquals("think", state.thinkingTextToEmit)
        assertEquals("Hello", state.visibleTextToEmit)
    }

    // =========================================================================
    // Mixed Variant Tests
    // =========================================================================

    @Test
    fun `multiple thinking blocks with THINK_bracket are concatenated`() {
        val state = useCase("", "[THINK]first[/THINK][THINK]second[/THINK]", false)
        assertEquals("firstsecond", state.thinkingTextToEmit)
    }

    @Test
    fun `interleaved visible text with THINK_bracket`() {
        val state = useCase("", "Hello[THINK]thinking[/THINK]World", false)
        assertEquals("HelloWorld", state.visibleTextToEmit)
        assertEquals("thinking", state.thinkingTextToEmit)
    }

    @Test
    fun `visible text between thinking blocks with THINK_bracket`() {
        val state = useCase("", "[THINK]first[/THINK]middle[THINK]second[/THINK]", false)
        assertEquals("firstsecond", state.thinkingTextToEmit)
        assertEquals("middle", state.visibleTextToEmit)
    }

    // =========================================================================
    // Partial Token Boundary Tests (Streaming Simulation)
    // =========================================================================

    @Test
    fun `partial THINK_bracket token split across chunks - step 1`() {
        val state = useCase("", "[TH", false)
        // Partial token should be held in buffer, nothing emitted
        assertEquals("[TH", state.buffer)
        assertEquals("", state.visibleTextToEmit)
        assertEquals("", state.thinkingTextToEmit)
    }

    @Test
    fun `partial THINK_bracket token split across chunks - step 2`() {
        // Step 1: partial "[TH" in buffer
        // Step 2: receive rest of token
        val state = useCase("[TH", "INK]think", false)
        assertEquals(true, state.isThinking)
        assertEquals("think", state.thinkingTextToEmit)
    }

    @Test
    fun `partial slash_THINK_bracket token split across chunks`() {
        // Step 1: partial "[/TH" while thinking
        val state1 = useCase("", "[/TH", true)
        assertEquals("[/TH", state1.buffer)
        assertEquals(true, state1.isThinking)

        // Step 2: complete the end token
        val state2 = useCase("[/TH", "INK]", true, state1.thinkingTextToEmit, state1.visibleTextToEmit)
        assertEquals(false, state2.isThinking)
    }

    @Test
    fun `partial token detection prevents premature emission`() {
        // "[THI" is a prefix of [THINK], so should not emit
        val state = useCase("", "[THI", false)
        assertEquals("[THI", state.buffer)
        assertEquals("", state.visibleTextToEmit)
        assertEquals("", state.thinkingTextToEmit)
    }

    // =========================================================================
    // Edge Case Tests
    // =========================================================================

    @Test
    fun `empty THINK_bracket slash_THINK_bracket block handled`() {
        val state = useCase("", "[THINK][/THINK]", false)
        assertEquals("", state.thinkingTextToEmit)
        assertEquals(false, state.isThinking)
    }

    @Test
    fun `thinking then visible both emitted with THINK_bracket`() {
        val state = useCase("", "[THINK]think[/THINK]response", false)
        assertEquals("think", state.thinkingTextToEmit)
        assertEquals("response", state.visibleTextToEmit)
    }

    // =========================================================================
    // STREAMING Tests (Critical Missing Tests for [THINK] Tag Bug)
    // =========================================================================

    @Test
    fun `opening tag THINK_bracket alone then content in separate call emits as THINKING`() {
        // THE CRITICAL MISSING TEST - streaming without complete block in single call
        // Step 1: Opening tag alone
        val state1 = useCase("", "[THINK]", false)
        assertEquals(true, state1.isThinking)
        assertEquals("", state1.visibleTextToEmit)
        assertEquals("", state1.thinkingTextToEmit)

        // Step 2: Content arrives in separate call - should be classified as THINKING
        val state2 = useCase("", "thinking content", true, state1.thinkingTextToEmit, state1.visibleTextToEmit)
        assertEquals(true, state2.isThinking)
        assertEquals("thinking content", state2.thinkingTextToEmit)
        assertEquals("", state2.visibleTextToEmit)
    }

    @Test
    fun `complete streaming flow - THINK_bracket content slash_THINK_bracket`() {
        // Step 1: Opening tag alone
        val state1 = useCase("", "[THINK]", false)
        assertEquals(true, state1.isThinking)

        // Step 2: Content in separate call
        val state2 = useCase("", "thinking content", true, state1.thinkingTextToEmit, state1.visibleTextToEmit)
        assertEquals("thinking content", state2.thinkingTextToEmit)
        assertEquals(true, state2.isThinking)

        // Step 3: Closing tag with visible text
        val state3 = useCase("", "[/THINK]response", true, state2.thinkingTextToEmit, state2.visibleTextToEmit)
        assertEquals(false, state3.isThinking)
        assertEquals("response", state3.visibleTextToEmit)
    }

    @Test
    fun `multiple streaming chunks accumulate correctly with THINK_bracket`() {
        // Step 1: Opening tag
        val state1 = useCase("", "[THINK]", false)
        assertEquals(true, state1.isThinking)

        // Step 2-4: Multiple chunks accumulate as THINKING
        val state2 = useCase("", "part1", true, state1.thinkingTextToEmit, state1.visibleTextToEmit)
        assertEquals("part1", state2.thinkingTextToEmit)

        val state3 = useCase("", " part2", true, state2.thinkingTextToEmit, state2.visibleTextToEmit)
        assertEquals("part1 part2", state3.thinkingTextToEmit)

        val state4 = useCase("", " part3", true, state3.thinkingTextToEmit, state3.visibleTextToEmit)
        assertEquals("part1 part2 part3", state4.thinkingTextToEmit)

        // Step 5: Closing tag
        val state5 = useCase("", "[/THINK]", true, state4.thinkingTextToEmit, state4.visibleTextToEmit)
        assertEquals(false, state5.isThinking)
        assertEquals("part1 part2 part3", state5.thinkingTextToEmit)
    }

    @Test
    fun `streaming with visible text after closing tag`() {
        // Step 1: Opening tag
        val state1 = useCase("", "[THINK]", false)

        // Step 2: Thinking content
        val state2 = useCase("", "thinking", true, state1.thinkingTextToEmit, state1.visibleTextToEmit)
        assertEquals("thinking", state2.thinkingTextToEmit)

        // Step 3: Closing tag with visible text
        val state3 = useCase("", "[/THINK]visible", true, state2.thinkingTextToEmit, state2.visibleTextToEmit)
        assertEquals("thinking", state3.thinkingTextToEmit)
        assertEquals("visible", state3.visibleTextToEmit)
    }

    @Test
    fun `content without opening tag is visible`() {
        val state = useCase("", "just visible text", false)
        assertEquals(false, state.isThinking)
        assertEquals("just visible text", state.visibleTextToEmit)
        assertEquals("", state.thinkingTextToEmit)
    }

    @Test
    fun `thinking format still works correctly in streaming mode`() {
        // Regression test for <think> format in streaming
        // Step 1: Opening tag
        val state1 = useCase("", "<think>", false)
        assertEquals(true, state1.isThinking)

        // Step 2: Content in separate call
        val state2 = useCase("", "thought content", true, state1.thinkingTextToEmit, state1.visibleTextToEmit)
        assertEquals("thought content", state2.thinkingTextToEmit)

        // Step 3: Closing tag
        val state3 = useCase("", "</think>visible", true, state2.thinkingTextToEmit, state2.visibleTextToEmit)
        assertEquals(false, state3.isThinking)
        assertEquals("visible", state3.visibleTextToEmit)
    }

    @Test
    fun `switching between bracket and angle formats`() {
        // Step 1: [THINK] format
        val state1 = useCase("", "[THINK]", false)
        assertEquals(true, state1.isThinking)

        val state2 = useCase("", "bracket think", true, state1.thinkingTextToEmit, state1.visibleTextToEmit)
        assertEquals("bracket think", state2.thinkingTextToEmit)

        val state3 = useCase("", "[/THINK]", true, state2.thinkingTextToEmit, state2.visibleTextToEmit)
        assertEquals(false, state3.isThinking)

        // Step 2: <think> format
        val state4 = useCase("", "<think>", false, state3.thinkingTextToEmit, state3.visibleTextToEmit)
        assertEquals(true, state4.isThinking)

        val state5 = useCase("", "angle think", true, state4.thinkingTextToEmit, state4.visibleTextToEmit)
        // With accumulation, state5 should contain all thinking content
        assertEquals("bracket thinkangle think", state5.thinkingTextToEmit)

        val state6 = useCase("", "</think>", true, state5.thinkingTextToEmit, state5.visibleTextToEmit)
        assertEquals(false, state6.isThinking)

        // Combined thinking should have both
        assertEquals("bracket think", state2.thinkingTextToEmit)
        assertEquals("bracket thinkangle think", state5.thinkingTextToEmit)
    }
}
