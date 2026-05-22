package com.browntowndev.enclave.core.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.browntowndev.enclave.domain.model.artifact.MimeType
import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.model.chat.MessageState
import com.browntowndev.enclave.domain.model.chat.Role
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension
import java.util.Date
import java.util.UUID

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [34])
class SyncDaoTest {
    private lateinit var database: EnclaveDatabase
    private lateinit var chatDao: ChatDao
    private lateinit var messageDao: MessageDao
    private lateinit var firestoreSyncPlaceholderDao: FirestoreSyncPlaceholderDao

    @BeforeEach
    fun setup() {
        database =
            Room
                .inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    EnclaveDatabase::class.java,
                ).allowMainThreadQueries()
                .build()
        chatDao = database.chatDao()
        messageDao = database.messageDao()
        firestoreSyncPlaceholderDao = database.firestoreSyncPlaceholderDao()
    }

    @AfterEach
    fun teardown() {
        database.close()
    }

    // ========== Phase 1: Chat and Message DAO ==========

    @Test
    fun `ChatDao getClaimedForSync returns only claimed rows`() =
        runTest {
            val id1 = ChatId(UUID.randomUUID().toString())
            val id2 = ChatId(UUID.randomUUID().toString())
            chatDao.upsert(ChatEntity(id1, "Chat 1", Date(), Date(), false, pendingSync = 1))
            chatDao.upsert(ChatEntity(id2, "Chat 2", Date(), Date(), false, pendingSync = 0))

            chatDao.claimPending()
            val claimed = chatDao.getClaimedForSync()
            assertEquals(1, claimed.size)
            assertEquals(id1, claimed[0].id)
            assertEquals(2, claimed[0].pendingSync)
        }

    @Test
    fun `ChatDao markSynced resets pending_sync`() =
        runTest {
            val id = ChatId(UUID.randomUUID().toString())
            chatDao.upsert(ChatEntity(id, "Chat", Date(), Date(), false, pendingSync = 1))

            chatDao.claimPending()
            chatDao.markSynced(listOf(id))

            val chat = chatDao.getChatById(id)!!
            assertEquals(0, chat.pendingSync)
        }

    @Test
    fun `ChatDao deleteOrphanedPlaceholders removes only created sentinel rows`() =
        runTest {
            val placeholderId = ChatId("placeholder-chat")
            val realId = ChatId("real-chat")
            chatDao.upsert(
                ChatEntity(
                    id = placeholderId,
                    name = "Syncing...",
                    created = Date(0),
                    lastModified = Date(0),
                    pinned = false,
                ),
            )
            chatDao.upsert(
                ChatEntity(
                    id = realId,
                    name = "Real chat",
                    created = Date(1_000L),
                    lastModified = Date(2_000L),
                    pinned = false,
                ),
            )

            val removed = chatDao.deleteOrphanedPlaceholders()

            assertEquals(1, removed)
            assertEquals(null, chatDao.getChatById(placeholderId))
            assertEquals(realId, chatDao.getChatById(realId)?.id)
        }

    @Test
    fun `ChatDao deleteOrphanedPlaceholders keeps placeholder chats that already have messages`() =
        runTest {
            val placeholderId = ChatId("placeholder-with-message")
            val chat =
                ChatEntity(
                    id = placeholderId,
                    name = "Syncing...",
                    created = Date(0),
                    lastModified = Date(0),
                    pinned = false,
                )
            val messageId = MessageId("msg-placeholder")
            firestoreSyncPlaceholderDao.insertPlaceholderChatAndMessage(
                chatEntity = chat,
                messageEntity =
                    MessageEntity(
                        id = messageId,
                        chatId = placeholderId,
                        content = "Syncing...",
                        role = Role.USER,
                        messageState = MessageState.COMPLETE,
                        createdAt = 0L,
                    ),
            )

            val removed = chatDao.deleteOrphanedPlaceholders()

            assertEquals(0, removed)
            assertEquals(placeholderId, chatDao.getChatById(placeholderId)?.id)
            assertEquals(messageId, messageDao.getMessageById(messageId)?.message?.id)
        }

    @Test
    fun `MessageDao getClaimedForSync returns only claimed rows`() =
        runTest {
            val chatId = ChatId("chat-1")
            chatDao.upsert(ChatEntity(chatId, "Chat", Date(), Date(), false))

            val msgId1 = MessageId("msg-1")
            val msgId2 = MessageId("msg-2")
            messageDao.upsert(MessageEntity(id = msgId1, chatId = chatId, content = "C1", role = Role.USER, pendingSync = 1))
            messageDao.upsert(MessageEntity(id = msgId2, chatId = chatId, content = "C2", role = Role.USER, pendingSync = 0))

            messageDao.claimPending()
            val claimed = messageDao.getClaimedForSync()
            assertEquals(1, claimed.size)
            assertEquals(msgId1, claimed[0].message.id)
        }

    @Test
    fun `MessageDao markSynced resets pending_sync`() =
        runTest {
            val chatId = ChatId("chat-m")
            chatDao.upsert(ChatEntity(chatId, "Chat", Date(), Date(), false))
            val msgId = MessageId("msg-m-1")
            messageDao.upsert(MessageEntity(id = msgId, chatId = chatId, content = "C", role = Role.USER, pendingSync = 1))

            messageDao.claimPending()
            messageDao.markSynced(listOf(msgId))

            val msg = messageDao.getMessageById(msgId)!!.message
            assertEquals(0, msg.pendingSync)
        }

    // ========== Phase 2: MessageFileDao ==========

    @Test
    fun `MessageFileDao getClaimedForSync returns only claimed rows`() =
        runTest {
            val chatId = ChatId("chat-f")
            chatDao.upsert(ChatEntity(chatId, "Chat", Date(), Date(), false))
            val msgId = MessageId("msg-f-1")
            messageDao.upsert(MessageEntity(id = msgId, chatId = chatId, content = "C", role = Role.USER))
            val fileDao = database.messageFileDao()
            fileDao.insert(MessageFileEntity("f-1", msgId.value, "file1.txt", "content1", mimeType = MimeType.PLAIN_TEXT, pendingSync = 1))
            fileDao.insert(MessageFileEntity("f-2", msgId.value, "file2.txt", "content2", mimeType = MimeType.PLAIN_TEXT, pendingSync = 0))

            fileDao.claimPending()
            val pending = fileDao.getClaimedForSync()
            assertEquals(1, pending.size)
            assertEquals("f-1", pending[0].id)
            assertEquals(2, pending[0].pendingSync)
        }

    @Test
    fun `MessageFileDao markSynced resets pending_sync`() =
        runTest {
            val chatId = ChatId("chat-f-sync")
            chatDao.upsert(ChatEntity(chatId, "Chat", Date(), Date(), false))
            val msgId = MessageId("msg-f-sync-1")
            messageDao.upsert(MessageEntity(id = msgId, chatId = chatId, content = "C", role = Role.USER))
            val fileDao = database.messageFileDao()
            fileDao.insert(
                MessageFileEntity("f-sync-1", msgId.value, "file.txt", "content", mimeType = MimeType.PLAIN_TEXT, pendingSync = 1),
            )

            fileDao.claimPending()
            fileDao.markSynced(listOf("f-sync-1"))

            val file = fileDao.getById("f-sync-1")!!
            assertEquals(0, file.pendingSync)
        }
}
