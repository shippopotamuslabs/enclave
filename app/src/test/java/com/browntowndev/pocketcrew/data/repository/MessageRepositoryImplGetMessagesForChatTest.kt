package com.browntowndev.pocketcrew.data.repository

import com.browntowndev.pocketcrew.data.local.MessageDao
import com.browntowndev.pocketcrew.data.local.MessageEntity
import com.browntowndev.pocketcrew.domain.model.chat.Content
import com.browntowndev.pocketcrew.domain.model.chat.Message
import com.browntowndev.pocketcrew.domain.model.chat.Role
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class MessageRepositoryImplGetMessagesForChatTest {

    private lateinit var messageDao: MessageDao
    private lateinit var repository: MessageRepositoryImpl

    @BeforeEach
    fun setup() {
        messageDao = mockk(relaxed = true)
        repository = MessageRepositoryImpl(messageDao)
    }

    // ========== Basic Functionality Tests ==========

    @Test
    fun `getMessagesForChat returns messages for specific chat`() = runTest {
        // Given
        val chatId = 5L
        val entities = listOf(
            MessageEntity(id = 1, chatId = chatId, content = "Hello", role = Role.USER),
            MessageEntity(id = 2, chatId = chatId, content = "Hi there!", role = Role.ASSISTANT)
        )
        coEvery { messageDao.getMessagesByChatId(chatId) } returns entities

        // When
        val result = repository.getMessagesForChat(chatId)

        // Then
        assertEquals(2, result.size)
        assertEquals("Hello", result[0].content.text)
        assertEquals("Hi there!", result[1].content.text)
    }

    @Test
    fun `getMessagesForChat returns messages in chronological order by id ASC`() = runTest {
        // Given - the DAO query orders by id ASC, so we provide already sorted entities
        val chatId = 10L
        val entities = listOf(
            MessageEntity(id = 3, chatId = chatId, content = "Message 1", role = Role.USER),
            MessageEntity(id = 5, chatId = chatId, content = "Message 2", role = Role.ASSISTANT),
            MessageEntity(id = 8, chatId = chatId, content = "Message 3", role = Role.USER)
        )
        coEvery { messageDao.getMessagesByChatId(chatId) } returns entities

        // When
        val result = repository.getMessagesForChat(chatId)

        // Then - the repository preserves the order from DAO (which has ORDER BY id ASC)
        assertEquals(3, result.size)
        assertEquals(3L, result[0].id)
        assertEquals(5L, result[1].id)
        assertEquals(8L, result[2].id)
    }

    @Test
    fun `getMessagesForChat returns empty list for chat with no messages`() = runTest {
        // Given
        val chatId = 999L
        coEvery { messageDao.getMessagesByChatId(chatId) } returns emptyList()

        // When
        val result = repository.getMessagesForChat(chatId)

        // Then
        assertTrue(result.isEmpty())
    }

    // ========== Role Mapping Tests ==========

    @Test
    fun `getMessagesForChat maps USER role correctly`() = runTest {
        // Given
        val chatId = 1L
        val entity = MessageEntity(id = 1, chatId = chatId, content = "User message", role = Role.USER)
        coEvery { messageDao.getMessagesByChatId(chatId) } returns listOf(entity)

        // When
        val result = repository.getMessagesForChat(chatId)

        // Then
        assertEquals(Role.USER, result[0].role)
    }

    @Test
    fun `getMessagesForChat maps ASSISTANT role correctly`() = runTest {
        // Given
        val chatId = 1L
        val entity = MessageEntity(id = 2, chatId = chatId, content = "Assistant response", role = Role.ASSISTANT)
        coEvery { messageDao.getMessagesByChatId(chatId) } returns listOf(entity)

        // When
        val result = repository.getMessagesForChat(chatId)

        // Then
        assertEquals(Role.ASSISTANT, result[0].role)
    }

    @Test
    fun `getMessagesForChat maps SYSTEM role correctly`() = runTest {
        // Given
        val chatId = 1L
        val entity = MessageEntity(id = 3, chatId = chatId, content = "System message", role = Role.SYSTEM)
        coEvery { messageDao.getMessagesByChatId(chatId) } returns listOf(entity)

        // When
        val result = repository.getMessagesForChat(chatId)

        // Then
        assertEquals(Role.SYSTEM, result[0].role)
    }

    // ========== Content Preservation Tests ==========

    @Test
    fun `getMessagesForChat preserves message content exactly`() = runTest {
        // Given - content with special characters that could break parsing
        val chatId = 1L
        val specialContent = "Hello! \"Quotes\" & <special> chars\nnewlines\ttabs"
        val entity = MessageEntity(id = 1, chatId = chatId, content = specialContent, role = Role.USER)
        coEvery { messageDao.getMessagesByChatId(chatId) } returns listOf(entity)

        // When
        val result = repository.getMessagesForChat(chatId)

        // Then
        assertEquals(specialContent, result[0].content.text)
    }

    @Test
    fun `getMessagesForChat preserves empty content`() = runTest {
        // Given
        val chatId = 1L
        val entity = MessageEntity(id = 1, chatId = chatId, content = "", role = Role.ASSISTANT)
        coEvery { messageDao.getMessagesByChatId(chatId) } returns listOf(entity)

        // When
        val result = repository.getMessagesForChat(chatId)

        // Then
        assertEquals("", result[0].content.text)
    }

    // ========== Chat ID Tests ==========

    @Test
    fun `getMessagesForChat calls dao with correct chatId`() = runTest {
        // Given
        val chatId = 42L
        coEvery { messageDao.getMessagesByChatId(chatId) } returns emptyList()

        // When
        repository.getMessagesForChat(chatId)

        // Then
        coVerify { messageDao.getMessagesByChatId(42L) }
    }

    @Test
    fun `getMessagesForChat works with chatId of 1`() = runTest {
        // Given - edge case: minimum valid ID
        val chatId = 1L
        val entity = MessageEntity(id = 1, chatId = chatId, content = "First chat", role = Role.USER)
        coEvery { messageDao.getMessagesByChatId(chatId) } returns listOf(entity)

        // When
        val result = repository.getMessagesForChat(chatId)

        // Then
        assertEquals(1, result.size)
        assertEquals(chatId, result[0].chatId)
    }

    @Test
    fun `getMessagesForChat works with large chatId`() = runTest {
        // Given - edge case: large ID
        val chatId = Long.MAX_VALUE
        val entity = MessageEntity(id = 1, chatId = chatId, content = "Large ID chat", role = Role.USER)
        coEvery { messageDao.getMessagesByChatId(chatId) } returns listOf(entity)

        // When
        val result = repository.getMessagesForChat(chatId)

        // Then
        assertEquals(1, result.size)
        assertEquals(chatId, result[0].chatId)
    }

    // ========== Multi-turn Conversation Tests ==========

    @Test
    fun `getMessagesForChat returns full conversation history in order`() = runTest {
        // Given - simulate a multi-turn conversation
        val chatId = 1L
        val entities = listOf(
            MessageEntity(id = 1, chatId = chatId, content = "Hello", role = Role.USER),
            MessageEntity(id = 2, chatId = chatId, content = "Hi! How can I help?", role = Role.ASSISTANT),
            MessageEntity(id = 3, chatId = chatId, content = "Tell me a joke", role = Role.USER),
            MessageEntity(id = 4, chatId = chatId, content = "Why did the chicken cross the road?", role = Role.ASSISTANT),
            MessageEntity(id = 5, chatId = chatId, content = "I don't know, why?", role = Role.USER),
            MessageEntity(id = 6, chatId = chatId, content = "To get to the other side!", role = Role.ASSISTANT)
        )
        coEvery { messageDao.getMessagesByChatId(chatId) } returns entities

        // When
        val result = repository.getMessagesForChat(chatId)

        // Then - all 6 messages in order
        assertEquals(6, result.size)
        assertEquals("Hello", result[0].content.text)
        assertEquals("Hi! How can I help?", result[1].content.text)
        assertEquals("Tell me a joke", result[2].content.text)
        assertEquals("Why did the chicken cross the road?", result[3].content.text)
        assertEquals("I don't know, why?", result[4].content.text)
        assertEquals("To get to the other side!", result[5].content.text)

        // Verify alternating user/assistant pattern
        assertEquals(Role.USER, result[0].role)
        assertEquals(Role.ASSISTANT, result[1].role)
        assertEquals(Role.USER, result[2].role)
        assertEquals(Role.ASSISTANT, result[3].role)
        assertEquals(Role.USER, result[4].role)
        assertEquals(Role.ASSISTANT, result[5].role)
    }

    // ========== Edge Cases ==========

    @Test
    fun `getMessagesForChat handles single message chat`() = runTest {
        // Given
        val chatId = 1L
        val entity = MessageEntity(id = 1, chatId = chatId, content = "Only message", role = Role.USER)
        coEvery { messageDao.getMessagesByChatId(chatId) } returns listOf(entity)

        // When
        val result = repository.getMessagesForChat(chatId)

        // Then
        assertEquals(1, result.size)
        assertEquals("Only message", result[0].content.text)
    }

    @Test
    fun `getMessagesForChat handles messages with unicode content`() = runTest {
        // Given
        val chatId = 1L
        val unicodeContent = "Hello 世界 🌍 مرحبا"
        val entity = MessageEntity(id = 1, chatId = chatId, content = unicodeContent, role = Role.USER)
        coEvery { messageDao.getMessagesByChatId(chatId) } returns listOf(entity)

        // When
        val result = repository.getMessagesForChat(chatId)

        // Then
        assertEquals(unicodeContent, result[0].content.text)
    }
}
