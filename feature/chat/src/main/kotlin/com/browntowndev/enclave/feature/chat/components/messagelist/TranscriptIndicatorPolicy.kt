package com.browntowndev.enclave.feature.chat.components.messagelist

import com.browntowndev.enclave.feature.chat.IndicatorState

/**
 * When interleaved transcript rows render thinking, the top thinking indicator is omitted.
 */
internal fun transcriptLayoutShowsTopThinkingIndicator(
    indicatorState: IndicatorState?,
    suppressDuplicateThoughtForHeader: Boolean,
): Boolean = (indicatorState is IndicatorState.Thinking) && !suppressDuplicateThoughtForHeader

/**
 * Engine loading / processing strips are fallbacks before transcript rows exist.
 * Once interleaved transcript segments are present, they own status UI.
 */
internal fun messageListShowsTopLevelStatusIndicator(
    indicatorState: IndicatorState?,
    hasTranscriptSegments: Boolean,
): Boolean {
    if (hasTranscriptSegments) return false
    return (indicatorState is IndicatorState.EngineLoading) || (indicatorState is IndicatorState.Processing)
}

/**
 * Once transcript rows exist, active processing/thinking status belongs after the latest transcript
 * segment so previous thinking and answer text do not move.
 */
internal fun transcriptLayoutShowsTrailingStatusIndicator(
    indicatorState: IndicatorState?,
    hasStreamingThinkingSegment: Boolean,
): Boolean =
    when (indicatorState) {
        is IndicatorState.Processing -> true
        is IndicatorState.Thinking -> !hasStreamingThinkingSegment
        else -> false
    }

/**
 * Active tool calls belong inside the live thinking row when one exists. Otherwise they stay
 * after the latest transcript segment so completed thought/answer text does not move.
 */
internal fun showsTrailingActiveToolCall(hasStreamingThinkingSegment: Boolean): Boolean = !hasStreamingThinkingSegment

/**
 * Aggregate "Thought for …" header above the assistant body is omitted when transcript segments
 * already carry per-chunk headers.
 */
internal fun transcriptLayoutShowsAggregateThoughtForHeader(
    indicatorState: IndicatorState?,
    suppressDuplicateThoughtForHeader: Boolean,
): Boolean {
    if ((indicatorState == null) || suppressDuplicateThoughtForHeader) return false
    val raw =
        when (indicatorState) {
            is IndicatorState.Generating -> indicatorState.thinkingData?.thinkingRaw
            is IndicatorState.Complete -> indicatorState.thinkingData?.thinkingRaw
            else -> null
        }
    return !raw.isNullOrBlank()
}
