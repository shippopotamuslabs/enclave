package com.browntowndev.enclave.feature.chat.components.assistantresponse

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.browntowndev.enclave.feature.chat.ToolCallBannerUi
import com.browntowndev.enclave.feature.chat.TranscriptSegmentUi
import com.browntowndev.enclave.feature.chat.components.thinkingindicator.ThinkingIndicator
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds

@Composable
internal fun LiveThinkingTranscriptRow(
    segment: TranscriptSegmentUi.ThinkingBlock,
    isPreview: Boolean,
    toolCallBanner: ToolCallBannerUi? = null,
    onToggleDetails: () -> Unit,
) {
    val startMs = segment.thinkingStartTimeMs ?: 0L
    var elapsedSeconds by remember(segment.ordinal) { mutableIntStateOf(0) }
    if ((startMs > 0L) && !isPreview) {
        LaunchedEffect(segment.ordinal, startMs) {
            while (true) {
                elapsedSeconds = ((System.currentTimeMillis() - startMs) / 1000).toInt()
                delay(1.seconds)
            }
        }
    }
    ThinkingIndicator(
        elapsedSeconds = elapsedSeconds,
        isExpanded = false,
        toolCallBanner = toolCallBanner,
        onToggleDetails = onToggleDetails,
    )
}
