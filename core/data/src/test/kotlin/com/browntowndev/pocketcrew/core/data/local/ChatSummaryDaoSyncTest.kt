package com.browntowndev.pocketcrew.core.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.browntowndev.pocketcrew.domain.model.chat.ChatId
import com.browntowndev.pocketcrew.domain.model.chat.MessageId
import com.browntowndev.pocketcrew.domain.model.chat.Role
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Date
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ChatSummaryDaoSyncTest {
    private lateinit var database: PocketCrewDatabase
    private lateinit var chatDao: ChatDao
    private lateinit var messageDao: MessageDao
    private lateinit var chatSummaryDao: ChatSummaryDao

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            PocketCrewDatabase::class.java
        ).allowMainThreadQueries().build()
        chatDao = database.chatDao()
        messageDao = database.messageDao()
        chatSummaryDao = database.chatSummaryDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun `ChatSummaryDao getClaimedForSync returns only claimed rows`() = runTest {
        val chatId = ChatId(UUID.randomUUID().toString())
        chatDao.upsert(ChatEntity(chatId, "Chat", Date(), Date(), false))
        
        val summary1 = ChatSummaryEntity(
            id = 1L,
            chatId = chatId,
            content = "Summary 1",
            lastSummarizedMessageId = null,
            pendingSync = 1
        )
        val summary2 = ChatSummaryEntity(
            id = 2L,
            chatId = ChatId(UUID.randomUUID().toString()), // This will fail due to FK if I don't insert chat
            content = "Summary 2",
            lastSummarizedMessageId = null,
            pendingSync = 0
        )
        // Need second chat
        chatDao.upsert(ChatEntity(summary2.chatId, "Chat 2", Date(), Date(), false))

        chatSummaryDao.insertOrUpdateSummary(summary1)
        chatSummaryDao.insertOrUpdateSummary(summary2)

        chatSummaryDao.claimPending()
        val pending = chatSummaryDao.getClaimedForSync()
        assertEquals(1, pending.size)
        assertEquals(1L, pending[0].id)
        assertEquals(2, pending[0].pendingSync)
    }

    @Test
    fun `ChatSummaryDao markSynced resets pending_sync`() = runTest {
        val chatId = ChatId(UUID.randomUUID().toString())
        chatDao.upsert(ChatEntity(chatId, "Chat", Date(), Date(), false))
        
        val summaryId = 1L
        chatSummaryDao.insertOrUpdateSummary(ChatSummaryEntity(
            id = summaryId,
            chatId = chatId,
            content = "Summary",
            lastSummarizedMessageId = null,
            pendingSync = 1
        ))

        chatSummaryDao.claimPending()
        chatSummaryDao.markSynced(listOf(summaryId))

        val summary = chatSummaryDao.getSummaryForChatSync(chatId)!!
        assertEquals(0, summary.pendingSync)
    }

    @Test
    fun `ChatSummaryDao softDeleteById sets deleted flag and pending_sync`() = runTest {
        val chatId = ChatId(UUID.randomUUID().toString())
        chatDao.upsert(ChatEntity(chatId, "Chat", Date(), Date(), false))
        
        val summaryId = 1L
        chatSummaryDao.insertOrUpdateSummary(ChatSummaryEntity(
            id = summaryId,
            chatId = chatId,
            content = "Summary",
            lastSummarizedMessageId = null,
            pendingSync = 0
        ))

        chatSummaryDao.softDeleteById(summaryId)

        val summary = chatSummaryDao.getSummaryByChatIdIncludingDeletedSync(chatId)!!
        assertEquals(true, summary.deleted)
        assertEquals(1, summary.pendingSync)
    }
}
