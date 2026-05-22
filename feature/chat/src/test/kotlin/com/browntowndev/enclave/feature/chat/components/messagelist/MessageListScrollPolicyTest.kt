package com.browntowndev.enclave.feature.chat.components.messagelist

import androidx.compose.ui.unit.dp
import com.browntowndev.enclave.domain.model.chat.MessageId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("Message list scroll policy")
class MessageListScrollPolicyTest {
    private val userId = MessageId("user-1")
    private val otherUserId = MessageId("user-2")

    @Test
    fun `initial open scrolls to latest user turn without animation`() {
        val action =
            resolveMessageListProgrammaticScrollAction(
                initialScrollDone = false,
                messagesNotEmpty = true,
                latestUserMessageId = userId,
                lastScrolledUserMessageId = null,
                latestUserMessageIndex = 3,
                messageCount = 5,
            )

        assertEquals(
            MessageListProgrammaticScrollAction.ScrollToUserTurn(lazyIndex = 3, animated = false),
            action,
        )
    }

    @Test
    fun `new user message scrolls to user row with animation`() {
        val action =
            resolveMessageListProgrammaticScrollAction(
                initialScrollDone = true,
                messagesNotEmpty = true,
                latestUserMessageId = otherUserId,
                lastScrolledUserMessageId = userId,
                latestUserMessageIndex = 5,
                messageCount = 7,
            )

        assertEquals(
            MessageListProgrammaticScrollAction.ScrollToUserTurn(lazyIndex = 5, animated = true),
            action,
        )
    }

    @Test
    fun `streaming token updates do not trigger programmatic scroll`() {
        val action =
            resolveMessageListProgrammaticScrollAction(
                initialScrollDone = true,
                messagesNotEmpty = true,
                latestUserMessageId = userId,
                lastScrolledUserMessageId = userId,
                latestUserMessageIndex = 3,
                messageCount = 5,
            )

        assertEquals(MessageListProgrammaticScrollAction.None, action)
    }

    @Test
    fun `assistant completion does not trigger programmatic scroll`() {
        val action =
            resolveMessageListProgrammaticScrollAction(
                initialScrollDone = true,
                messagesNotEmpty = true,
                latestUserMessageId = userId,
                lastScrolledUserMessageId = userId,
                latestUserMessageIndex = 3,
                messageCount = 5,
            )

        assertEquals(MessageListProgrammaticScrollAction.None, action)
    }

    @Test
    fun `empty messages do not trigger programmatic scroll`() {
        val action =
            resolveMessageListProgrammaticScrollAction(
                initialScrollDone = false,
                messagesNotEmpty = false,
                latestUserMessageId = null,
                lastScrolledUserMessageId = null,
                latestUserMessageIndex = -1,
                messageCount = 0,
            )

        assertEquals(MessageListProgrammaticScrollAction.None, action)
    }

    @Test
    fun `active interaction height leaves only bottom content padding below viewport`() {
        val minHeight =
            activeInteractionMinHeight(
                viewportHeight = 720.dp,
                bottomContentPadding = 16.dp,
            )

        assertEquals(704.dp, minHeight)
    }

    @Test
    fun `active turn content height includes measured rows and spacing`() {
        val contentHeightPx =
            messageListActiveTurnContentHeightPx(
                itemHeightsPx = listOf(96, 220, 80),
                itemSpacingPx = 12f,
            )

        assertEquals(420, contentHeightPx)
    }

    @Test
    fun `bottom spacer fills remaining viewport below active turn`() {
        val spacerHeightPx =
            messageListBottomSpacerHeightPx(
                targetMinHeightPx = 704f,
                activeTurnContentHeightPx = 108,
                bottomSpacerSpacingPx = 12f,
            )

        assertEquals(584, spacerHeightPx)
    }

    @Test
    fun `bottom spacer does not go negative when active turn fills viewport`() {
        val spacerHeightPx =
            messageListBottomSpacerHeightPx(
                targetMinHeightPx = 704f,
                activeTurnContentHeightPx = 720,
                bottomSpacerSpacingPx = 12f,
            )

        assertEquals(0, spacerHeightPx)
    }

    @Test
    fun `fab is visible while scrolling when list can scroll forward`() {
        val update =
            resolveMessageListFabVisibilityUpdate(
                isScrollInProgress = true,
                canScrollForward = true,
            )

        assertEquals(MessageListFabVisibilityUpdate.SetVisible(true), update)
    }

    @Test
    fun `fab hides when scrolled to bottom`() {
        val update =
            resolveMessageListFabVisibilityUpdate(
                isScrollInProgress = false,
                canScrollForward = false,
            )

        assertEquals(MessageListFabVisibilityUpdate.SetVisible(false), update)
    }

    @Test
    fun `fab schedules auto hide after scroll stops above bottom`() {
        val update =
            resolveMessageListFabVisibilityUpdate(
                isScrollInProgress = false,
                canScrollForward = true,
            )

        assertEquals(MessageListFabVisibilityUpdate.ScheduleAutoHide, update)
    }
}
