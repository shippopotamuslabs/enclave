package com.browntowndev.enclave.feature.chat.components.assistantresponse

import com.browntowndev.enclave.feature.chat.TranscriptSegmentUi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("toolCallBannerInsertAfterSegmentIndex")
class ToolCallBannerPlacementTest {
    @Test
    fun `insert after last leading streaming thinking block`() {
        val segments =
            listOf(
                TranscriptSegmentUi.ThinkingBlock(ordinal = 0, text = "a", durationSeconds = null, isStreaming = true),
                TranscriptSegmentUi.ThinkingBlock(ordinal = 1, text = "b", durationSeconds = null, isStreaming = true),
                TranscriptSegmentUi.AnswerBlock(ordinal = 2, text = "answer", isStreaming = false),
            )
        assertEquals(1, toolCallBannerInsertAfterSegmentIndex(segments))
    }

    @Test
    fun `insert after single streaming thinking block`() {
        val segments =
            listOf(
                TranscriptSegmentUi.ThinkingBlock(ordinal = 0, text = "t", durationSeconds = null, isStreaming = true),
                TranscriptSegmentUi.AnswerBlock(ordinal = 1, text = "x", isStreaming = true),
            )
        assertEquals(0, toolCallBannerInsertAfterSegmentIndex(segments))
    }

    @Test
    fun `insert after completed thought header when not streaming`() {
        val segments =
            listOf(
                TranscriptSegmentUi.ThinkingBlock(ordinal = 0, text = "t", durationSeconds = 8L, isStreaming = false),
                TranscriptSegmentUi.AnswerBlock(ordinal = 1, text = "x", isStreaming = false),
            )
        assertEquals(0, toolCallBannerInsertAfterSegmentIndex(segments))
    }

    @Test
    fun `insert after last thinking when answer precedes final thought`() {
        val segments =
            listOf(
                TranscriptSegmentUi.AnswerBlock(ordinal = 0, text = "hi", isStreaming = true),
                TranscriptSegmentUi.ThinkingBlock(ordinal = 1, text = "t", durationSeconds = null, isStreaming = false),
            )
        assertEquals(1, toolCallBannerInsertAfterSegmentIndex(segments))
    }

    @Test
    fun `insert after last thinking in interleaved transcript`() {
        val segments =
            listOf(
                TranscriptSegmentUi.ThinkingBlock(ordinal = 0, text = "t1", durationSeconds = 1L, isStreaming = false),
                TranscriptSegmentUi.AnswerBlock(ordinal = 1, text = "a1", isStreaming = false),
                TranscriptSegmentUi.ThinkingBlock(ordinal = 2, text = "t2", durationSeconds = 2L, isStreaming = false),
                TranscriptSegmentUi.AnswerBlock(ordinal = 3, text = "a2", isStreaming = false),
            )
        assertEquals(2, toolCallBannerInsertAfterSegmentIndex(segments))
    }

    @Test
    fun `null when transcript has no thinking rows`() {
        val segments =
            listOf(
                TranscriptSegmentUi.AnswerBlock(ordinal = 0, text = "only answer", isStreaming = false),
            )
        assertNull(toolCallBannerInsertAfterSegmentIndex(segments))
    }

    @Test
    fun `empty segments returns null`() {
        assertNull(toolCallBannerInsertAfterSegmentIndex(emptyList()))
    }
}
