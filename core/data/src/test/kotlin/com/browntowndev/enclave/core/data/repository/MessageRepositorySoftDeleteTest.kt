package com.browntowndev.enclave.core.data.repository

import com.browntowndev.enclave.core.data.local.MessageDao
import com.browntowndev.enclave.core.data.local.MessageEntity
import com.browntowndev.enclave.core.data.local.MessageWithFiles
import com.browntowndev.enclave.core.data.sync.CloudSyncTrigger
import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.model.chat.Role
import com.browntowndev.enclave.domain.port.repository.TransactionProvider
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class MessageRepositorySoftDeleteTest {
    private lateinit var messageDao: MessageDao
    private lateinit var repository: MessageRepositoryImpl
    private val syncTrigger = mockk<CloudSyncTrigger>(relaxed = true)

    @BeforeEach
    fun setup() {
        messageDao = mockk(relaxed = true)
        repository =
            createMessageRepositoryImpl(
                syncTrigger = syncTrigger,
                messageDao = messageDao,
                messageVisionAnalysisDao = mockk(relaxed = true),
                chatSummaryDao = mockk(relaxed = true),
                embeddingDao = mockk(relaxed = true),
                transactionProvider =
                    object : TransactionProvider {
                        override suspend fun <T> runInTransaction(block: suspend () -> T): T = block()
                    },
                healer = testSensitiveSelfHealer(FakeLocalDataEncryptionService()),
                readRepairApplier = mockk(relaxed = true),
            )
    }

    @Test
    @DisplayName("DOPG-D-08 — softDeleteMessage delegates to MessageDao.softDeleteById")
    fun dopgD08_softDeleteDelegatesToDao() =
        runTest {
            val messageId = MessageId("msg-soft-1")

            repository.softDeleteMessage(messageId)

            coVerify(exactly = 1) { messageDao.softDeleteById(messageId, any()) }
            verify { syncTrigger.trigger() }
        }

    @Test
    @DisplayName("DOPG-D-09 — soft-deleted message excluded from getMessagesForChat results")
    fun dopgD09_softDeletedExcludedFromActiveQuery() =
        runTest {
            val chatId = ChatId("chat-soft-1")
            val deletedId = MessageId("msg-soft-2")
            val activeId = MessageId("msg-soft-3")
            val beforeDelete =
                listOf(
                    MessageWithFiles(
                        message =
                            MessageEntity(
                                id = deletedId,
                                chatId = chatId,
                                content = "will delete",
                                role = Role.USER,
                            ),
                        files = emptyList(),
                    ),
                    MessageWithFiles(
                        message =
                            MessageEntity(
                                id = activeId,
                                chatId = chatId,
                                content = "still visible",
                                role = Role.USER,
                            ),
                        files = emptyList(),
                    ),
                )
            val afterDelete =
                listOf(
                    MessageWithFiles(
                        message =
                            MessageEntity(
                                id = activeId,
                                chatId = chatId,
                                content = "still visible",
                                role = Role.USER,
                            ),
                        files = emptyList(),
                    ),
                )
            coEvery { messageDao.getMessagesByChatId(chatId) } returnsMany listOf(beforeDelete, afterDelete)

            val beforeResult = repository.getMessagesForChat(chatId)
            repository.softDeleteMessage(deletedId)
            val afterResult = repository.getMessagesForChat(chatId)

            coVerify(exactly = 1) { messageDao.softDeleteById(deletedId, any()) }
            assertEquals(2, beforeResult.size)
            assertEquals(1, afterResult.size)
            assertEquals(activeId, afterResult.single().id)
            assert(afterResult.none { it.id == deletedId })
        }
}
