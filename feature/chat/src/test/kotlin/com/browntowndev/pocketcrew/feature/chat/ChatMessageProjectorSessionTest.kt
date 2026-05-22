package com.browntowndev.pocketcrew.feature.chat

import com.browntowndev.pocketcrew.domain.model.chat.ChatId
import com.browntowndev.pocketcrew.domain.model.chat.Content
import com.browntowndev.pocketcrew.domain.model.chat.Message
import com.browntowndev.pocketcrew.domain.model.chat.MessageId
import com.browntowndev.pocketcrew.domain.model.chat.MessageState
import com.browntowndev.pocketcrew.domain.model.chat.Role
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("ChatMessageProjector session clear")
class ChatMessageProjectorSessionTest {
    private val chatA = ChatId("chat-a")
    private val chatB = ChatId("chat-b")
    private val projector = ChatMessageProjector()

    @Test
    @DisplayName("SC-PERF-06 — projector clear wipes memo so identical inputs recompute")
    fun scPerf06_clearWipesMemoOnChatChange() {
        val messages = listOf(dbMessage(MessageId("msg-1"), chatA))

        val r1 = projector.project(messages, null, null)
        projector.onActiveChatChanged(chatB)
        val r2 = projector.project(messages, null, null)

        assertNotSame(r1.messages, r2.messages)
    }

    @Test
    @DisplayName("SC-PERF-07 — onActiveChatChanged with same id is no-op")
    fun scPerf07_sameChatIdIsNoOp() {
        val messages = listOf(dbMessage(MessageId("msg-1"), chatA))

        projector.onActiveChatChanged(chatA)
        val first = projector.project(messages, null, null).messages
        val second = projector.project(messages, null, null).messages
        projector.onActiveChatChanged(chatA)
        val third = projector.project(messages, null, null).messages

        assertSame(first, second)
        assertSame(second, third)
    }

    private fun dbMessage(
        id: MessageId,
        chatId: ChatId,
    ) = Message(
        id = id,
        chatId = chatId,
        content = Content("hello"),
        role = Role.ASSISTANT,
        messageState = MessageState.COMPLETE,
        createdAt = 1L,
    )
}
