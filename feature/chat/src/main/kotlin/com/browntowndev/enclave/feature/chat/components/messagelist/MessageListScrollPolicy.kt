package com.browntowndev.enclave.feature.chat.components.messagelist

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.browntowndev.enclave.domain.model.chat.MessageId
import kotlin.math.abs
import kotlin.math.roundToInt

internal const val MESSAGE_LIST_HEIGHT_COMMIT_MIN_DELTA_PX = 8

internal fun shouldCommitMessageHeightPx(
    previousHeightPx: Int?,
    newHeightPx: Int,
    minDeltaPx: Int = MESSAGE_LIST_HEIGHT_COMMIT_MIN_DELTA_PX,
): Boolean {
    if (previousHeightPx == null) return true
    return abs(newHeightPx - previousHeightPx) >= minDeltaPx
}

internal fun activeInteractionMinHeight(
    viewportHeight: Dp,
    bottomContentPadding: Dp,
): Dp = maxOf(0.dp, viewportHeight - bottomContentPadding)

internal sealed interface MessageListProgrammaticScrollAction {
    data object None : MessageListProgrammaticScrollAction

    data class ScrollToUserTurn(
        val lazyIndex: Int,
        val animated: Boolean,
    ) : MessageListProgrammaticScrollAction
}

internal fun messageListScrollTargetLazyIndex(
    messageCount: Int,
    latestUserMessageIndex: Int,
): Int {
    if (messageCount <= 0) return 0
    return if (latestUserMessageIndex != -1) {
        latestUserMessageIndex
    } else {
        messageCount - 1
    }
}

internal fun messageListActiveTurnContentHeightPx(
    itemHeightsPx: List<Int>,
    itemSpacingPx: Float,
): Int {
    if (itemHeightsPx.isEmpty()) return 0
    val spacingPx = itemSpacingPx * (itemHeightsPx.size - 1)
    return (itemHeightsPx.sum() + spacingPx).roundToInt()
}

internal fun messageListBottomSpacerHeightPx(
    targetMinHeightPx: Float,
    activeTurnContentHeightPx: Int,
    bottomSpacerSpacingPx: Float,
): Int {
    if (targetMinHeightPx <= 0f) return 0
    val remaining =
        targetMinHeightPx -
            activeTurnContentHeightPx -
            bottomSpacerSpacingPx.coerceAtLeast(0f)
    return maxOf(0f, remaining).roundToInt()
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

    if ((latestUserMessageId != null) && (latestUserMessageId != lastScrolledUserMessageId)) {
        return MessageListProgrammaticScrollAction.ScrollToUserTurn(
            lazyIndex = latestUserMessageIndex.coerceAtLeast(0),
            animated = true,
        )
    }

    return MessageListProgrammaticScrollAction.None
}

internal sealed interface MessageListFabVisibilityUpdate {
    data class SetVisible(
        val visible: Boolean,
    ) : MessageListFabVisibilityUpdate

    data object ScheduleAutoHide : MessageListFabVisibilityUpdate
}

internal fun resolveMessageListFabVisibilityUpdate(
    isScrollInProgress: Boolean,
    canScrollForward: Boolean,
): MessageListFabVisibilityUpdate =
    when {
        isScrollInProgress -> MessageListFabVisibilityUpdate.SetVisible(visible = canScrollForward)
        !canScrollForward -> MessageListFabVisibilityUpdate.SetVisible(visible = false)
        else -> MessageListFabVisibilityUpdate.ScheduleAutoHide
    }
