package com.browntowndev.pocketcrew.feature.chat.components.assistantresponse

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.browntowndev.pocketcrew.feature.chat.TranscriptSegmentUi
import com.browntowndev.pocketcrew.feature.chat.components.thinkingindicator.ThinkingIndicator
import kotlinx.coroutines.delay

@Composable
internal fun LiveThinkingTranscriptRow(
    segment: TranscriptSegmentUi.ThinkingBlock,
    isPreview: Boolean,
    onToggleDetails: () -> Unit,
) {
    val startMs = segment.thinkingStartTimeMs ?: 0L
    var elapsedSeconds by remember(segment.ordinal) { mutableIntStateOf(0) }
    if (startMs > 0L && !isPreview) {
        LaunchedEffect(segment.ordinal, startMs) {
            while (true) {
                elapsedSeconds = ((System.currentTimeMillis() - startMs) / 1000).toInt()
                delay(1000L)
            }
        }
    }
    ThinkingIndicator(
        elapsedSeconds = elapsedSeconds,
        isExpanded = false,
        onToggleDetails = onToggleDetails,
    )
}
