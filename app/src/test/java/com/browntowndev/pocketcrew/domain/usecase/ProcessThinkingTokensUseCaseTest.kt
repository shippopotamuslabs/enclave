package com.browntowndev.pocketcrew.domain.usecase

import com.browntowndev.pocketcrew.domain.usecase.chat.ProcessThinkingTokensUseCase
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Tests for ProcessThinkingTokensUseCase - basic behavior verification.
 */
class ProcessThinkingTokensUseCaseTest {

    private lateinit var useCase: ProcessThinkingTokensUseCase

    @Before
    fun setup() {
        useCase = ProcessThinkingTokensUseCase()
    }

    @Test
    fun `empty input returns empty state`() {
        val state = useCase("", "", false)
        assertEquals(false, state.isThinking)
        assertEquals("", state.buffer)
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
}
