package com.browntowndev.pocketcrew.inference.llama

import com.browntowndev.pocketcrew.domain.model.chat.Role
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

/**
 * Unit tests for ChatRole mapping from domain Role.
 */
class ChatRoleMappingTest {

    // ========== Role Mapping Tests ==========

    @Test
    fun `fromDomainRole maps USER correctly`() {
        // Given
        val domainRole = Role.USER

        // When
        val chatRole = ChatRole.fromDomainRole(domainRole)

        // Then
        assertEquals(ChatRole.USER, chatRole)
    }

    @Test
    fun `fromDomainRole maps ASSISTANT correctly`() {
        // Given
        val domainRole = Role.ASSISTANT

        // When
        val chatRole = ChatRole.fromDomainRole(domainRole)

        // Then
        assertEquals(ChatRole.ASSISTANT, chatRole)
    }

    @Test
    fun `fromDomainRole maps SYSTEM correctly`() {
        // Given
        val domainRole = Role.SYSTEM

        // When
        val chatRole = ChatRole.fromDomainRole(domainRole)

        // Then
        assertEquals(ChatRole.SYSTEM, chatRole)
    }

    // ========== All Domain Roles Covered ==========

    @Test
    fun `fromDomainRole covers all domain roles`() {
        // Given - all domain roles
        val allDomainRoles = Role.entries

        // When & Then - all should map without exception
        allDomainRoles.forEach { role ->
            val chatRole = ChatRole.fromDomainRole(role)
            assertNotNull(chatRole, "Should map $role")
        }
    }

    // ========== ChatMessage Creation Tests ==========

    @Test
    fun `ChatMessage creation with USER role`() {
        // Given
        val content = "User message"

        // When
        val message = ChatMessage(role = ChatRole.USER, content = content)

        // Then
        assertEquals(ChatRole.USER, message.role)
        assertEquals(content, message.content)
    }

    @Test
    fun `ChatMessage creation with ASSISTANT role`() {
        // Given
        val content = "Assistant response"

        // When
        val message = ChatMessage(role = ChatRole.ASSISTANT, content = content)

        // Then
        assertEquals(ChatRole.ASSISTANT, message.role)
        assertEquals(content, message.content)
    }

    @Test
    fun `ChatMessage creation with SYSTEM role`() {
        // Given
        val content = "System prompt"

        // When
        val message = ChatMessage(role = ChatRole.SYSTEM, content = content)

        // Then
        assertEquals(ChatRole.SYSTEM, message.role)
        assertEquals(content, message.content)
    }

    // ========== Content Preservation Tests ==========

    @Test
    fun `ChatMessage preserves special characters in content`() {
        // Given
        val specialContent = "Hello \"quoted\" & <special> chars\nnewlines"

        // When
        val message = ChatMessage(role = ChatRole.USER, content = specialContent)

        // Then
        assertEquals(specialContent, message.content)
    }

    @Test
    fun `ChatMessage preserves empty content`() {
        // Given
        val emptyContent = ""

        // When
        val message = ChatMessage(role = ChatRole.USER, content = emptyContent)

        // Then
        assertEquals(emptyContent, message.content)
    }

    @Test
    fun `ChatMessage preserves unicode content`() {
        // Given
        val unicodeContent = "Hello 世界 🌍 مرحبا"

        // When
        val message = ChatMessage(role = ChatRole.USER, content = unicodeContent)

        // Then
        assertEquals(unicodeContent, message.content)
    }

    @Test
    fun `ChatMessage preserves very long content`() {
        // Given - simulate a very long message
        val longContent = "A".repeat(10000)

        // When
        val message = ChatMessage(role = ChatRole.USER, content = longContent)

        // Then
        assertEquals(10000, message.content.length)
    }

    // ========== ChatRole Enum Tests ==========

    @Test
    fun `ChatRole has all expected values`() {
        // Then
        assertEquals(3, ChatRole.entries.size)
        assertTrue(ChatRole.entries.contains(ChatRole.SYSTEM))
        assertTrue(ChatRole.entries.contains(ChatRole.USER))
        assertTrue(ChatRole.entries.contains(ChatRole.ASSISTANT))
    }
}
