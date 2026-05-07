package com.browntowndev.pocketcrew.core.data.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.browntowndev.pocketcrew.core.data.local.ChatDao
import com.browntowndev.pocketcrew.core.data.local.ChatEntity
import com.browntowndev.pocketcrew.core.data.local.MessageDao
import com.browntowndev.pocketcrew.core.data.local.MessageEntity
import com.browntowndev.pocketcrew.core.data.local.MessageWithFiles
import com.browntowndev.pocketcrew.domain.model.chat.ChatId
import com.browntowndev.pocketcrew.domain.model.chat.MessageId
import com.browntowndev.pocketcrew.domain.model.chat.Role
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.repository.CloudSyncPort
import com.browntowndev.pocketcrew.domain.port.security.CryptoManagerPort
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Date

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SyncWorkerTest {
    private lateinit var context: Context
    private val chatDao = mockk<ChatDao>(relaxed = true)
    private val messageDao = mockk<MessageDao>(relaxed = true)
    private val cryptoManager = mockk<CryptoManagerPort>(relaxed = true)
    private val cloudSyncPort = mockk<CloudSyncPort>(relaxed = true)
    private val loggingPort = mockk<LoggingPort>(relaxed = true)

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        every { cryptoManager.hasCachedKey() } returns true
    }

    @Test
    fun `doWork returns success when no pending data`() = runTest {
        coEvery { chatDao.getPendingSyncChats() } returns emptyList()
        coEvery { messageDao.getPendingSyncMessages() } returns emptyList()

        val worker = TestListenableWorkerBuilder<SyncWorker>(context)
            .setWorkerFactory(SyncWorkerFactory())
            .build()

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
    }

    @Test
    fun `doWork returns retry when vault is locked`() = runTest {
        every { cryptoManager.hasCachedKey() } returns false
        coEvery { chatDao.getPendingSyncChats() } returns listOf(mockk())

        val worker = TestListenableWorkerBuilder<SyncWorker>(context)
            .setWorkerFactory(SyncWorkerFactory())
            .build()

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.retry(), result)
        coVerify(exactly = 0) { cloudSyncPort.syncEntitiesToCloud(any(), any()) }
    }

    @Test
    fun `doWork syncs data and marks as synced on success`() = runTest {
        every { cryptoManager.hasCachedKey() } returns true
        
        val chatId = ChatId("chat-1")
        val messageId = MessageId("msg-1")
        
        val chatEntity = ChatEntity(chatId, "Chat", Date(), Date(), false, pendingSync = true)
        val messageEntity = MessageEntity(messageId, chatId, "Content", role = Role.USER, pendingSync = true)
        val messageWithFiles = MessageWithFiles(messageEntity, emptyList())

        coEvery { chatDao.getPendingSyncChats() } returns listOf(chatEntity)
        coEvery { messageDao.getPendingSyncMessages() } returns listOf(messageWithFiles)
        coEvery { cloudSyncPort.syncEntitiesToCloud(any(), any()) } returns Result.success(Unit)

        val worker = TestListenableWorkerBuilder<SyncWorker>(context)
            .setWorkerFactory(SyncWorkerFactory())
            .build()

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        coVerify { cloudSyncPort.syncEntitiesToCloud(any(), any()) }
        coVerify { chatDao.markSynced(listOf(chatId)) }
        coVerify { messageDao.markSynced(listOf(messageId)) }
    }

    private inner class SyncWorkerFactory : androidx.work.WorkerFactory() {
        override fun createWorker(
            appContext: Context,
            workerClassName: String,
            workerParameters: WorkerParameters
        ): ListenableWorker? {
            return SyncWorker(
                appContext,
                workerParameters,
                chatDao,
                messageDao,
                cryptoManager,
                cloudSyncPort,
                loggingPort
            )
        }
    }
}
