package com.browntowndev.pocketcrew.feature.chat.components.messagelist

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow

/**
 * When the viewport filler shrinks as the assistant message grows, LazyColumn would otherwise
 * pull visible content upward. Compensate by scrolling down by the same delta so the user's
 * viewport stays fixed.
 */
@Composable
internal fun MessageListScrollAnchorEffect(
    listState: LazyListState,
    fillerHeightPx: Int,
    suppressScrollAnchor: MutableState<Boolean>,
) {
    var previousFillerHeightPx by remember { mutableIntStateOf(-1) }

    LaunchedEffect(listState) {
        snapshotFlow { fillerHeightPx to suppressScrollAnchor.value }.collect { (newHeightPx, suppressed) ->
            if (suppressed) {
                previousFillerHeightPx = newHeightPx
                return@collect
            }
            val deltaPx =
                messageListFillerScrollCompensationPx(
                    previousFillerHeightPx = previousFillerHeightPx,
                    newFillerHeightPx = newHeightPx,
                    isScrollInProgress = listState.isScrollInProgress,
                    suppressScrollAnchor = suppressed,
                )
            if (deltaPx != null) {
                listState.scroll {
                    scrollBy(deltaPx.toFloat())
                }
            }
            previousFillerHeightPx = newHeightPx
        }
    }
}
