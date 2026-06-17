package com.browntowndev.enclave.feature.chat

import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.Content
import com.browntowndev.enclave.domain.model.chat.Message
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.model.chat.MessageState
import com.browntowndev.enclave.domain.model.chat.Role
import com.browntowndev.enclave.domain.model.inference.ModelType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class ChatMessageProjectorLiveVoiceOrderingTest {
    @Test
    @DisplayName("LV-PROJECT-001: assistant createdAt keeps live voice turns interleaved")
    fun lvProject001_assistantCreatedAtKeepsTurnsInterleaved() {
        val projector = ChatMessageProjector()
        val chatId = ChatId("c1")
        val messages =
            listOf(
                userMessage(chatId, "u1", createdAt = 100L),
                userMessage(chatId, "u2", createdAt = 300L),
                assistantMessage(chatId, "a1", createdAt = 101L),
                assistantMessage(chatId, "a2", createdAt = 301L),
            )

        val result = projector.project(messages, activeSnapshot = null, activeKey = null)

        assertEquals(listOf("u1", "a1", "u2", "a2"), result.messages.map { it.id.value })
    }

    private fun userMessage(
        chatId: ChatId,
        id: String,
        createdAt: Long,
    ): Message =
        Message(
            id = MessageId(id),
            role = Role.USER,
            chatId = chatId,
            messageState = MessageState.COMPLETE,
            content = Content(text = id),
            createdAt = createdAt,
        )

    private fun assistantMessage(
        chatId: ChatId,
        id: String,
        createdAt: Long?,
    ): Message =
        Message(
            id = MessageId(id),
            role = Role.ASSISTANT,
            chatId = chatId,
            messageState = MessageState.COMPLETE,
            modelType = ModelType.LIVE_VOICE,
            content = Content(text = id),
            createdAt = createdAt,
        )
}