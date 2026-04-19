package com.browntowndev.pocketcrew.domain.model.inference

import com.browntowndev.pocketcrew.domain.model.chat.ChatId
import com.browntowndev.pocketcrew.domain.model.chat.MessageId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ToolExecutionEventTest {

    @Test
    fun `Extracting event holds url and context fields`() {
        val eventId = "evt-123"
        val url = "https://developer.android.com"
        val chatId = ChatId("chat-1")
        val userMessageId = MessageId("msg-1")

        val event = ToolExecutionEvent.Extracting(
            eventId = eventId,
            url = url,
            chatId = chatId,
            userMessageId = userMessageId,
        )

        assertEquals(eventId, event.eventId)
        assertEquals(url, event.url)
        assertEquals(chatId, event.chatId)
        assertEquals(userMessageId, event.userMessageId)
    }

    @Test
    fun `Extracting event is a subclass of ToolExecutionEvent`() {
        val event = ToolExecutionEvent.Extracting(
            eventId = "evt-456",
            url = "https://example.com",
            chatId = ChatId("chat-2"),
            userMessageId = MessageId("msg-2"),
        )

        assertTrue(event is ToolExecutionEvent, "Extracting should be a subclass of ToolExecutionEvent")
    }
}