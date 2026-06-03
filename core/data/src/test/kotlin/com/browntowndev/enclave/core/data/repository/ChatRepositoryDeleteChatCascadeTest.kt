package com.browntowndev.enclave.core.data.repository

import com.browntowndev.enclave.core.data.local.ChatDao
import com.browntowndev.enclave.core.data.local.DocumentDao
import com.browntowndev.enclave.core.data.local.MessageDao
import com.browntowndev.enclave.core.data.local.MessageTranscriptSegmentDao
import com.browntowndev.enclave.core.data.local.WebSearchSourceDao
import com.browntowndev.enclave.core.data.security.SensitiveRoomReadRepairApplier
import com.browntowndev.enclave.core.data.sync.CloudSyncTrigger
import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.port.repository.TransactionProvider
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class ChatRepositoryDeleteChatCascadeTest {
    private val syncTrigger = mockk<CloudSyncTrigger>(relaxed = true)
    private val chatDao = mockk<ChatDao>(relaxed = true)
    private val messageDao = mockk<MessageDao>(relaxed = true)
    private val messageTranscriptSegmentDao = mockk<MessageTranscriptSegmentDao>(relaxed = true)
    private val webSearchSourceDao = mockk<WebSearchSourceDao>(relaxed = true)
    private val documentDao = mockk<DocumentDao>(relaxed = true)
    private val transactionProvider = mockk<TransactionProvider>(relaxed = true)
    private val readRepairApplier = mockk<SensitiveRoomReadRepairApplier>(relaxed = true)

    private lateinit var repository: ChatRepositoryImpl

    @BeforeEach
    fun setup() {
        coEvery { transactionProvider.runInTransaction<Any>(any()) } answers {
            runTest { (it.invocation.args[0] as suspend () -> Any).invoke() }
        }

        repository =
            ChatRepositoryImpl(
                syncTrigger = syncTrigger,
                chatDao = chatDao,
                messageDao = messageDao,
                messageTranscriptSegmentDao = messageTranscriptSegmentDao,
                webSearchSourceDao = webSearchSourceDao,
                documentDao = documentDao,
                transactionProvider = transactionProvider,
                cleanupDao = mockk(relaxed = true),
                messageInferenceTelemetryDao = mockk(relaxed = true),
                messageFileDao = mockk(relaxed = true),
                healer = testSensitiveSelfHealer(FakeLocalDataEncryptionService()),
                readRepairApplier = readRepairApplier,
            )
    }

    @Test
    @DisplayName("deleteChat soft-deletes active messages before chat in a single transaction")
    fun deleteChat_cascadesMessageSoftDeleteInTransaction() =
        runTest {
            val chatId = ChatId("chat-cascade")
            coEvery { chatDao.softDeleteById(chatId, any()) } returns 1

            repository.deleteChat(chatId)

            coVerify(exactly = 1) { transactionProvider.runInTransaction<Any>(any()) }
            coVerifyOrder {
                messageDao.softDeleteAllActiveByChatId(chatId, any())
                chatDao.softDeleteById(chatId, any())
                documentDao.softDeleteByChatId(chatId, any())
            }
            coVerify(exactly = 1) { syncTrigger.trigger() }
        }
}