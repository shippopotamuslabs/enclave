package com.browntowndev.pocketcrew.feature.chat.components.messagelist

import com.browntowndev.pocketcrew.domain.model.chat.MessageId

/**
 * Programmatic list scroll triggered only for chat open or a new user turn — never for
 * streaming token updates or assistant completion.
 */
internal sealed interface MessageListProgrammaticScrollAction {
    data object None : MessageListProgrammaticScrollAction

    data class ScrollToUserTurn(
        val lazyIndex: Int,
        val animated: Boolean,
    ) : MessageListProgrammaticScrollAction
}

internal fun resolveMessageListProgrammaticScrollAction(
    initialScrollDone: Boolean,
    messagesNotEmpty: Boolean,
    latestUserMessageId: MessageId?,
    lastScrolledUserMessageId: MessageId?,
    latestUserMessageIndex: Int,
    messageCount: Int,
): MessageListProgrammaticScrollAction {
    if (!messagesNotEmpty) return MessageListProgrammaticScrollAction.None

    val targetLazyIndex =
        messageListScrollTargetLazyIndex(
            messageCount = messageCount,
            latestUserMessageIndex = latestUserMessageIndex,
        )

    if (!initialScrollDone) {
        return MessageListProgrammaticScrollAction.ScrollToUserTurn(
            lazyIndex = targetLazyIndex,
            animated = false,
        )
    }

    if (latestUserMessageId != null && latestUserMessageId != lastScrolledUserMessageId) {
        return MessageListProgrammaticScrollAction.ScrollToUserTurn(
            lazyIndex = latestUserMessageIndex.coerceAtLeast(0),
            animated = true,
        )
    }

    return MessageListProgrammaticScrollAction.None
}

/**
 * Scroll delta (px) to apply when the viewport filler shrinks so visible content does not jump.
 * Returns null when no compensation should run.
 */
internal fun messageListFillerScrollCompensationPx(
    previousFillerHeightPx: Int,
    newFillerHeightPx: Int,
    isScrollInProgress: Boolean,
    suppressScrollAnchor: Boolean,
): Int? {
    if (suppressScrollAnchor) return null
    if (previousFillerHeightPx <= 0) return null
    if (newFillerHeightPx >= previousFillerHeightPx) return null
    if (isScrollInProgress) return null
    return previousFillerHeightPx - newFillerHeightPx
}

internal sealed interface MessageListFabVisibilityUpdate {
    data class SetVisible(
        val visible: Boolean,
    ) : MessageListFabVisibilityUpdate

    data object ScheduleAutoHide : MessageListFabVisibilityUpdate
}

/**
 * FAB visibility transitions driven by list scroll position
 * ([androidx.compose.foundation.lazy.LazyListState.canScrollForward]).
 */
internal fun resolveMessageListFabVisibilityUpdate(
    isScrollInProgress: Boolean,
    canScrollForward: Boolean,
): MessageListFabVisibilityUpdate =
    when {
        isScrollInProgress -> MessageListFabVisibilityUpdate.SetVisible(canScrollForward)
        !canScrollForward -> MessageListFabVisibilityUpdate.SetVisible(false)
        else -> MessageListFabVisibilityUpdate.ScheduleAutoHide
    }
