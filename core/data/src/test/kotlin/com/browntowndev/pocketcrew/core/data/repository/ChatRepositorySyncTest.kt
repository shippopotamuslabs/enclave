package com.browntowndev.pocketcrew.core.data.repository

import com.browntowndev.pocketcrew.core.data.local.ChatDao
import com.browntowndev.pocketcrew.core.data.local.DocumentDao
import com.browntowndev.pocketcrew.core.data.local.MessageDao
import com.browntowndev.pocketcrew.core.data.local.MessageTranscriptSegmentDao
import com.browntowndev.pocketcrew.core.data.local.WebSearchSourceDao
import com.browntowndev.pocketcrew.core.data.security.SensitiveRoomReadRepairApplier
import com.browntowndev.pocketcrew.core.data.sync.CloudSyncTrigger
import com.browntowndev.pocketcrew.domain.model.chat.Chat
import com.browntowndev.pocketcrew.domain.model.chat.ChatId
import com.browntowndev.pocketcrew.domain.model.chat.MessageId
import com.browntowndev.pocketcrew.domain.model.chat.MessageState
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ChatRepositorySyncTest {
    private val syncTrigger = mockk<CloudSyncTrigger>(relaxed = true)
    private val chatDao = mockk<ChatDao>(relaxed = true)
    private val messageDao = mockk<MessageDao>(relaxed = true)
    private val messageTranscriptSegmentDao = mockk<MessageTranscriptSegmentDao>(relaxed = true)
    private val webSearchSourceDao = mockk<WebSearchSourceDao>(relaxed = true)
    private val documentDao = mockk<DocumentDao>(relaxed = true)
    private val transactionProvider = mockk<com.browntowndev.pocketcrew.domain.port.repository.TransactionProvider>(relaxed = true)
    private val readRepairApplier = mockk<SensitiveRoomReadRepairApplier>(relaxed = true)

    private lateinit var repository: ChatRepositoryImpl

    @BeforeEach
    fun setup() {
        // Mock transaction provider to run the block immediately
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
                healer = testSensitiveSelfHealer(FakeLocalDataEncryptionService()),
                readRepairApplier = readRepairApplier,
            )
    }

    @Test
    fun `createChat sets pendingSync to 1 and triggers sync`() =
        runTest {
            val chat =
                Chat(
                    id = ChatId("id"),
                    name = "Chat",
                    created = java.util.Date(),
                    lastModified = java.util.Date(),
                )

            repository.createChat(chat)

            coVerify {
                chatDao.upsert(match { it.pendingSync == 1 })
                syncTrigger.trigger()
            }
        }

    @Test
    fun `createAssistantMessage sets pendingSync to 1 and triggers sync`() =
        runTest {
            repository.createAssistantMessage(ChatId("chat-id"), MessageId("user-id"), ModelType.FAST, null)

            coVerify {
                messageDao.upsert(match { it.pendingSync == 1 })
                syncTrigger.trigger()
            }
        }

    @Test
    fun `updateMessageState triggers sync`() =
        runTest {
            repository.updateMessageState(MessageId("id"), MessageState.COMPLETE)

            coVerify {
                syncTrigger.trigger()
            }
        }

    @Test
    fun `deleteChat triggers sync`() =
        runTest {
            coEvery { chatDao.softDeleteById(any(), any()) } returns 1
            repository.deleteChat(ChatId("id"))

            coVerify {
                chatDao.softDeleteById(ChatId("id"), any())
                documentDao.softDeleteByChatId(ChatId("id"), any())
                syncTrigger.trigger()
            }
        }
}
