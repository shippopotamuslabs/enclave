package com.browntowndev.enclave.feature.chat.mapping

import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.Content
import com.browntowndev.enclave.domain.model.chat.Message
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.model.chat.MessageState
import com.browntowndev.enclave.domain.model.chat.Role
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class ChatMessageUiMapperCacheTest {
    private val mapper = ChatMessageUiMapper()
    private val displayNames = emptyMap<com.browntowndev.enclave.domain.model.inference.ModelType, String>()

    @Test
    fun `returns same ChatMessage instance when domain message reference is unchanged`() {
        val message =
            Message(
                id = MessageId("m1"),
                chatId = ChatId("c1"),
                role = Role.USER,
                content = Content(text = "hello"),
                messageState = MessageState.COMPLETE,
            )

        val first = mapper.map(message, displayNames)
        val second = mapper.map(message, displayNames)

        assertSame(first, second)
    }

    @Test
    fun `remaps when domain message instance changes`() {
        val id = MessageId("m1")
        val firstMessage =
            Message(
                id = id,
                chatId = ChatId("c1"),
                role = Role.USER,
                content = Content(text = "hello"),
                messageState = MessageState.COMPLETE,
            )
        val secondMessage = firstMessage.copy(content = Content(text = "hello!"))

        val first = mapper.map(firstMessage, displayNames)
        val second = mapper.map(secondMessage, displayNames)

        assertNotSame(first, second)
    }

    @Test
    fun `clears cache when active chat changes`() {
        val message =
            Message(
                id = MessageId("m1"),
                chatId = ChatId("c1"),
                role = Role.USER,
                content = Content(text = "hello"),
                messageState = MessageState.COMPLETE,
            )

        val first = mapper.map(message, displayNames)
        mapper.onActiveChatChanged(ChatId("c2"))
        val second = mapper.map(message, displayNames)

        assertNotSame(first, second)
    }
}
