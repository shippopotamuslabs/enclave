package com.browntowndev.enclave.core.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.browntowndev.enclave.core.data.local.ChatEntity
import com.browntowndev.enclave.core.data.local.DocumentEntity
import com.browntowndev.enclave.core.data.local.EnclaveDatabase
import com.browntowndev.enclave.core.data.local.MessageEntity
import com.browntowndev.enclave.core.data.local.messageDao
import com.browntowndev.enclave.core.testing.FakeTransactionProvider
import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.model.chat.MessageState
import com.browntowndev.enclave.domain.model.chat.Role
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension
import java.util.Date

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [34])
class ChatRepositoryDeleteChatCascadeTest {
    private lateinit var database: EnclaveDatabase
    private lateinit var syncTrigger: RecordingCloudSyncTrigger
    private lateinit var transactionProvider: FakeTransactionProvider

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
        repository = database.chatRepositoryImpl(syncTrigger, transactionProvider)
    }

    @AfterEach
    fun tearDown() {
        database.close()
    }

    @Test
    @DisplayName("deleteChat soft-deletes active messages before chat in a single transaction")
    fun deleteChat_cascadesMessageSoftDeleteInTransaction() =
        runTest {
            val chatId = ChatId("chat-cascade")
            database.chatDao().upsert(
                ChatEntity(
                    id = chatId,
                    name = "Cascade",
                    created = Date(),
                    lastModified = Date(),
                    pinned = false,
                ),
            )
            database.messageDao().upsert(
                MessageEntity(
                    id = MessageId("msg-cascade"),
                    chatId = chatId,
                    role = Role.USER,
                    messageState = MessageState.COMPLETE,
                    content = "hello",
                    createdAt = 1000L,
                ),
            )
            database.documentDao().insertDocument(
                DocumentEntity(
                    id = "doc-cascade",
                    messageId = MessageId("msg-cascade"),
                    json = "{}",
                    title = "Doc",
                ),
            )

            repository.deleteChat(chatId)

            // Final state: the chat, its messages, and its documents are all soft-deleted
            // (the DAOs' active-row queries no longer return them), the cascade ran in
            // exactly one transaction, and sync was triggered exactly once.
            assertEquals(1, transactionProvider.runInTransactionCallCount)
            assertNull(database.chatDao().getChatById(chatId))
            assertTrue(database.messageDao().getMessagesByChatId(chatId).isEmpty())
            // KNOWN PRODUCTION BUG (surfaced by this real-Room translation; the MockK-era
            // test only verified the DAO call and masked it): the document assertion below
            // currently FAILS against production. ChatRepositoryImpl.deleteChat soft-deletes
            // the chat's messages FIRST, and DocumentDao.softDeleteByChatId's subquery only
            // matches messages with deleted = 0 — so by the time it runs, the subquery is
            // empty and the chat's documents are never soft-deleted (they stay deleted = 0,
            // pendingSync = 0 and can resurrect on sync, contradicting the intent stated in
            // deleteChat). MessageFileDao.softDeleteByChatId has no deleted = 0 filter on
            // its message subquery and works. The assertion is kept as the intended-contract
            // check; fixing the production query/order is a separate production change.
            assertNull(database.documentDao().getDocumentById("doc-cascade"))
            assertEquals(1, syncTrigger.triggerCalls)
        }
}
