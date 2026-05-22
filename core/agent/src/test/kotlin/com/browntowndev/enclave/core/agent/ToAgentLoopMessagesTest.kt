package com.browntowndev.enclave.core.agent

import com.browntowndev.enclave.domain.model.chat.ChatMessage
import com.browntowndev.enclave.domain.model.chat.Role
import com.browntowndev.enclave.domain.model.inference.toAgentLoopMessages
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ToAgentLoopMessagesTest {
    @Test
    fun `maps chat messages to stable ids`() {
        val list =
            listOf(
                ChatMessage(Role.SYSTEM, "s"),
                ChatMessage(Role.USER, "u"),
            )
        val mapped = list.toAgentLoopMessages()
        assertEquals("chat-0", mapped[0].id)
        assertEquals(Role.SYSTEM, mapped[0].role)
        assertEquals("chat-1", mapped[1].id)
        assertEquals("u", mapped[1].content)
    }
}
