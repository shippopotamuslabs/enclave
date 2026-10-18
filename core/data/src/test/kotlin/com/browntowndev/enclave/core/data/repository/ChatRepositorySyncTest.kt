package com.browntowndev.enclave.core.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.browntowndev.enclave.core.data.local.ChatDao
import com.browntowndev.enclave.core.data.local.ChatEntity
import com.browntowndev.enclave.core.data.local.EnclaveDatabase
import com.browntowndev.enclave.core.data.local.MessageEntity
import com.browntowndev.enclave.core.data.local.dao.message.MessageDao
import com.browntowndev.enclave.core.data.local.messageDao
import com.browntowndev.enclave.core.testing.FakeTransactionProvider
import com.browntowndev.enclave.domain.model.chat.Chat
import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.model.chat.MessageState
import com.browntowndev.enclave.domain.model.chat.Role
import com.browntowndev.enclave.domain.model.inference.ModelType
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension
import java.util.Date

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [34])
class ChatRepositorySyncTest {
    private lateinit var database: EnclaveDatabase
    private lateinit var syncTrigger: RecordingCloudSyncTrigger
    private lateinit var transactionProvider: FakeTransactionProvider
    private lateinit var chatDao: ChatDao
    private lateinit var messageDao: MessageDao

    private lateinit var repository: ChatRepositoryImpl

    @BeforeEach
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database =
            Room
                .inMemoryDatabaseBuilder(context, EnclaveDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        syncTrigger = RecordingCloudSyncTrigger()
        transactionProvider = FakeTransactionProvider()
        chatDao = database.chatDao()
        messageDao = database.messageDao()
        repository = database.chatRepositoryImpl(syncTrigger, transactionProvider)
    }

    @AfterEach
    fun tearDown() {
        database.close()
    }

    @Test
    fun `createChat sets pendingSync to 1 and triggers sync`() =
        runTest {
            val chat =
                Chat(
                    id = ChatId("id"),
                    name = "Chat",
                    created = Date(),
                    lastModified = Date(),
                )

            repository.createChat(chat)

            val stored = chatDao.getChatById(ChatId("id"))
            assertEquals(1, stored?.pendingSync)
            assertEquals(1, syncTrigger.triggerCalls)
        }

    @Test
    fun `createAssistantMessage sets pendingSync to 1 and triggers sync`() =
        runTest {
            chatDao.upsert(
                ChatEntity(
                    id = ChatId("chat-id"),
                    name = "Chat",
                    created = Date(),
                    lastModified = Date(),
                    pinned = false,
                ),
            )
            // createAssistantMessage writes the assistant row with
            // user_message_id = "user-id", a self-FK to message.id: the parent user
            // message row must exist first or the upsert violates the FK (invisible
            // under the MockK DAOs, a SQLiteConstraintException under real Room).
            messageDao.upsert(
                MessageEntity(
                    id = MessageId("user-id"),
                    chatId = ChatId("chat-id"),
                    content = "Question",
                    role = Role.USER,
                ),
            )

            val messageId = repository.createAssistantMessage(ChatId("chat-id"), MessageId("user-id"), ModelType.FAST, null)

            val stored = messageDao.getMessageById(messageId)
            assertNotNull(stored)
            assertEquals(1, stored!!.message.pendingSync)
            assertEquals(1, syncTrigger.triggerCalls)
        }

    @Test
    fun `updateMessageState triggers sync`() =
        runTest {
            repository.updateMessageState(MessageId("id"), MessageState.COMPLETE)

            assertEquals(1, syncTrigger.triggerCalls)
        }

    @Test
    fun `deleteChat triggers sync`() =
        runTest {
            chatDao.upsert(
                ChatEntity(
                    id = ChatId("id"),
                    name = "Chat",
                    created = Date(),
                    lastModified = Date(),
                    pinned = false,
                ),
            )

            repository.deleteChat(ChatId("id"))

            assertEquals(1, transactionProvider.runInTransactionCallCount)
            assertNull(chatDao.getChatById(ChatId("id")))
            assertTrue(messageDao.getMessagesByChatId(ChatId("id")).isEmpty())
            assertEquals(1, syncTrigger.triggerCalls)
        }
}
