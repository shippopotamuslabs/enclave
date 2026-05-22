package com.browntowndev.enclave.core.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.browntowndev.enclave.domain.model.artifact.MimeType
import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.MessageId
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension
import java.util.Date
import java.util.UUID

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [34])
class MessageFileDaoTest {
    private lateinit var database: EnclaveDatabase
    private lateinit var chatDao: ChatDao
    private lateinit var messageDao: MessageDao
    private lateinit var fileDao: MessageFileDao

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
        fileDao = database.messageFileDao()
    }

    @AfterEach
    fun teardown() {
        database.close()
    }

    private suspend fun createChatAndMessage(): Pair<ChatId, MessageId> {
        val chatId = ChatId(UUID.randomUUID().toString())
        val messageId = MessageId(UUID.randomUUID().toString())
        chatDao.upsert(ChatEntity(chatId, "Chat", Date(), Date(), false))
        messageDao.upsert(
            MessageEntity(
                id = messageId,
                chatId = chatId,
                content = "C",
                role = com.browntowndev.enclave.domain.model.chat.Role.USER,
            ),
        )
        return chatId to messageId
    }

    @Test
    fun `getPendingUploads returns only PENDING_UPLOAD files that are not deleted`() =
        runTest {
            val (_, messageId) = createChatAndMessage()

            fileDao.insert(
                MessageFileEntity(
                    id = "f1",
                    messageId = messageId.value,
                    name = "file1.txt",
                    content = "c1",
                    mimeType = MimeType.PLAIN_TEXT,
                    syncStatus = "PENDING_UPLOAD",
                    deleted = false,
                ),
            )
            fileDao.insert(
                MessageFileEntity(
                    id = "f2",
                    messageId = messageId.value,
                    name = "file2.txt",
                    content = "c2",
                    mimeType = MimeType.PLAIN_TEXT,
                    syncStatus = "UPLOADED",
                    deleted = false,
                ),
            )
            fileDao.insert(
                MessageFileEntity(
                    id = "f3",
                    messageId = messageId.value,
                    name = "file3.txt",
                    content = "c3",
                    mimeType = MimeType.PLAIN_TEXT,
                    syncStatus = "PENDING_UPLOAD",
                    deleted = true,
                ),
            )

            val pending = fileDao.getPendingUploads()
            assertEquals(1, pending.size)
            assertEquals("f1", pending[0].id)
        }

    @Test
    fun `getPendingDeletions returns only deleted files that are not yet DELETED in cloud`() =
        runTest {
            val (_, messageId) = createChatAndMessage()

            fileDao.insert(
                MessageFileEntity(
                    id = "f1",
                    messageId = messageId.value,
                    name = "f1",
                    content = "c",
                    mimeType = MimeType.PLAIN_TEXT,
                    syncStatus = "UPLOADED",
                    deleted = true,
                ),
            )
            fileDao.insert(
                MessageFileEntity(
                    id = "f2",
                    messageId = messageId.value,
                    name = "f2",
                    content = "c",
                    mimeType = MimeType.PLAIN_TEXT,
                    syncStatus = "DELETED",
                    deleted = true,
                ),
            )
            fileDao.insert(
                MessageFileEntity(
                    id = "f3",
                    messageId = messageId.value,
                    name = "f3",
                    content = "c",
                    mimeType = MimeType.PLAIN_TEXT,
                    syncStatus = "UPLOADED",
                    deleted = false,
                ),
            )

            val pending = fileDao.getPendingDeletions()
            assertEquals(1, pending.size)
            assertEquals("f1", pending[0].id)
        }

    @Test
    fun `markCloudDeleted updates syncStatus to DELETED`() =
        runTest {
            val (_, messageId) = createChatAndMessage()
            fileDao.insert(
                MessageFileEntity(
                    id = "f1",
                    messageId = messageId.value,
                    name = "f1",
                    content = "c",
                    mimeType = MimeType.PLAIN_TEXT,
                    syncStatus = "UPLOADED",
                    deleted = true,
                ),
            )

            fileDao.markCloudDeleted("f1")

            val file = fileDao.getByIdIncludingDeleted("f1")
            assertNotNull(file)
            assertEquals("DELETED", file?.syncStatus)
        }

    @Test
    fun `claimPending updates pendingSync from 1 to 2`() =
        runTest {
            val (_, messageId) = createChatAndMessage()
            fileDao.insert(MessageFileEntity("f1", messageId.value, "f1", "c", mimeType = MimeType.PLAIN_TEXT, pendingSync = 1))
            fileDao.insert(MessageFileEntity("f2", messageId.value, "f2", "c", mimeType = MimeType.PLAIN_TEXT, pendingSync = 0))

            fileDao.claimPending()

            val f1 = fileDao.getByIdIncludingDeleted("f1")
            val f2 = fileDao.getByIdIncludingDeleted("f2")
            assertEquals(2, f1?.pendingSync)
            assertEquals(0, f2?.pendingSync)
        }

    @Test
    fun `getClaimedForSync returns only pendingSync = 2 files`() =
        runTest {
            val (_, messageId) = createChatAndMessage()
            fileDao.insert(
                MessageFileEntity(
                    id = "f1",
                    messageId = messageId.value,
                    name = "f1",
                    content = "c",
                    mimeType = MimeType.PLAIN_TEXT,
                    pendingSync = 2,
                ),
            )
            fileDao.insert(
                MessageFileEntity(
                    id = "f2",
                    messageId = messageId.value,
                    name = "f2",
                    content = "c",
                    mimeType = MimeType.PLAIN_TEXT,
                    pendingSync = 1,
                ),
            )

            val claimed = fileDao.getClaimedForSync()
            assertEquals(1, claimed.size)
            assertEquals("f1", claimed[0].id)
        }

    @Test
    fun `markSynced updates pendingSync from 2 to 0`() =
        runTest {
            val (_, messageId) = createChatAndMessage()
            fileDao.insert(
                MessageFileEntity(
                    id = "f1",
                    messageId = messageId.value,
                    name = "f1",
                    content = "c",
                    mimeType = MimeType.PLAIN_TEXT,
                    pendingSync = 2,
                ),
            )
            fileDao.insert(
                MessageFileEntity(
                    id = "f2",
                    messageId = messageId.value,
                    name = "f2",
                    content = "c",
                    mimeType = MimeType.PLAIN_TEXT,
                    pendingSync = 2,
                ),
            )

            fileDao.markSynced(listOf("f1"))

            val f1 = fileDao.getByIdIncludingDeleted("f1")
            val f2 = fileDao.getByIdIncludingDeleted("f2")
            assertEquals(0, f1?.pendingSync)
            assertEquals(2, f2?.pendingSync)
        }

    @Test
    fun `updateStatus updates syncStatus and updatedAt`() =
        runTest {
            val (_, messageId) = createChatAndMessage()
            val initialTime = 1000L
            fileDao.insert(
                MessageFileEntity(
                    id = "f1",
                    messageId = messageId.value,
                    name = "f1",
                    content = "c",
                    mimeType = MimeType.PLAIN_TEXT,
                    syncStatus = "PENDING_UPLOAD",
                    updatedAt = initialTime,
                ),
            )

            val updateTime = 2000L
            fileDao.updateStatus("f1", "UPLOADED", updateTime)

            val file = fileDao.getByIdIncludingDeleted("f1")
            assertNotNull(file)
            assertEquals("UPLOADED", file?.syncStatus)
            assertEquals(updateTime, file?.updatedAt)
        }

    @Test
    fun `MessageFileEntity supports remoteUrl and mimeType`() =
        runTest {
            val (_, messageId) = createChatAndMessage()
            fileDao.insert(
                MessageFileEntity(
                    id = "f1",
                    messageId = messageId.value,
                    name = "f1",
                    content = "c",
                    mimeType = MimeType.PDF,
                    remoteUrl = "https://example.com/file.png",
                ),
            )

            val file = fileDao.getByIdIncludingDeleted("f1")
            assertNotNull(file)
            assertEquals(MimeType.PDF, file?.mimeType)
            assertEquals("https://example.com/file.png", file?.remoteUrl)
        }
}
