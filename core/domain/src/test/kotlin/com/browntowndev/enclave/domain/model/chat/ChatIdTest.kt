package com.browntowndev.enclave.domain.model.chat

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ChatIdTest {
    // ========================================================================
    // Test: Blank ChatId throws IllegalArgumentException
    // Evidence: Invariant — ChatId must never be blank (prevents Firestore corruption)
    // ========================================================================

    @Test
    fun `ChatId with empty string throws IllegalArgumentException`() {
        assertThrows<IllegalArgumentException> {
            ChatId("")
        }
    }

    @Test
    fun `ChatId with blank string throws IllegalArgumentException`() {
        assertThrows<IllegalArgumentException> {
            ChatId("   ")
        }
    }

    @Test
    fun `ChatId with whitespace-only string throws IllegalArgumentException`() {
        assertThrows<IllegalArgumentException> {
            ChatId(" ")
        }
    }

    @Test
    fun `ChatId with valid UUID string succeeds`() {
        val id = ChatId("550e8400-e29b-41d4-a716-446655440000")
        assertEquals("550e8400-e29b-41d4-a716-446655440000", id.value)
    }

    @Test
    fun `ChatId with any non-blank string succeeds`() {
        val id = ChatId("some-chat-id")
        assertEquals("some-chat-id", id.value)
    }
}
