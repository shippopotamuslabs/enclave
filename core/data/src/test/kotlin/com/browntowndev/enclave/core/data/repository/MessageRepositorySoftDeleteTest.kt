package com.browntowndev.enclave.core.data.repository

import com.browntowndev.enclave.core.data.local.EnclaveDatabase
import com.browntowndev.enclave.core.data.local.MessageEntity
import com.browntowndev.enclave.core.data.local.messageDao
import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.model.chat.Role
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import tech.apter.junit.jupiter.robolectric.RobolectricExtension
import org.robolectric.annotation.Config as RoboConfig

/**
 * Translated from MockK to a real in-memory Room database (ENC-28):
 * soft-delete behavior is asserted on the stored rows (the row remains with
 * deleted = 1 and drops out of the active query) and on the recording sync
 * trigger's call count.
 */
@ExtendWith(RobolectricExtension::class)
@RoboConfig(sdk = [34])
class MessageRepositorySoftDeleteTest {
    private lateinit var database: EnclaveDatabase
    private lateinit var repository: MessageRepositoryImpl
    private lateinit var syncTrigger: RecordingCloudSyncTrigger

    @BeforeEach
    fun setup() {
        syncTrigger = RecordingCloudSyncTrigger()
        database = createInMemoryEnclaveDatabase()
        repository =
            createMessageRepositoryImpl(
                database = database,
                syncTrigger = syncTrigger,
            )
    }

    @AfterEach
    fun tearDown() {
        database.close()
    }

    @Test
    @DisplayName("DOPG-D-08 — softDeleteMessage delegates to MessageDao.softDeleteById")
    fun dopgD08_softDeleteDelegatesToDao() =
        runTest {
            val chatId = ChatId("chat-soft-1")
            val messageId = MessageId("msg-soft-1")
            database.seedChat(chatId)
            database.messageDao().upsert(
                MessageEntity(id = messageId, chatId = chatId, content = "to delete", role = Role.USER),
            )

            repository.softDeleteMessage(messageId)

            // The row is soft-deleted: gone from the active (deleted = 0) query, still stored.
            assertEquals(null, database.messageQueryDao().getMessageById(messageId))
            assertTrue(database.messageQueryDao().getAllIds().contains(messageId))
            assertEquals(1, syncTrigger.triggerCalls)
        }

    @Test
    @DisplayName("DOPG-D-09 — soft-deleted message excluded from getMessagesForChat results")
    fun dopgD09_softDeletedExcludedFromActiveQuery() =
        runTest {
            val chatId = ChatId("chat-soft-1")
            val deletedId = MessageId("msg-soft-2")
            val activeId = MessageId("msg-soft-3")
            database.seedChat(chatId)
            database.messageDao().upsert(
                MessageEntity(id = deletedId, chatId = chatId, content = "will delete", role = Role.USER, createdAt = 1L),
            )
            database.messageDao().upsert(
                MessageEntity(id = activeId, chatId = chatId, content = "still visible", role = Role.USER, createdAt = 2L),
            )

            val beforeResult = repository.getMessagesForChat(chatId)
            repository.softDeleteMessage(deletedId)
            val afterResult = repository.getMessagesForChat(chatId)

            assertEquals(2, beforeResult.size)
            assertEquals(1, afterResult.size)
            assertEquals(activeId, afterResult.single().id)
            assert(afterResult.none { it.id == deletedId })
        }
}
