package com.browntowndev.enclave.feature.chat.mapping

import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.Content
import com.browntowndev.enclave.domain.model.chat.Message
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.model.chat.MessageState
import com.browntowndev.enclave.domain.model.chat.MessageTranscriptSegment
import com.browntowndev.enclave.domain.model.chat.Role
import com.browntowndev.enclave.feature.chat.IndicatorState
import com.browntowndev.enclave.feature.chat.TranscriptSegmentUi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ChatMessageUiMapperTranscriptTest {
    private val mapper = ChatMessageUiMapper()
    private val displayNames = emptyMap<com.browntowndev.enclave.domain.model.inference.ModelType, String>()

    @Test
    fun `empty generating state maps to processing indicator`() {
        val message =
            assistantMessage(
                state = MessageState.GENERATING,
                transcript = emptyList(),
            )

        val ui = mapper.map(message, displayNames)

        assertTrue(ui.indicatorState is IndicatorState.Processing)
    }

    @Test
    fun `open thinking segment streams while message state is processing`() {
        val message =
            assistantMessage(
                state = MessageState.PROCESSING,
                transcript =
                    listOf(
                        MessageTranscriptSegment.Thinking(
                            ordinal = 0,
                            text = "planning",
                            thinkingStartTime = 1L,
                        ),
                    ),
            )

        val ui = mapper.map(message, displayNames)
        val block = ui.transcriptSegments.single() as TranscriptSegmentUi.ThinkingBlock

        assertTrue(block.isStreaming)
        assertTrue(ui.indicatorState is IndicatorState.Thinking)
    }

    @Test
    fun `closed thinking segment does not stream`() {
        val message =
            assistantMessage(
                state = MessageState.GENERATING,
                transcript =
                    listOf(
                        MessageTranscriptSegment.Thinking(
                            ordinal = 0,
                            text = "done",
                            thinkingStartTime = 1L,
                            thinkingEndTime = 2L,
                            thinkingDurationSeconds = 1L,
                        ),
                        MessageTranscriptSegment.AssistantText(ordinal = 1, text = "answer"),
                    ),
            )

        val ui = mapper.map(message, displayNames)
        val block = ui.transcriptSegments.first() as TranscriptSegmentUi.ThinkingBlock

        assertEquals(false, block.isStreaming)
    }

    private fun assistantMessage(
        state: MessageState,
        transcript: List<MessageTranscriptSegment>,
    ): Message =
        Message(
            id = MessageId("assistant"),
            chatId = ChatId("chat"),
            role = Role.ASSISTANT,
            content = Content(text = ""),
            messageState = state,
            transcriptSegments = transcript,
        )
}
