package com.browntowndev.pocketcrew.feature.chat.components.messagelist

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

internal fun activeInteractionMinHeight(
    viewportHeight: Dp,
    bottomContentPadding: Dp,
): Dp = maxOf(0.dp, viewportHeight - bottomContentPadding)

/**
 * LazyColumn index of the latest user message row (history prefix + active user item).
 */
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

/**
 * Total lazy rows: history prefix, active user, and one active-assistant column.
 */
internal fun messageListTotalLazyItems(
    messageCount: Int,
    latestUserMessageIndex: Int,
): Int =
    if (latestUserMessageIndex != -1) {
        latestUserMessageIndex + 2
    } else {
        messageCount
    }

internal fun messageListActiveAssistantColumnLazyIndex(latestUserMessageIndex: Int): Int =
    latestUserMessageIndex + 1

/**
 * Minimum height of the assistant column below the active user row so indicators and
 * streaming content sit directly under the user bubble with padding below the turn.
 */
internal fun messageListActiveTurnColumnMinHeightPx(
    targetMinHeightPx: Float,
    userHeightPx: Int,
    userToColumnSpacingPx: Float,
): Int {
    if (targetMinHeightPx <= 0f) return 0
    val remaining = targetMinHeightPx - userHeightPx - userToColumnSpacingPx
    return maxOf(0f, remaining).roundToInt()
}
