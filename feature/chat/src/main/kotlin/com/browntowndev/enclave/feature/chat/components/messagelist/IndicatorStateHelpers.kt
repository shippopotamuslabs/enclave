package com.browntowndev.enclave.feature.chat.components.messagelist

import com.browntowndev.enclave.feature.chat.IndicatorState
import com.browntowndev.enclave.feature.chat.TranscriptSegmentUi

internal fun indicatorThinkingStartTime(indicatorState: IndicatorState): Long =
    when (indicatorState) {
        is IndicatorState.Thinking -> indicatorState.thinkingStartTime
        is IndicatorState.Generating -> indicatorState.thinkingData?.thinkingStartTime ?: 0L
        is IndicatorState.Complete -> indicatorState.thinkingData?.thinkingStartTime ?: 0L
        else -> 0L
    }

internal fun isIndicatorThinkingActive(indicatorState: IndicatorState): Boolean =
    indicatorState is IndicatorState.Thinking ||
        indicatorState is IndicatorState.EngineLoading ||
        (
            indicatorState is IndicatorState.Generating &&
                indicatorState.thinkingData?.thinkingDurationSeconds == 0L
        )

internal fun settledIndicatorElapsedSeconds(indicatorState: IndicatorState): Int =
    when (indicatorState) {
        is IndicatorState.Thinking -> indicatorState.thinkingDurationSeconds.toInt()
        is IndicatorState.EngineLoading -> 0
        is IndicatorState.Generating -> indicatorState.thinkingData?.thinkingDurationSeconds?.toInt() ?: 0
        is IndicatorState.Complete -> indicatorState.thinkingData?.thinkingDurationSeconds?.toInt() ?: 0
        else -> 0
    }

internal fun indicatorThinkingRawForSheet(indicatorState: IndicatorState): String =
    when (indicatorState) {
        is IndicatorState.Thinking -> indicatorState.thinkingRaw
        is IndicatorState.Generating -> indicatorState.thinkingData?.thinkingRaw ?: ""
        is IndicatorState.Complete -> indicatorState.thinkingData?.thinkingRaw ?: ""
        else -> ""
    }

internal fun liveThinkingTextForSheet(
    transcriptSegments: List<TranscriptSegmentUi>,
    indicatorState: IndicatorState?,
): String {
    val thinkingSegments = transcriptSegments.filterIsInstance<TranscriptSegmentUi.ThinkingBlock>()
    val streamingSegment = thinkingSegments.lastOrNull { it.isStreaming }
    if (streamingSegment != null) {
        return streamingSegment.text
    }
    if (thinkingSegments.isNotEmpty()) {
        return thinkingSegments.joinToString("\n\n") { it.text }
    }
    return indicatorState?.let(::indicatorThinkingRawForSheet).orEmpty()
}

internal fun indicatorThinkingDurationForSheet(indicatorState: IndicatorState): Long =
    when (indicatorState) {
        is IndicatorState.Thinking -> indicatorState.thinkingDurationSeconds
        is IndicatorState.Generating -> indicatorState.thinkingData?.thinkingDurationSeconds ?: 0
        is IndicatorState.Complete -> indicatorState.thinkingData?.thinkingDurationSeconds ?: 0
        else -> 0
    }

internal fun isIndicatorStreaming(indicatorState: IndicatorState): Boolean =
    indicatorState !is IndicatorState.Complete && indicatorState !is IndicatorState.None
