package com.browntowndev.pocketcrew.feature.chat.components.detailbottomsheet

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("thinkingSheetHeaderTitle")
class ThinkingSheetHeaderTitleTest {
    @Test
    fun `active thinking without elapsed shows Thinking`() {
        assertEquals("Thinking", thinkingSheetHeaderTitle(isStreaming = true, durationSeconds = 0L))
    }

    @Test
    fun `active thinking with elapsed shows counter`() {
        assertEquals(
            "Thinking • 12s",
            thinkingSheetHeaderTitle(isStreaming = true, durationSeconds = 12L),
        )
    }

    @Test
    fun `completed thinking shows Thought for duration`() {
        assertEquals(
            "Thought for 45s",
            thinkingSheetHeaderTitle(isStreaming = false, durationSeconds = 45L),
        )
    }
}

@DisplayName("thinkingSheetDisplayDuration")
class ThinkingSheetDisplayDurationTest {
    @Test
    fun `streaming prefers elapsed over zero stored duration`() {
        assertEquals(
            8L,
            thinkingSheetDisplayDuration(
                isStreaming = true,
                durationSeconds = 0L,
                elapsedSeconds = 8,
            ),
        )
    }

    @Test
    fun `streaming with no elapsed yet uses stored duration`() {
        assertEquals(
            0L,
            thinkingSheetDisplayDuration(
                isStreaming = true,
                durationSeconds = 0L,
                elapsedSeconds = 0,
            ),
        )
    }

    @Test
    fun `completed uses elapsed when stored duration is zero`() {
        assertEquals(
            5L,
            thinkingSheetDisplayDuration(
                isStreaming = false,
                durationSeconds = 0L,
                elapsedSeconds = 5,
            ),
        )
    }
}
