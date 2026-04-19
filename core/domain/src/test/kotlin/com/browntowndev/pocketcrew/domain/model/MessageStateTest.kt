package com.browntowndev.pocketcrew.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Tests for MessageState enum.
 */
class MessageStateTest {

    @Test
    fun messageState_hasProcessingValue() {
        val state = MessageState.PROCESSING
        assertEquals("PROCESSING", state.name)
    }

    @Test
    fun messageState_hasThinkingValue() {
        val state = MessageState.THINKING
        assertEquals("THINKING", state.name)
    }

    @Test
    fun messageState_hasGeneratingValue() {
        val state = MessageState.GENERATING
        assertEquals("GENERATING", state.name)
    }

    @Test
    fun messageState_hasCompleteValue() {
        val state = MessageState.COMPLETE
        assertEquals("COMPLETE", state.name)
    }

    @Test
    fun messageState_hasExactlyFiveValues() {
        val values = MessageState.entries
        assertEquals(5, values.size)
    }
}
