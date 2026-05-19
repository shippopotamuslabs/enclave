package com.browntowndev.pocketcrew.feature.chat.components.messagelist

import com.browntowndev.pocketcrew.domain.model.chat.MessageId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
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
  fun `filler shrink returns compensation delta when anchor is active`() {
    assertEquals(
      120,
      messageListFillerScrollCompensationPx(
        previousFillerHeightPx = 400,
        newFillerHeightPx = 280,
        isScrollInProgress = false,
        suppressScrollAnchor = false,
      ),
    )
  }

  @Test
  fun `filler shrink does not compensate while user is scrolling`() {
    assertNull(
      messageListFillerScrollCompensationPx(
        previousFillerHeightPx = 400,
        newFillerHeightPx = 280,
        isScrollInProgress = true,
        suppressScrollAnchor = false,
      ),
    )
  }

  @Test
  fun `filler shrink does not compensate while programmatic scroll is in flight`() {
    assertNull(
      messageListFillerScrollCompensationPx(
        previousFillerHeightPx = 400,
        newFillerHeightPx = 280,
        isScrollInProgress = false,
        suppressScrollAnchor = true,
      ),
    )
  }

  @Test
  fun `filler growth does not compensate`() {
    assertNull(
      messageListFillerScrollCompensationPx(
        previousFillerHeightPx = 200,
        newFillerHeightPx = 260,
        isScrollInProgress = false,
        suppressScrollAnchor = false,
      ),
    )
  }

  @Test
  fun `first filler measurement does not compensate`() {
    assertNull(
      messageListFillerScrollCompensationPx(
        previousFillerHeightPx = -1,
        newFillerHeightPx = 500,
        isScrollInProgress = false,
        suppressScrollAnchor = false,
      ),
    )
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
