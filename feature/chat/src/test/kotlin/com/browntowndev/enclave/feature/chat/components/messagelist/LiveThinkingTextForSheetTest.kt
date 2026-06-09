package com.browntowndev.enclave.feature.chat.components.messagelist

import com.browntowndev.enclave.feature.chat.IndicatorState
import com.browntowndev.enclave.feature.chat.TranscriptSegmentUi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LiveThinkingTextForSheetTest {
    @Test
    fun `prefers streaming transcript segment over indicator aggregate`() {
        val text =
            liveThinkingTextForSheet(
                transcriptSegments =
                    listOf(
                        TranscriptSegmentUi.ThinkingBlock(
                            ordinal = 0,
                            text = "live trace",
                            durationSeconds = null,
                            thinkingStartTimeMs = 1L,
                            isStreaming = true,
                        ),
                    ),
                indicatorState =
                    IndicatorState.Thinking(
                        thinkingRaw = "stale aggregate",
                        thinkingDurationSeconds = 0L,
                        thinkingStartTime = 1L,
                    ),
            )

        assertEquals("live trace", text)
    }

    @Test
    fun `falls back to indicator thinking raw when no streaming segment`() {
        val text =
            liveThinkingTextForSheet(
                transcriptSegments =
                    listOf(
                        TranscriptSegmentUi.ThinkingBlock(
                            ordinal = 0,
                            text = "completed trace",
                            durationSeconds = 3L,
                            thinkingStartTimeMs = 1L,
                            isStreaming = false,
                        ),
                    ),
                indicatorState =
                    IndicatorState.Complete(
                        thinkingData =
                            com.browntowndev.enclave.feature.chat.ThinkingDataUi(
                                thinkingDurationSeconds = 3L,
                                thinkingRaw = "completed aggregate",
                                thinkingStartTime = 1L,
                            ),
                    ),
            )

        assertEquals("completed trace", text)
    }

    @Test
    fun `joins completed thinking transcript segments for detail sheet`() {
        val text =
            liveThinkingTextForSheet(
                transcriptSegments =
                    listOf(
                        TranscriptSegmentUi.ThinkingBlock(
                            ordinal = 0,
                            text = "wave one",
                            durationSeconds = 2L,
                            thinkingStartTimeMs = 1L,
                            isStreaming = false,
                        ),
                        TranscriptSegmentUi.AnswerBlock(
                            ordinal = 1,
                            text = "answer",
                            isStreaming = false,
                        ),
                        TranscriptSegmentUi.ThinkingBlock(
                            ordinal = 2,
                            text = "wave two",
                            durationSeconds = 3L,
                            thinkingStartTimeMs = 4L,
                            isStreaming = false,
                        ),
                    ),
                indicatorState =
                    IndicatorState.Complete(
                        thinkingData =
                            com.browntowndev.enclave.feature.chat.ThinkingDataUi(
                                thinkingDurationSeconds = 5L,
                                thinkingRaw = "wave onewave two",
                                thinkingStartTime = 1L,
                            ),
                    ),
            )

        assertEquals("wave one\n\nwave two", text)
    }
}
