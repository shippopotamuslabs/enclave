package com.browntowndev.pocketcrew.core.data.repository

import com.browntowndev.pocketcrew.core.data.local.ChatDao
import com.browntowndev.pocketcrew.core.data.local.ChatEntity
import com.browntowndev.pocketcrew.core.data.local.MessageDao
import com.browntowndev.pocketcrew.core.data.local.MessageEntity
import com.browntowndev.pocketcrew.core.data.local.MessageWithFiles
import com.browntowndev.pocketcrew.core.data.local.WebSearchSourceDao
import com.browntowndev.pocketcrew.core.data.local.WebSearchSourceEntity
import com.browntowndev.pocketcrew.domain.model.chat.MessageState
import com.browntowndev.pocketcrew.domain.model.chat.ChatId
import com.browntowndev.pocketcrew.domain.model.chat.MessageId
import com.browntowndev.pocketcrew.domain.model.chat.Role
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.Date
import android.content.Context
import androidx.work.WorkManager
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.After

class ChatRepositoryImplTest {

    private lateinit var chatDao: ChatDao
    private lateinit var messageDao: MessageDao
    private lateinit var tavilySourceDao: WebSearchSourceDao
    private lateinit var repository: ChatRepositoryImpl
    private val workManager = mockk<WorkManager>(relaxed = true)

    private val testDate = Date(1700_000_000_000L)

    @BeforeEach
    fun setup() {
        chatDao = mockk<ChatDao>(relaxed = true)
        messageDao = mockk<MessageDao>(relaxed = true)
        tavilySourceDao = mockk<WebSearchSourceDao>(relaxed = true)
        repository = ChatRepositoryImpl(workManager, chatDao, messageDao, tavilySourceDao)
    }

    @After
    fun tearDown() {
        // No longer need mockkStatic(WorkManager::class)
    }

    @Test
    fun `D1 maps Entity to Domain correctly`() = runTest {
        val entity = ChatEntity(
            id = ChatId("1"),
            name = "Test",
            created = testDate,
            lastModified = testDate,
            pinned = true
        )
        // Correctly use every for Flow return type
        every { chatDao.getAllChats() } returns flowOf(listOf(entity))

        val result = repository.getAllChats().first()

        assertEquals(1, result.size)
        val chat = result.first()
        assertEquals(ChatId("1"), chat.id)
        assertEquals("Test", chat.name)
        assertTrue(chat.pinned)
        assertEquals(testDate, chat.created)
        assertEquals(testDate, chat.lastModified)
    }

    @Test
    fun `D2 togglePinStatus updates database from false to true`() = runTest {
        val entity = ChatEntity(
            id = ChatId("5"),
            name = "Test Chat",
            created = testDate,
            lastModified = testDate,
            pinned = false
        )
        coEvery { chatDao.getChatById(ChatId("5")) } returns entity
        coEvery { chatDao.updatePinStatus(ChatId("5")) } returns 1

        repository.togglePinStatus(ChatId("5"))

        coVerify { chatDao.updatePinStatus(ChatId("5")) }
    }

    @Test
    fun `D3 togglePinStatus toggles from true to false`() = runTest {
        val entity = ChatEntity(
            id = ChatId("10"),
            name = "Pinned Chat",
            created = testDate,
            lastModified = testDate,
            pinned = true
        )
        coEvery { chatDao.getChatById(ChatId("10")) } returns entity
        coEvery { chatDao.updatePinStatus(ChatId("10")) } returns 1

        repository.togglePinStatus(ChatId("10"))

        coVerify { chatDao.updatePinStatus(ChatId("10")) }
    }

    @Test
    fun `D4 getMessagesForChat combines messages with tavily sources`() = runTest {
        val chatId = ChatId("chat-1")
        val messageId = MessageId("msg-1")
        val messageEntity = MessageEntity(
            id = messageId,
            chatId = chatId,
            content = "Hello",
            role = Role.ASSISTANT,
            userMessageId = MessageId("user-1"),
            messageState = MessageState.COMPLETE,
            modelType = ModelType.FAST,
        )
        val sourceEntity = WebSearchSourceEntity(
            id = "src-1",
            messageId = messageId,
            title = "Test Source",
            url = "https://example.com",
            content = "Test content",
            score = 0.9,
            extracted = true,
        )

        every { messageDao.getMessagesByChatIdFlow(chatId) } returns flowOf(
            listOf(MessageWithFiles(message = messageEntity, files = emptyList()))
        )
        every { tavilySourceDao.getByChatIdFlow(chatId) } returns flowOf(listOf(sourceEntity))

        val result = repository.getMessagesForChat(chatId).first()

        assertEquals(1, result.size)
        assertEquals(1, result[0].webSearchSources.size)
        assertEquals("https://example.com", result[0].webSearchSources[0].url)
        assertTrue(result[0].webSearchSources[0].extracted,
            "Extracted source should have extracted=true from tavily_source table")
    }
}
