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
        val messageId = "123"
        val content = "This is the assistant response"
        val thinkingData = ThinkingData(
            durationSeconds = 10,
            steps = listOf("Step 1", "Step 2", "Step 3"),
            rawFullThought = "Full reasoning chain"
        )

        repository.saveAssistantMessage(messageId, content, thinkingData)

        coVerify {
            messageDao.updateMessageContent(
                id = 123L,
                content = content,
                thinkingDuration = thinkingData.durationSeconds,
                thinkingSteps = "Step 1\nStep 2\nStep 3",
                thinkingRaw = thinkingData.rawFullThought
            )
        }
    }

    @Test
    fun `saveAssistantMessage updates message content without thinking data`() = runTest {
        val messageId = "456"
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
        val messageId = "not-a-number"
        val content = "Response content"

        // When messageId cannot be converted to Long, the function returns early
        repository.saveAssistantMessage(messageId, content, null)

        // verify that updateMessageContent is NOT called for invalid messageId
        coVerify(exactly = 0) { messageDao.updateMessageContent(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `saveAssistantMessage does not throw on invalid messageId`() = runTest {
        val invalidMessageId = "abc123"
        val content = "Test content"

        // Function should return early without throwing when messageId is invalid
        // This should not throw any exception
        repository.saveAssistantMessage(invalidMessageId, content, null)
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

