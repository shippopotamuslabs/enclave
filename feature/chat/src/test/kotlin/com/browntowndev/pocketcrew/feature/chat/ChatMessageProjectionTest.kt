package com.browntowndev.pocketcrew.feature.chat

import com.browntowndev.pocketcrew.domain.model.MessageState
import com.browntowndev.pocketcrew.domain.model.chat.AccumulatedMessages
import com.browntowndev.pocketcrew.domain.model.chat.ChatId
import com.browntowndev.pocketcrew.domain.model.chat.Content
import com.browntowndev.pocketcrew.domain.model.chat.Message
import com.browntowndev.pocketcrew.domain.model.chat.MessageId
import com.browntowndev.pocketcrew.domain.model.chat.MessageSnapshot
import com.browntowndev.pocketcrew.domain.model.chat.Role
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.port.inference.ActiveChatTurnKey
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ChatMessageProjectionTest {

    private val chatId = ChatId("chat")
    private val assistantMessageId = MessageId("assistant")

    @Test
    fun `projectChatMessages prefers snapshot when db row is partial`() {
        val result = projectChatMessages(
            dbMessages = listOf(
                assistantMessage(
                    content = "partial",
                    state = MessageState.GENERATING,
                ),
            ),
            activeSnapshot = snapshot(
                assistantMessageId = assistantMessageId,
                content = "streaming answer",
                state = MessageState.COMPLETE,
            ),
            activeKey = ActiveChatTurnKey(chatId, assistantMessageId),
        )

        assertEquals("streaming answer", result.messages.single().content.text)
        assertFalse(result.handoffReady)
    }

    @Test
    fun `projectChatMessages prefers Room when db row is complete even if snapshot is longer`() {
        val result = projectChatMessages(
            dbMessages = listOf(
                assistantMessage(
                    content = "short",
                    state = MessageState.COMPLETE,
                ),
            ),
            activeSnapshot = snapshot(
                assistantMessageId = assistantMessageId,
                content = "streaming answer",
                state = MessageState.COMPLETE,
            ),
            activeKey = ActiveChatTurnKey(chatId, assistantMessageId),
        )

        assertEquals("short", result.messages.single().content.text)
        assertTrue(result.handoffReady)
    }

    @Test
    fun `projectChatMessages prefers Room when db row matches snapshot content`() {
        val result = projectChatMessages(
            dbMessages = listOf(
                assistantMessage(
                    content = "final answer",
                    state = MessageState.COMPLETE,
                ),
            ),
            activeSnapshot = snapshot(
                assistantMessageId = assistantMessageId,
                content = "final answer",
                state = MessageState.COMPLETE,
            ),
            activeKey = ActiveChatTurnKey(chatId, assistantMessageId),
        )

        assertEquals("final answer", result.messages.single().content.text)
        assertTrue(result.handoffReady)
    }

    @Test
    fun `projectChatMessages prefers Room when db row is longer than snapshot content`() {
        val result = projectChatMessages(
            dbMessages = listOf(
                assistantMessage(
                    content = "final answer with extra room text",
                    state = MessageState.COMPLETE,
                ),
            ),
            activeSnapshot = snapshot(
                assistantMessageId = assistantMessageId,
                content = "final answer",
                state = MessageState.COMPLETE,
            ),
            activeKey = ActiveChatTurnKey(chatId, assistantMessageId),
        )

        assertEquals("final answer with extra room text", result.messages.single().content.text)
        assertTrue(result.handoffReady)
    }

    @Test
    fun `projectChatMessages prefers complete Room content even when snapshot text differs`() {
        val result = projectChatMessages(
            dbMessages = listOf(
                assistantMessage(
                    content = "final answer",
                    state = MessageState.COMPLETE,
                ),
            ),
            activeSnapshot = snapshot(
                assistantMessageId = assistantMessageId,
                content = "<tool_result>{raw}</tool_result> final answer plus transient trace",
                state = MessageState.COMPLETE,
            ),
            activeKey = ActiveChatTurnKey(chatId, assistantMessageId),
        )

        assertEquals("final answer", result.messages.single().content.text)
        assertTrue(result.handoffReady)
    }

    @Test
    fun `projectChatMessages requires all snapshot messages to be complete before handoff`() {
        val secondMessageId = MessageId("assistant-2")
        val result = projectChatMessages(
            dbMessages = listOf(
                assistantMessage(
                    id = assistantMessageId,
                    content = "first persisted answer",
                    state = MessageState.COMPLETE,
                ),
                assistantMessage(
                    id = secondMessageId,
                    content = "second partial",
                    state = MessageState.GENERATING,
                ),
            ),
            activeSnapshot = AccumulatedMessages(
                messages = mapOf(
                    assistantMessageId to messageSnapshot(
                        assistantMessageId = assistantMessageId,
                        content = "first streaming answer",
                        state = MessageState.COMPLETE,
                    ),
                    secondMessageId to messageSnapshot(
                        assistantMessageId = secondMessageId,
                        content = "second streaming answer",
                        state = MessageState.COMPLETE,
                    ),
                ),
            ),
            activeKey = ActiveChatTurnKey(chatId, assistantMessageId),
        )

        val projectedById = result.messages.associateBy { it.id }
        assertEquals("first persisted answer", projectedById.getValue(assistantMessageId).content.text)
        assertEquals("second streaming answer", projectedById.getValue(secondMessageId).content.text)
        assertFalse(result.handoffReady)
    }

    @Test
    fun `projectChatMessages projects Room when snapshot is null`() {
        val result = projectChatMessages(
            dbMessages = listOf(
                assistantMessage(
                    content = "final answer",
                    state = MessageState.COMPLETE,
                ),
            ),
            activeSnapshot = null,
            activeKey = null,
        )

        assertEquals("final answer", result.messages.single().content.text)
        assertFalse(result.handoffReady)
    }

    private fun assistantMessage(
        id: MessageId = assistantMessageId,
        content: String,
        state: MessageState,
    ): Message {
        return Message(
            id = id,
            chatId = chatId,
            content = Content(text = content),
            role = Role.ASSISTANT,
            messageState = state,
            createdAt = 1L,
        )
    }

    private fun snapshot(
        assistantMessageId: MessageId,
        content: String,
        state: MessageState,
    ): AccumulatedMessages {
        return AccumulatedMessages(
            messages = mapOf(
                assistantMessageId to messageSnapshot(
                    assistantMessageId = assistantMessageId,
                    content = content,
                    state = state,
                ),
            ),
        )
    }

    private fun messageSnapshot(
        assistantMessageId: MessageId,
        content: String,
        state: MessageState,
    ): MessageSnapshot {
        return MessageSnapshot(
            messageId = assistantMessageId,
            modelType = ModelType.FAST,
            content = content,
            thinkingRaw = "",
            messageState = state,
            isComplete = state == MessageState.COMPLETE,
        )
    }
}
