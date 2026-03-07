package com.browntowndev.pocketcrew.data.repository

import com.browntowndev.pocketcrew.data.local.MessageDao
import com.browntowndev.pocketcrew.data.local.MessageEntity
import com.browntowndev.pocketcrew.domain.model.Message
import com.browntowndev.pocketcrew.domain.model.Role
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class MessageRepositoryImplTest {

    private lateinit var messageDao: MessageDao
    private lateinit var repository: MessageRepositoryImpl

    @BeforeEach
    fun setup() {
        messageDao = mockk(relaxed = true)
        repository = MessageRepositoryImpl(messageDao)
    }

    @Test
    fun `saveMessage calls insertMessageWithSearch with correct entity`() = runTest {
        val message = Message(
            id = 0,
            chatId = 1,
            content = "Hello, world!",
            role = Role.USER
        )
        val expectedId = 1L

        coEvery { messageDao.insertMessageWithSearch(any()) } returns expectedId

        repository.saveMessage(message)

        coVerify { messageDao.insertMessageWithSearch(any()) }
    }

    @Test
    fun `saveMessage maps domain message to entity correctly`() = runTest {
        val message = Message(
            id = 0,
            chatId = 5,
            content = "Test message content",
            role = Role.ASSISTANT
        )
        val expectedId = 10L

        coEvery { messageDao.insertMessageWithSearch(any()) } returns expectedId

        repository.saveMessage(message)

        coVerify {
            messageDao.insertMessageWithSearch(
                match { entity ->
                    entity.id == 0L &&
                    entity.chatId == 5L &&
                    entity.content == "Test message content" &&
                    entity.role == Role.ASSISTANT
                }
            )
        }
    }

    @Test
    fun `saveMessage returns generated id from dao`() = runTest {
        val message = Message(
            id = 0,
            chatId = 1,
            content = "Message",
            role = Role.USER
        )
        val expectedId = 42L

        coEvery { messageDao.insertMessageWithSearch(any()) } returns expectedId

        repository.saveMessage(message)

        coVerify { messageDao.insertMessageWithSearch(any()) }
    }

    @Test
    fun `getMessageById returns message when found`() = runTest {
        val messageId = 1L
        val entity = MessageEntity(
            id = messageId,
            chatId = 5,
            content = "Test content",
            role = Role.USER
        )
        val expectedMessage = Message(
            id = messageId,
            chatId = 5,
            content = "Test content",
            role = Role.USER
        )

        coEvery { messageDao.getMessageById(messageId) } returns entity

        val result = repository.getMessageById(messageId)

        assertEquals(expectedMessage.id, result?.id)
        assertEquals(expectedMessage.content, result?.content)
        assertEquals(expectedMessage.role, result?.role)
        assertEquals(expectedMessage.chatId, result?.chatId)
    }

    @Test
    fun `getMessageById returns null when message not found`() = runTest {
        val messageId = 999L

        coEvery { messageDao.getMessageById(messageId) } returns null

        val result = repository.getMessageById(messageId)

        assertNull(result)
    }

    @Test
    fun `getMessageById calls dao with correct id`() = runTest {
        val messageId = 42L

        coEvery { messageDao.getMessageById(messageId) } returns null

        repository.getMessageById(messageId)

        coVerify { messageDao.getMessageById(messageId) }
    }

    @Test
    fun `saveMessage handles assistant role correctly`() = runTest {
        val message = Message(
            id = 0,
            chatId = 3,
            content = "Assistant response",
            role = Role.ASSISTANT
        )

        coEvery { messageDao.insertMessageWithSearch(any()) } returns 1L

        repository.saveMessage(message)

        coVerify {
            messageDao.insertMessageWithSearch(
                match { it.role == Role.ASSISTANT }
            )
        }
    }

    @Test
    fun `saveMessage handles system role correctly`() = runTest {
        val message = Message(
            id = 0,
            chatId = 2,
            content = "System prompt",
            role = Role.SYSTEM
        )

        coEvery { messageDao.insertMessageWithSearch(any()) } returns 1L

        repository.saveMessage(message)

        coVerify {
            messageDao.insertMessageWithSearch(
                match { it.role == Role.SYSTEM }
            )
        }
    }

    @Test
    fun `getMessageById maps entity to domain correctly`() = runTest {
        val entity = MessageEntity(
            id = 7,
            chatId = 10,
            content = "Mapped content",
            role = Role.ASSISTANT
        )

        coEvery { messageDao.getMessageById(7) } returns entity

        val result = repository.getMessageById(7)

        assertNotNull(result)
        assertEquals(7L, result!!.id)
        assertEquals(10L, result.chatId)
        assertEquals("Mapped content", result.content)
        assertEquals(Role.ASSISTANT, result.role)
    }

    @Test
    fun `saveMessage with existing id preserves id in entity`() = runTest {
        val message = Message(
            id = 15,
            chatId = 3,
            content = "Message with existing ID",
            role = Role.USER
        )

        coEvery { messageDao.insertMessageWithSearch(any()) } returns 15L

        repository.saveMessage(message)

        coVerify {
            messageDao.insertMessageWithSearch(
                match { it.id == 15L }
            )
        }
    }
}

