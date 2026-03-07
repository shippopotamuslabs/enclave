package com.browntowndev.pocketcrew.domain.usecase

import com.browntowndev.pocketcrew.domain.usecase.chat.ProcessThinkingTokensUseCase
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals

class ProcessThinkingTokensUseCaseTest {

    private val useCase = ProcessThinkingTokensUseCase()

    @Test
    fun `basic thinking token detection`() {
        var state = useCase("", "<think>", false)
        assertEquals(true, state.isThinking)
        assertEquals("", state.textToEmit)
        assertEquals("", state.buffer)

        // Second chunk - reasoning
        state = useCase(state.buffer, "Let me think...", state.isThinking)
        assertEquals(true, state.isThinking)
        assertEquals("Let me think...", state.textToEmit)

        // Third chunk - start token embedded
        state = useCase(state.buffer, "<think>More thoughts", state.isThinking)
        assertEquals(true, state.isThinking)
        assertEquals("More thoughts", state.textToEmit)

        // Fourth chunk - end token
        state = useCase(state.buffer, "</think>\nNow I know.", state.isThinking)
        assertEquals(false, state.isThinking)
        assertEquals("\nNow I know.", state.textToEmit)

        // Fifth chunk - response continuation
        state = useCase(state.buffer, " Final answer.", state.isThinking)
        assertEquals(false, state.isThinking)
        assertEquals(" Final answer.", state.textToEmit)
    }

    @Test
    fun `handles split start thinking token - first chunk is opening angle bracket`() {
        // "<" is buffered (partial token)
        var state = useCase("", "<", false)
        assertEquals(false, state.isThinking)
        assertEquals("", state.textToEmit)
        assertEquals("<", state.buffer)

        // Next chunk completes the token
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
    fun `handles split start thinking token - think part split`() {
        // "<t" is buffered (partial token)
        var state = useCase("", "<t", false)
        assertEquals(false, state.isThinking)
        assertEquals("", state.textToEmit)
        assertEquals("<t", state.buffer)

        // Next chunk completes token
        state = useCase(state.buffer, "hink>Reasoning", state.isThinking)
        assertEquals(true, state.isThinking)
        assertEquals("Reasoning", state.textToEmit)
    }

    @Test
    fun `handles split end thinking token - first chunk is closing angle bracket`() {
        // Start thinking
        var state = useCase("", "<think>", false)
        assertEquals(true, state.isThinking)

        // Add reasoning - this emits "My reasoning"
        state = useCase(state.buffer, "My reasoning", state.isThinking)
        assertEquals(true, state.isThinking)
        assertEquals("My reasoning", state.textToEmit)
        assertEquals("", state.buffer)

        // When we get partial end token "<", buffer it
        // Note: "My reasoning" was already emitted in previous step
        state = useCase(state.buffer, "<", state.isThinking)
        assertEquals(true, state.isThinking)
        assertEquals("", state.textToEmit)  // Nothing new to emit
        assertEquals("<", state.buffer)     // Buffer the partial

        // Complete token with remaining "/think>"
        state = useCase(state.buffer, "/think>", state.isThinking)
        assertEquals(false, state.isThinking)
        assertEquals("", state.textToEmit)

        // Response
        state = useCase(state.buffer, "Response", state.isThinking)
        assertEquals(false, state.isThinking)
        assertEquals("Response", state.textToEmit)
    }

    @Test
    fun `handles end thinking token split across multiple chunks`() {
        // Start thinking
        var state = useCase("", "<think>", false)
        state = useCase(state.buffer, "My reasoning", state.isThinking)
        // "My reasoning" was emitted, buffer should be empty

        // Partial: "<" buffered
        state = useCase(state.buffer, "<", state.isThinking)
        assertEquals("", state.textToEmit)  // Nothing new to emit
        assertEquals("<", state.buffer)

        // Partial: "/" buffered - combines with previous "<" to make "</"
        state = useCase(state.buffer, "/", state.isThinking)
        assertEquals("", state.textToEmit)
        assertEquals("</", state.buffer)  // Combined

        // Complete token
        state = useCase(state.buffer, "think>", state.isThinking)
        assertEquals(false, state.isThinking)

        // Response
        state = useCase(state.buffer, "Response", state.isThinking)
        assertEquals("Response", state.textToEmit)
    }

    @Test
    fun `handles empty chunks`() {
        var state = useCase("", "<think>", false)
        state = useCase(state.buffer, "", state.isThinking)
        assertEquals("", state.textToEmit)

        state = useCase(state.buffer, "reasoning", state.isThinking)
        assertEquals("reasoning", state.textToEmit)

        state = useCase(state.buffer, "</think>", state.isThinking)
        assertEquals(false, state.isThinking)

        state = useCase(state.buffer, "", state.isThinking)
        assertEquals("", state.textToEmit)
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
    fun `handles multiple thinking cycles`() {
        // First cycle: think, respond
        var state = useCase("", "<think>", false)
        state = useCase(state.buffer, "first thought", state.isThinking)
        assertEquals(true, state.isThinking)

        state = useCase(state.buffer, "</think>first response", state.isThinking)
        assertEquals(false, state.isThinking)

        // Second cycle: think, respond
        state = useCase(state.buffer, "<think>", state.isThinking)
        state = useCase(state.buffer, "second thought", state.isThinking)
        assertEquals(true, state.isThinking)

        state = useCase(state.buffer, "</think>second response", state.isThinking)
        assertEquals(false, state.isThinking)
    }
}
