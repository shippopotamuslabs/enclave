package com.browntowndev.pocketcrew.feature.chat.components.detailbottomsheet

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("formatSheetThinkingDuration")
class FormatSheetThinkingDurationTest {
    @Test
    fun `formats seconds under one minute`() {
        assertEquals("45s", formatSheetThinkingDuration(45))
    }

    @Test
    fun `formats minutes and seconds`() {
        assertEquals("2m 5s", formatSheetThinkingDuration(125))
    }

    @Test
    fun `formats exact minute`() {
        assertEquals("1m 0s", formatSheetThinkingDuration(60))
    }
}
