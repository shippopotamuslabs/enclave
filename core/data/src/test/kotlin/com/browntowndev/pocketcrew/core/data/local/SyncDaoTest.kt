package com.browntowndev.pocketcrew.core.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.browntowndev.pocketcrew.core.data.mapper.toEntity
import com.browntowndev.pocketcrew.domain.model.chat.Chat
import com.browntowndev.pocketcrew.domain.model.chat.ChatId
import com.browntowndev.pocketcrew.domain.model.chat.Message
import com.browntowndev.pocketcrew.domain.model.chat.MessageId
import com.browntowndev.pocketcrew.domain.model.chat.MessageState
import com.browntowndev.pocketcrew.domain.model.chat.Role
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Date
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SyncDaoTest {
    private lateinit var database: PocketCrewDatabase
    private lateinit var chatDao: ChatDao
    private lateinit var messageDao: MessageDao

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            PocketCrewDatabase::class.java
        ).allowMainThreadQueries().build()
        chatDao = database.chatDao()
        messageDao = database.messageDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun `ChatDao updatePinStatus sets pending_sync to true`() = runTest {
        val chatId = ChatId(UUID.randomUUID().toString())
        val chat = ChatEntity(
            id = chatId,
            name = "Test Chat",
            created = Date(),
            lastModified = Date(),
            pinned = false,
            pendingSync = false
        )
        chatDao.insert(chat)
        
        // Initial state
        var savedChat = chatDao.getChatById(chatId)!!
        assertFalse(savedChat.pendingSync)

        // Action
        chatDao.updatePinStatus(chatId)

        // Verify
        savedChat = chatDao.getChatById(chatId)!!
        assertTrue(savedChat.pendingSync)
        assertTrue(savedChat.pinned)
    }

    @Test
    fun `ChatDao updateName sets pending_sync to true`() = runTest {
        val chatId = ChatId(UUID.randomUUID().toString())
        val chat = ChatEntity(
            id = chatId,
            name = "Old Name",
            created = Date(),
            lastModified = Date(),
            pinned = false,
            pendingSync = false
        )
        chatDao.insert(chat)

        // Action
        chatDao.updateName(chatId, "New Name")

        // Verify
        val savedChat = chatDao.getChatById(chatId)!!
        assertTrue(savedChat.pendingSync)
        assertEquals("New Name", savedChat.name)
    }

    @Test
    fun `MessageDao updateMessageContent sets pending_sync to true`() = runTest {
        val chatId = ChatId(UUID.randomUUID().toString())
        val messageId = MessageId(UUID.randomUUID().toString())
        
        // Need a chat first due to foreign key
        chatDao.insert(ChatEntity(chatId, "Chat", Date(), Date(), false))
        
        val message = MessageEntity(
            id = messageId,
            chatId = chatId,
            content = "Old Content",
            role = Role.USER,
            pendingSync = false
        )
        messageDao.insert(message)

        // Action
        messageDao.updateMessageContent(messageId, "New Content", 10, "thinking")

        // Verify
        val savedMessage = messageDao.getMessageById(messageId)!!.message
        assertTrue(savedMessage.pendingSync)
        assertEquals("New Content", savedMessage.content)
    }

    @Test
    fun `MessageDao updateMessageState sets pending_sync to true`() = runTest {
        val chatId = ChatId(UUID.randomUUID().toString())
        val messageId = MessageId(UUID.randomUUID().toString())
        chatDao.insert(ChatEntity(chatId, "Chat", Date(), Date(), false))
        
        messageDao.insert(MessageEntity(
            id = messageId,
            chatId = chatId,
            content = "Content",
            role = Role.USER,
            pendingSync = false
        ))

        // Action
        messageDao.updateMessageState(messageId, MessageState.COMPLETE)

        // Verify
        val savedMessage = messageDao.getMessageById(messageId)!!.message
        assertTrue(savedMessage.pendingSync)
        assertEquals(MessageState.COMPLETE, savedMessage.messageState)
    }

    @Test
    fun `ChatDao getPendingSyncChats returns only chats with pending_sync true`() = runTest {
        chatDao.insert(ChatEntity(ChatId("1"), "Chat 1", Date(), Date(), false, pendingSync = true))
        chatDao.insert(ChatEntity(ChatId("2"), "Chat 2", Date(), Date(), false, pendingSync = false))

        val pending = chatDao.getPendingSyncChats()
        assertEquals(1, pending.size)
        assertEquals("1", pending[0].id.value)
    }

    @Test
    fun `MessageDao getPendingSyncMessages returns only messages with pending_sync true`() = runTest {
        val chatId = ChatId("chat")
        chatDao.insert(ChatEntity(chatId, "Chat", Date(), Date(), false))
        
        messageDao.insert(MessageEntity(
            id = MessageId("1"),
            chatId = chatId,
            content = "C1",
            role = Role.USER,
            pendingSync = true
        ))
        messageDao.insert(MessageEntity(
            id = MessageId("2"),
            chatId = chatId,
            content = "C2",
            role = Role.USER,
            pendingSync = false
        ))

        val pending = messageDao.getPendingSyncMessages()
        assertEquals(1, pending.size)
        assertEquals("1", pending[0].message.id.value)
    }

    @Test
    fun `Chat mapping to entity sets pendingSync to true`() {
        val chat = Chat(
            id = ChatId("1"),
            name = "Test",
            created = Date(),
            lastModified = Date(),
            pinned = false
        )
        val entity = chat.toEntity()
        assertTrue("Entity should have pendingSync = true when mapped from domain", entity.pendingSync)
    }

    @Test
    fun `Message mapping to entity sets pendingSync to true`() {
        val message = Message(
            id = MessageId("1"),
            chatId = ChatId("chat"),
            content = com.browntowndev.pocketcrew.domain.model.chat.Content(text = "Hello"),
            role = Role.USER,
            createdAt = 1000L
        )
        val entity = message.toEntity()
        assertTrue("Entity should have pendingSync = true when mapped from domain", entity.pendingSync)
    }

    @Test
    fun `Chat markSynced resets pending_sync`() = runTest {
        val id = ChatId("1")
        chatDao.insert(ChatEntity(id, "Chat 1", Date(), Date(), false, pendingSync = true))

        chatDao.markSynced(listOf(id))

        val chat = chatDao.getChatById(id)!!
        assertFalse(chat.pendingSync)
    }

    @Test
    fun `MessageDao markSynced resets pending_sync`() = runTest {
        val chatId = ChatId("chat")
        val messageId = MessageId("1")
        chatDao.insert(ChatEntity(chatId, "Chat", Date(), Date(), false))
        messageDao.insert(MessageEntity(
            id = messageId,
            chatId = chatId,
            content = "C1",
            role = Role.USER,
            pendingSync = true
        ))

        messageDao.markSynced(listOf(messageId))

        val message = messageDao.getMessageById(messageId)!!.message
        assertFalse(message.pendingSync)
    }
}
