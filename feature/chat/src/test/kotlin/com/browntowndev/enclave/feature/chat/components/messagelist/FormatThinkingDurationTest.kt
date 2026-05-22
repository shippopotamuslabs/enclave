package com.browntowndev.enclave.feature.chat.components.messagelist

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("formatThinkingDuration")
class FormatThinkingDurationTest {
    @Test
    fun `formats sub minute durations as seconds`() {
        assertEquals("45s", formatThinkingDuration(45))
    }

    @Test
    fun `formats minute and remainder`() {
        assertEquals("2m 5s", formatThinkingDuration(125))
    }

    @Test
    fun `formats exact minutes with zero seconds`() {
        assertEquals("1m 0s", formatThinkingDuration(60))
    }
}
