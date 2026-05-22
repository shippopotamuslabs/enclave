package com.browntowndev.enclave.feature.chat.components.messagelist

import com.browntowndev.enclave.feature.chat.ChatMessage
import com.browntowndev.enclave.feature.chat.MessageRole
import com.browntowndev.enclave.feature.chat.ToolCallBannerKind
import com.browntowndev.enclave.feature.chat.ToolCallBannerUi
import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.feature.chat.ContentUi
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("Tool call banner row helpers")
class ToolCallBannerRowHelpersTest {
    @Test
    fun `inline transcript assistant messages skip standalone banner row`() {
        val message =
            assistantMessage(
                transcriptSegments =
                    listOf(
                        com.browntowndev.enclave.feature.chat.TranscriptSegmentUi.AnswerBlock(
                            ordinal = 0,
                            text = "answer",
                            isStreaming = false,
                        ),
                    ),
            )
        assertTrue(usesInlineToolCallBannerInTranscript(message))
    }

    @Test
    fun `user messages never use inline transcript banner`() {
        assertFalse(
            usesInlineToolCallBannerInTranscript(
                ChatMessage(
                    id = MessageId("1"),
                    chatId = ChatId("1"),
                    role = MessageRole.User,
                    content = ContentUi(text = "hi"),
                    formattedTimestamp = "10:00",
                ),
            ),
        )
    }

    @Test
    fun `resolve active banner only for matching indicator message id`() {
        val banner = ToolCallBannerUi(kind = ToolCallBannerKind.SEARCH, label = "searching")
        val message = assistantMessage()
        assertSame(
            banner,
            resolveActiveToolCallBanner(message, MessageId("msg"), banner),
        )
        assertNull(resolveActiveToolCallBanner(message, MessageId("other"), banner))
    }

    private fun assistantMessage(
        transcriptSegments: List<com.browntowndev.enclave.feature.chat.TranscriptSegmentUi> = emptyList(),
    ) = ChatMessage(
        id = MessageId("msg"),
        chatId = ChatId("1"),
        role = MessageRole.Assistant,
        content = ContentUi(text = ""),
        formattedTimestamp = "10:00",
        transcriptSegments = transcriptSegments,
    )
}
