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
 * Engine loading / processing strips are fallbacks before transcript rows exist.
 * Once interleaved transcript segments are present, they own status UI.
 */
internal fun messageListShowsTopLevelStatusIndicator(
    indicatorState: IndicatorState?,
    hasTranscriptSegments: Boolean,
): Boolean {
    if (hasTranscriptSegments) return false
    return indicatorState is IndicatorState.EngineLoading || indicatorState is IndicatorState.Processing
}

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
