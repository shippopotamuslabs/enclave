package com.browntowndev.pocketcrew.feature.chat.components

import androidx.compose.ui.unit.dp
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("Message list layout")
class MessageListLayoutTest {
    @Test
    fun `active interaction min height reserves trailing content padding inside viewport`() {
        assertEquals(
            784.dp,
            activeInteractionMinHeight(
                viewportHeight = 800.dp,
                bottomContentPadding = 16.dp,
            ),
        )
    }

    @Test
    fun `active interaction min height never goes negative`() {
        assertEquals(
            0.dp,
            activeInteractionMinHeight(
                viewportHeight = 12.dp,
                bottomContentPadding = 16.dp,
            ),
        )
    }

    @Test
    fun `scroll target is active row index when a user message exists`() {
        assertEquals(
            3,
            messageListScrollTargetLazyIndex(
                messageCount = 5,
                latestUserMessageIndex = 3,
            ),
        )
    }

    @Test
    fun `scroll target is last history index when there is no user message`() {
        assertEquals(
            4,
            messageListScrollTargetLazyIndex(
                messageCount = 5,
                latestUserMessageIndex = -1,
            ),
        )
    }

    @Test
    fun `scroll target is zero for empty message count`() {
        assertEquals(
            0,
            messageListScrollTargetLazyIndex(
                messageCount = 0,
                latestUserMessageIndex = -1,
            ),
        )
    }
}
