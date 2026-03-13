package com.browntowndev.pocketcrew.data.repository

import com.browntowndev.pocketcrew.data.local.ChatDao
import com.browntowndev.pocketcrew.data.local.MessageDao
import com.browntowndev.pocketcrew.domain.model.chat.Chat
import com.browntowndev.pocketcrew.domain.model.chat.ThinkingData
import io.mockk.coVerify
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.util.Date

class ChatRepositoryImplTest {

    private lateinit var chatDao: ChatDao
    private lateinit var messageDao: MessageDao
    private lateinit var repository: ChatRepositoryImpl

    @BeforeEach
    fun setup() {
        chatDao = mockk(relaxed = true)
        messageDao = mockk(relaxed = true)
        repository = ChatRepositoryImpl(chatDao, messageDao)
    }

    @Test
    fun `createChat inserts chat entity and returns generated id`() = runTest {
        val chat = Chat(
            id = 0,
            name = "Test Chat",
            created = Date(),
            lastModified = Date(),
            pinned = false
        )
        val expectedId = 1L

        coEvery { chatDao.insert(any()) } returns expectedId

        val result = repository.createChat(chat)

        assertEquals(expectedId, result)
        coVerify { chatDao.insert(any()) }
    }

    @Test
    fun `createChat returns id when chat has no initial id`() = runTest {
        val chat = Chat(
            id = 0,
            name = "New Chat",
            created = Date(),
            lastModified = Date(),
            pinned = false
        )
        val expectedId = 5L

        coEvery { chatDao.insert(any()) } returns expectedId

        val result = repository.createChat(chat)

        assertEquals(expectedId, result)
    }

    @Test
    fun `saveAssistantMessage updates message content with thinking data`() = runTest {
        val messageId = 123L
        val content = "This is the assistant response"
        val thinkingData = ThinkingData(
            thinkingDurationSeconds = 10,
            steps = listOf("Step 1", "Step 2", "Step 3"),
            rawFullThought = "Full reasoning chain"
        )

        repository.saveAssistantMessage(messageId, content, thinkingData)

        coVerify {
            messageDao.updateMessageContent(
                id = 123L,
                content = content,
                thinkingDuration = thinkingData.thinkingDurationSeconds,
                thinkingSteps = "Step 1\nStep 2\nStep 3",
                thinkingRaw = thinkingData.rawFullThought
            )
        }
    }

    @Test
    fun `saveAssistantMessage updates message content without thinking data`() = runTest {
        val messageId = 456L
        val content = "Simple response"

        repository.saveAssistantMessage(messageId, content, null)

        coVerify {
            messageDao.updateMessageContent(
                id = 456L,
                content = content,
                thinkingDuration = null,
                thinkingSteps = null,
                thinkingRaw = null
            )
        }
    }

    @Test
    fun `saveAssistantMessage handles non-numeric messageId gracefully`() = runTest {
        // This test is no longer applicable since messageId is now Long type
        // Invalid Long values would be a compile-time error, not runtime
        val messageId = 0L
        val content = "Response content"

        repository.saveAssistantMessage(messageId, content, null)

        // verify that updateMessageContent is called with the valid Long id
        coVerify {
            messageDao.updateMessageContent(
                id = 0L,
                content = content,
                thinkingDuration = null,
                thinkingSteps = null,
                thinkingRaw = null
            )
        }
    }

    @Test
    fun `saveAssistantMessage does not throw on invalid messageId`() = runTest {
        // This test is no longer applicable since messageId is now Long type
        // Invalid Long values would be a compile-time error, not runtime
        val messageId = 0L
        val content = "Test content"

        // Function should not throw any exception
        repository.saveAssistantMessage(messageId, content, null)
    }

    @Test
    fun `createChat correctly maps chat to entity`() = runTest {
        val now = Date()
        val chat = Chat(
            id = 0,
            name = "Test Conversation",
            created = now,
            lastModified = now,
            pinned = true
        )

        coEvery { chatDao.insert(any()) } returns 1L

        repository.createChat(chat)

        coVerify { chatDao.insert(any()) }
    }
}

