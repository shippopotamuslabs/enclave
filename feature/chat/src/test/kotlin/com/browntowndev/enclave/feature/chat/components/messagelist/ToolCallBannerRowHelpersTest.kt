package com.browntowndev.enclave.feature.chat.components.messagelist

import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.feature.chat.ChatMessage
import com.browntowndev.enclave.feature.chat.ContentUi
import com.browntowndev.enclave.feature.chat.MessageRole
import com.browntowndev.enclave.feature.chat.ToolCallBannerKind
import com.browntowndev.enclave.feature.chat.ToolCallBannerUi
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("Active tool call banner helpers")
class ToolCallBannerRowHelpersTest {
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

    @Test
    fun `resolve active banner ignores matching user messages`() {
        val banner = ToolCallBannerUi(kind = ToolCallBannerKind.SEARCH, label = "searching")
        val message =
            ChatMessage(
                id = MessageId("msg"),
                chatId = ChatId("1"),
                role = MessageRole.User,
                content = ContentUi(text = "hi"),
                formattedTimestamp = "10:00",
            )

        assertNull(resolveActiveToolCallBanner(message, MessageId("msg"), banner))
    }

    private fun assistantMessage() =
        ChatMessage(
            id = MessageId("msg"),
            chatId = ChatId("1"),
            role = MessageRole.Assistant,
            content = ContentUi(text = ""),
            formattedTimestamp = "10:00",
        )
}
