package com.browntowndev.pocketcrew.feature.chat.components.messagelist

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
    fun `scroll target is active user row index when a user message exists`() {
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

    @Test
    fun `total lazy items includes user filler and assistant tail`() {
        assertEquals(
            6,
            messageListTotalLazyItems(
                messageCount = 5,
                latestUserMessageIndex = 3,
            ),
        )
    }

    @Test
    fun `viewport filler shrinks as assistant tail grows`() {
        val targetMinPx = 800f
        val userPx = 120
        val spacingPx = 12f

        val initialFiller =
            messageListActiveViewportFillerHeightPx(
                targetMinHeightPx = targetMinPx,
                userHeightPx = userPx,
                assistantTailHeightPx = 0,
                lazyItemSpacingPx = spacingPx,
                activeAssistantCount = 1,
            )
        val laterFiller =
            messageListActiveViewportFillerHeightPx(
                targetMinHeightPx = targetMinPx,
                userHeightPx = userPx,
                assistantTailHeightPx = 200,
                lazyItemSpacingPx = spacingPx,
                activeAssistantCount = 1,
            )

        assertEquals(656, initialFiller)
        assertEquals(456, laterFiller)
        assertEquals(200, initialFiller - laterFiller)
    }

    @Test
    fun `assistant tail height sums messages and internal gaps`() {
        assertEquals(
            250,
            messageListActiveAssistantTailHeightPx(
                assistantHeightsPx = listOf(100, 120),
                lazyItemSpacingPx = 30f,
            ),
        )
    }
}
