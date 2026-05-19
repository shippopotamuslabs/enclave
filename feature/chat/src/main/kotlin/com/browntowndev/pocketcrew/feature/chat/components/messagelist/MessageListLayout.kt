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
 * Total lazy rows: history messages, active user, viewport filler, and active assistant tail.
 */
internal fun messageListTotalLazyItems(
    messageCount: Int,
    latestUserMessageIndex: Int,
): Int =
    if (latestUserMessageIndex != -1) {
        messageCount + 1
    } else {
        messageCount
    }

internal fun messageListActiveFillerLazyIndex(latestUserMessageIndex: Int): Int =
    latestUserMessageIndex + 1

internal fun messageListActiveAssistantStartLazyIndex(latestUserMessageIndex: Int): Int =
    latestUserMessageIndex + 2

/**
 * Height of the spacer below the active user message that keeps the user bubble pinned to the
 * top of the viewport while the assistant response streams downward.
 */
internal fun messageListActiveViewportFillerHeightPx(
    targetMinHeightPx: Float,
    userHeightPx: Int,
    assistantTailHeightPx: Int,
    lazyItemSpacingPx: Float,
    activeAssistantCount: Int,
): Int {
    if (targetMinHeightPx <= 0f) return 0
    val gapCount =
        when {
            activeAssistantCount > 0 -> 1 + activeAssistantCount
            else -> 1
        }
    val contentPx = userHeightPx + assistantTailHeightPx
    val gapsPx = lazyItemSpacingPx * gapCount
    val fillerPx = targetMinHeightPx - contentPx - gapsPx
    return maxOf(0f, fillerPx).roundToInt()
}

internal fun messageListActiveAssistantTailHeightPx(
    assistantHeightsPx: Collection<Int>,
    lazyItemSpacingPx: Float,
): Int {
    if (assistantHeightsPx.isEmpty()) return 0
    val heightsSum = assistantHeightsPx.sum()
    val internalGaps = ((assistantHeightsPx.size - 1) * lazyItemSpacingPx).roundToInt()
    return heightsSum + internalGaps
}
