package com.browntowndev.enclave.core.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.browntowndev.enclave.core.data.local.ChatDao
import com.browntowndev.enclave.core.data.local.ChatEntity
import com.browntowndev.enclave.core.data.local.EnclaveDatabase
import com.browntowndev.enclave.core.data.local.MessageEntity
import com.browntowndev.enclave.core.data.local.WebSearchSourceEntity
import com.browntowndev.enclave.core.data.local.dao.message.MessageDao
import com.browntowndev.enclave.core.data.local.messageDao
import com.browntowndev.enclave.domain.model.chat.Chat
import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.model.chat.MessageState
import com.browntowndev.enclave.domain.model.chat.Role
import com.browntowndev.enclave.domain.model.inference.ModelType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension
import java.util.Date

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [34])
class ChatRepositoryImplTest {
    private lateinit var database: EnclaveDatabase
    private lateinit var chatDao: ChatDao
    private lateinit var messageDao: MessageDao
    private lateinit var repository: ChatRepositoryImpl

    private val testDate = Date(1700_000_000_000L)

    @BeforeEach
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database =
            Room
                .inMemoryDatabaseBuilder(context, EnclaveDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        chatDao = database.chatDao()
        messageDao = database.messageDao()
        repository = database.chatRepositoryImpl(RecordingCloudSyncTrigger())
    }

    @AfterEach
    fun tearDown() {
        database.close()
    }

    @Test
    fun `D1 maps Entity to Domain correctly`() =
        runTest {
            val entity =
                ChatEntity(
                    id = ChatId("1"),
                    name = "Test",
                    created = testDate,
                    lastModified = testDate,
                    pinned = true,
                )
            chatDao.upsert(entity)

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
    fun `D2 togglePinStatus updates database from false to true`() =
        runTest {
            val entity =
                ChatEntity(
                    id = ChatId("5"),
                    name = "Test Chat",
                    created = testDate,
                    lastModified = testDate,
                    pinned = false,
                )
            chatDao.upsert(entity)

            repository.togglePinStatus(ChatId("5"))

            assertTrue(chatDao.getChatById(ChatId("5"))!!.pinned)
        }

    @Test
    fun `D3 togglePinStatus toggles from true to false`() =
        runTest {
            val entity =
                ChatEntity(
                    id = ChatId("10"),
                    name = "Pinned Chat",
                    created = testDate,
                    lastModified = testDate,
                    pinned = true,
                )
            chatDao.upsert(entity)

            repository.togglePinStatus(ChatId("10"))

            assertFalse(chatDao.getChatById(ChatId("10"))!!.pinned)
        }

    @Test
    fun `D4 getMessagesForChat combines messages with tavily sources`() =
        runTest {
            val chatId = ChatId("chat-1")
            val messageId = MessageId("msg-1")
            chatDao.upsert(
                ChatEntity(
                    id = chatId,
                    name = "Chat",
                    created = testDate,
                    lastModified = testDate,
                    pinned = false,
                ),
            )
            // MessageEntity.user_message_id is a self-FK to message.id, so the parent
            // user message must exist before the assistant reply can be upserted
            // (against the MockK DAOs this FK was invisible and the upsert threw a
            // SQLiteConstraintException once translated to real Room).
            messageDao.upsert(
                MessageEntity(
                    id = MessageId("user-1"),
                    chatId = chatId,
                    content = "Question",
                    role = Role.USER,
                    messageState = MessageState.COMPLETE,
                    createdAt = 1000L,
                ),
            )
            val messageEntity =
                MessageEntity(
                    id = messageId,
                    chatId = chatId,
                    content = "Hello",
                    role = Role.ASSISTANT,
                    userMessageId = MessageId("user-1"),
                    messageState = MessageState.COMPLETE,
                    modelType = ModelType.FAST,
                    createdAt = 2000L,
                )
            val sourceEntity =
                WebSearchSourceEntity(
                    id = "src-1",
                    messageId = messageId,
                    title = "Test Source",
                    url = "https://example.com",
                    content = "Test content",
                    score = 0.9,
                    extracted = true,
                )
            messageDao.upsert(messageEntity)
            database.webSearchSourceDao().upsert(sourceEntity)

            val result = repository.getMessagesForChat(chatId).first()

            // getMessagesForChat returns every message of the chat, so the seeded parent
            // user message is part of the result too (size 1 -> 2); the Tavily-source
            // assertions key on the assistant message by id instead of by position.
            assertEquals(2, result.size)
            val assistant = result.single { it.id == messageId }
            assertEquals(1, assistant.webSearchSources.size)
            assertEquals("https://example.com", assistant.webSearchSources[0].url)
            assertTrue(
                assistant.webSearchSources[0].extracted,
                "Extracted source should have extracted=true from tavily_source table",
            )
        }

    @Test
    fun `createChat stores encrypted title while getAllChats returns plaintext`() =
        runTest {
            val chat =
                Chat(
                    id = ChatId("chat-1"),
                    name = "Private title",
                    created = testDate,
                    lastModified = testDate,
                    pinned = false,
                )

            repository.createChat(chat)

            val stored = chatDao.getChatById(ChatId("chat-1"))
            assertEquals("PCENC:v1:aes-gcm:Private title", stored?.name)

            val result = repository.getAllChats().first()

            assertEquals("Private title", result.single().name)
        }
}
