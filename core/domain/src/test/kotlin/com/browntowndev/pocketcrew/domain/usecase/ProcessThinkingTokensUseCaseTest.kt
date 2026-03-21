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
        val state2 = useCase("[/TH", "INK]", true)
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
}
