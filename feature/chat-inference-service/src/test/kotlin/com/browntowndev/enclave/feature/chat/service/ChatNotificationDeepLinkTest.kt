package com.browntowndev.enclave.feature.chat.service

import com.browntowndev.enclave.domain.model.chat.ChatId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ChatNotificationDeepLinkTest {
    @Test
    fun `uriStringFor targets chat deep link with chat id path`() {
        val uri = ChatNotificationDeepLink.uriStringFor(ChatId("chat-123"))

        assertEquals("enclave://chat/chat-123", uri)
    }
}
