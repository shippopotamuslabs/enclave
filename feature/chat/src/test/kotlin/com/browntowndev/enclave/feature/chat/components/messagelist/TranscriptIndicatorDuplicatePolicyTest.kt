package com.browntowndev.enclave.feature.chat.components.messagelist

import com.browntowndev.enclave.feature.chat.IndicatorState
import com.browntowndev.enclave.feature.chat.ThinkingDataUi
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("Transcript duplicate-indicator suppression policy")
class TranscriptIndicatorDuplicatePolicyTest {
    @Test
    fun `top thinking strip hidden when transcript segments drive thinking UI`() {
        assertFalse(
            transcriptLayoutShowsTopThinkingIndicator(
                IndicatorState.Thinking(
                    thinkingRaw = "x",
                    thinkingDurationSeconds = 5L,
                    thinkingStartTime = 1L,
                ),
                suppressDuplicateThoughtForHeader = true,
            ),
        )
    }

    @Test
    fun `top thinking strip shown when no transcript duplicate suppression`() {
        assertTrue(
            transcriptLayoutShowsTopThinkingIndicator(
                IndicatorState.Thinking(
                    thinkingRaw = "x",
                    thinkingDurationSeconds = 5L,
                    thinkingStartTime = 1L,
                ),
                suppressDuplicateThoughtForHeader = false,
            ),
        )
    }

    @Test
    fun `aggregate thought header hidden for complete when suppression on`() {
        assertFalse(
            transcriptLayoutShowsAggregateThoughtForHeader(
                IndicatorState.Complete(
                    thinkingData = ThinkingDataUi(10L, "full trace", 0L),
                ),
                suppressDuplicateThoughtForHeader = true,
            ),
        )
    }

    @Test
    fun `aggregate thought header shown for complete when suppression off`() {
        assertTrue(
            transcriptLayoutShowsAggregateThoughtForHeader(
                IndicatorState.Complete(
                    thinkingData = ThinkingDataUi(10L, "full trace", 0L),
                ),
                suppressDuplicateThoughtForHeader = false,
            ),
        )
    }

    @Test
    fun `aggregate thought header hidden for generating when suppression on`() {
        assertFalse(
            transcriptLayoutShowsAggregateThoughtForHeader(
                IndicatorState.Generating(
                    thinkingData = ThinkingDataUi(3L, "partial", 0L),
                ),
                suppressDuplicateThoughtForHeader = true,
            ),
        )
    }

    @Test
    fun `aggregate header not shown when thinking raw blank`() {
        assertFalse(
            transcriptLayoutShowsAggregateThoughtForHeader(
                IndicatorState.Complete(
                    thinkingData = ThinkingDataUi(1L, "   ", 0L),
                ),
                suppressDuplicateThoughtForHeader = false,
            ),
        )
    }

    @Test
    fun `processing strip hidden when transcript segments exist`() {
        assertFalse(
            messageListShowsTopLevelStatusIndicator(
                indicatorState = IndicatorState.Processing,
                hasTranscriptSegments = true,
            ),
        )
    }

    @Test
    fun `processing strip shown when transcript empty`() {
        assertTrue(
            messageListShowsTopLevelStatusIndicator(
                indicatorState = IndicatorState.Processing,
                hasTranscriptSegments = false,
            ),
        )
    }

    @Test
    fun `trailing processing strip shown when transcript segments exist`() {
        assertTrue(
            transcriptLayoutShowsTrailingStatusIndicator(
                indicatorState = IndicatorState.Processing,
                hasStreamingThinkingSegment = false,
            ),
        )
    }

    @Test
    fun `trailing thinking strip shown when no streaming thinking segment owns status`() {
        assertTrue(
            transcriptLayoutShowsTrailingStatusIndicator(
                indicatorState =
                    IndicatorState.Thinking(
                        thinkingRaw = "next thought",
                        thinkingDurationSeconds = 0L,
                    ),
                hasStreamingThinkingSegment = false,
            ),
        )
    }

    @Test
    fun `trailing thinking strip hidden when streaming thinking segment owns status`() {
        assertFalse(
            transcriptLayoutShowsTrailingStatusIndicator(
                indicatorState =
                    IndicatorState.Thinking(
                        thinkingRaw = "streaming thought",
                        thinkingDurationSeconds = 0L,
                    ),
                hasStreamingThinkingSegment = true,
            ),
        )
    }

    @Test
    fun `trailing active tool call hidden when streaming thinking segment owns status`() {
        assertFalse(
            showsTrailingActiveToolCall(
                hasStreamingThinkingSegment = true,
            ),
        )
    }

    @Test
    fun `trailing active tool call shown when no streaming thinking segment owns status`() {
        assertTrue(
            showsTrailingActiveToolCall(
                hasStreamingThinkingSegment = false,
            ),
        )
    }

    @Test
    fun `trailing status hidden for complete state`() {
        assertFalse(
            transcriptLayoutShowsTrailingStatusIndicator(
                indicatorState = IndicatorState.Complete(thinkingData = null),
                hasStreamingThinkingSegment = false,
            ),
        )
    }

    @Test
    fun `top thinking not shown for non thinking states`() {
        assertFalse(
            transcriptLayoutShowsTopThinkingIndicator(
                IndicatorState.Complete(thinkingData = null),
                suppressDuplicateThoughtForHeader = false,
            ),
        )
    }
}
