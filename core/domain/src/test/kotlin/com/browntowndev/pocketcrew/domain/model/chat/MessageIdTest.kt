package com.browntowndev.pocketcrew.domain.model.chat

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class MessageIdTest {

    // ========================================================================
    // Test: Blank MessageId throws IllegalArgumentException
    // Evidence: Invariant — MessageId must never be blank (prevents Firestore corruption)
    // ========================================================================

    @Test
    fun `MessageId with empty string throws IllegalArgumentException`() {
        assertThrows<IllegalArgumentException> {
            MessageId("")
        }
    }

    @Test
    fun `MessageId with blank string throws IllegalArgumentException`() {
        assertThrows<IllegalArgumentException> {
            MessageId("   ")
        }
    }

    @Test
    fun `MessageId with whitespace-only string throws IllegalArgumentException`() {
        assertThrows<IllegalArgumentException> {
            MessageId(" ")
        }
    }

    @Test
    fun `MessageId with valid UUID string succeeds`() {
        val id = MessageId("550e8400-e29b-41d4-a716-446655440000")
        assertEquals("550e8400-e29b-41d4-a716-446655440000", id.value)
    }

    @Test
    fun `MessageId with any non-blank string succeeds`() {
        val id = MessageId("some-message-id")
        assertEquals("some-message-id", id.value)
    }
}
