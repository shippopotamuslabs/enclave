package com.browntowndev.pocketcrew.core.data.repository

import com.browntowndev.pocketcrew.domain.model.chat.ChatId
import com.browntowndev.pocketcrew.domain.model.chat.MessageId
import com.browntowndev.pocketcrew.core.data.local.ChatSummaryDao
import com.browntowndev.pocketcrew.core.data.local.EmbeddingDao
import com.browntowndev.pocketcrew.core.data.local.MessageDao
import com.browntowndev.pocketcrew.core.data.local.MessageVisionAnalysisDao
import com.browntowndev.pocketcrew.domain.model.chat.Content
import com.browntowndev.pocketcrew.domain.model.chat.Message
import com.browntowndev.pocketcrew.domain.model.chat.Role
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import com.browntowndev.pocketcrew.core.data.sync.CloudSyncTrigger
import java.util.UUID

class MessageRepositoryImplSaveMessageTest {

    private lateinit var messageDao: MessageDao
    private lateinit var messageVisionAnalysisDao: MessageVisionAnalysisDao
    private lateinit var chatSummaryDao: ChatSummaryDao
    private lateinit var embeddingDao: EmbeddingDao
    private lateinit var repository: MessageRepositoryImpl
    private val syncTrigger = mockk<CloudSyncTrigger>(relaxed = true)

    @BeforeEach
    fun setup() {
        messageDao = mockk(relaxed = true)
        messageVisionAnalysisDao = mockk(relaxed = true)
        chatSummaryDao = mockk(relaxed = true)
        embeddingDao = mockk(relaxed = true)
        repository = MessageRepositoryImpl(
            syncTrigger,
            messageDao,
            messageVisionAnalysisDao,
            chatSummaryDao,
            embeddingDao,
        )
    }

    @Test
    fun `saveMessage with null chatId throws IllegalArgumentException`() = runTest {
        // Given - a message without a chatId (null)
        val message = Message(
            id = MessageId(UUID.randomUUID().toString()),
            chatId = null,
            content = Content(text = "Test message"),
            role = Role.USER
        )

        // When & Then - expect IllegalArgumentException before DAO insertion
        val exception = assertThrows<IllegalArgumentException> {
            repository.saveMessage(message)
        }

        // Verify the error message is informative
        assert(exception.message?.contains("must have a chatId before persisting") == true)
        assert(exception.message?.contains("CreateUserMessageUseCase") == true)

        // Verify DAO was never called
        coVerify(exactly = 0) { messageDao.upsert(any()) }
    }

    @Test
    fun `saveMessage with non-null chatId succeeds`() = runTest {
        // Given - a valid message with a chatId
        val messageId = MessageId(UUID.randomUUID().toString())
        val chatId = ChatId(UUID.randomUUID().toString())
        val message = Message(
            id = messageId,
            chatId = chatId,
            content = Content(text = "Test message"),
            role = Role.USER
        )

        coEvery { messageDao.upsert(any()) } returns 1L

        // When
        val result = repository.saveMessage(message)

        // Then
        assert(result == messageId)
        coVerify { messageDao.upsert(any()) }
    }
}
