package com.browntowndev.pocketcrew.feature.chat.components.messagelist

import com.browntowndev.pocketcrew.feature.chat.IndicatorState

/**
 * When interleaved transcript rows render thinking, the top [ThinkingIndicator] is omitted.
 * Unit-tested via [TranscriptIndicatorDuplicatePolicyTest].
 */
internal fun transcriptLayoutShowsTopThinkingIndicator(
    indicatorState: IndicatorState?,
    suppressDuplicateThoughtForHeader: Boolean,
): Boolean = indicatorState is IndicatorState.Thinking && !suppressDuplicateThoughtForHeader

/**
 * Aggregate "Thought for …" header above the assistant body is omitted when transcript segments
 * already carry per-chunk headers. Unit-tested via [TranscriptIndicatorDuplicatePolicyTest].
 */
internal fun transcriptLayoutShowsAggregateThoughtForHeader(
    indicatorState: IndicatorState?,
    suppressDuplicateThoughtForHeader: Boolean,
): Boolean {
    if (indicatorState == null || suppressDuplicateThoughtForHeader) return false
    val raw =
        when (indicatorState) {
            is IndicatorState.Generating -> indicatorState.thinkingData?.thinkingRaw
            is IndicatorState.Complete -> indicatorState.thinkingData?.thinkingRaw
            else -> null
        }
    return !raw.isNullOrBlank()
}
