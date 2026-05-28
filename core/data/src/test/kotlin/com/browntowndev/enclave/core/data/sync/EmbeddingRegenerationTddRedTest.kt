package com.browntowndev.enclave.core.data.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.browntowndev.enclave.core.data.local.DocumentDao
import com.browntowndev.enclave.core.data.local.DocumentEntity
import com.browntowndev.enclave.core.data.local.EmbeddingDao
import com.browntowndev.enclave.core.data.security.LocalDataEncryptionService
import com.browntowndev.enclave.domain.port.inference.EmbeddingEnginePort
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import com.browntowndev.enclave.domain.port.repository.MemoriesRepository
import com.browntowndev.enclave.domain.port.repository.MessageRepository
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [34])
class EmbeddingRegenerationTddRedTest {
    @Suppress("UNCHECKED_CAST")
    private fun makeWorkerParams(): WorkerParameters {
        val params = mockk<WorkerParameters>(relaxed = true)
        val updater = mockk<androidx.work.ProgressUpdater>(relaxed = true)
        every { params.progressUpdater } returns updater
        every { updater.updateProgress(any(), any(), any()) } returns (Futures.immediateFuture(null) as ListenableFuture<Void>)
        return params
    }

    @Test
    fun `SCENARIO-09 Embedding regeneration coordinator debounces worker scheduling`() =
        runTest {
            val workManager = mockk<androidx.work.WorkManager>(relaxed = true)
            val coordinator = EmbeddingRegenerationCoordinator(workManager, this)

            coordinator.enqueueMessage("m1")
            testScheduler.advanceTimeBy(100)
            coordinator.enqueueMessage("m2")
            testScheduler.advanceTimeBy(100)
            coordinator.enqueueDocument("d1")

            // 200ms has elapsed since the first enqueue. Delay for 499ms more.
            testScheduler.advanceTimeBy(499)
            verify(exactly = 0) {
                workManager.enqueueUniqueWork(
                    "enclave_embedding_regen",
                    any<androidx.work.ExistingWorkPolicy>(),
                    any<androidx.work.OneTimeWorkRequest>(),
                )
            }

            // Advance 2ms more to cross the 500ms debounce window from last enqueue.
            testScheduler.advanceTimeBy(2)
            verify(exactly = 1) {
                workManager.enqueueUniqueWork(
                    "enclave_embedding_regen",
                    any<androidx.work.ExistingWorkPolicy>(),
                    any<androidx.work.OneTimeWorkRequest>(),
                )
            }

            val pending = coordinator.consumePending()
            assertEquals(listOf("m1", "m2"), pending.messageIds)
            assertEquals(listOf("d1"), pending.documentIds)
            assertFalse(pending.fullScanPending)
        }

    @Test
    fun `SCENARIO-10 Embedding coordinator retains failed pending work`() =
        runTest {
            val workManager = mockk<androidx.work.WorkManager>(relaxed = true)
            val coordinator = EmbeddingRegenerationCoordinator(workManager, this)

            coordinator.enqueueMessage("m1")
            coordinator.enqueueDocument("d1")
            testScheduler.advanceTimeBy(501)
            // First debounce fires → 1 enqueueUniqueWork call.
            verify(exactly = 1) {
                workManager.enqueueUniqueWork(
                    "enclave_embedding_regen",
                    any<androidx.work.ExistingWorkPolicy>(),
                    any<androidx.work.OneTimeWorkRequest>(),
                )
            }

            // Simulate: worker consumes, processing fails → items re-queued via markFailed.
            val pending = coordinator.consumePending()
            assertEquals(listOf("m1"), pending.messageIds)
            assertEquals(listOf("d1"), pending.documentIds)

            coordinator.markFailed(pending) // m1 and d1 are back in the pending queue

            // Worker finishes → hasPendingLocked() is true → triggerWorker() → new debounce started.
            coordinator.onWorkerFinished()

            // 499ms in — second debounce has not yet fired.
            testScheduler.advanceTimeBy(499)
            verify(exactly = 1) {
                workManager.enqueueUniqueWork(
                    "enclave_embedding_regen",
                    any<androidx.work.ExistingWorkPolicy>(),
                    any<androidx.work.OneTimeWorkRequest>(),
                )
            }

            // Cross the 500ms window → second debounce fires → second enqueueUniqueWork call.
            testScheduler.advanceTimeBy(2)
            verify(exactly = 2) {
                workManager.enqueueUniqueWork(
                    "enclave_embedding_regen",
                    any<androidx.work.ExistingWorkPolicy>(),
                    any<androidx.work.OneTimeWorkRequest>(),
                )
            }
        }

    @Test
    fun `SCENARIO-11 Embedding worker propagates cancellation and retries recoverable failures`() =
        runTest {
            val coordinator = mockk<EmbeddingRegenerationCoordinator>(relaxed = true)
            val messageRepository = mockk<MessageRepository>(relaxed = true)
            val memoriesRepository = mockk<MemoriesRepository>(relaxed = true)
            val messageDao = mockk<com.browntowndev.enclave.core.data.local.MessageDao>(relaxed = true)
            val memoriesDao = mockk<com.browntowndev.enclave.core.data.local.MemoriesDao>(relaxed = true)
            val documentDao = mockk<com.browntowndev.enclave.core.data.local.DocumentDao>(relaxed = true)
            val embeddingDao = mockk<com.browntowndev.enclave.core.data.local.EmbeddingDao>(relaxed = true)
            val embeddingEngine = mockk<EmbeddingEnginePort>(relaxed = true)
            val progressManager = mockk<EmbeddingProgressManager>(relaxed = true)
            val loggingPort = mockk<LoggingPort>(relaxed = true)
            val localEncryption = mockk<LocalDataEncryptionService>(relaxed = true)

            // Recoverable database error retry test:
            coEvery { coordinator.consumePending() } returnsMany
                listOf(
                    PendingWork(messageIds = listOf("msg1"), memoryIds = emptyList(), documentIds = emptyList()),
                    PendingWork(emptyList(), emptyList(), emptyList()),
                )
            coEvery { messageRepository.getMessageById(any()) } throws RuntimeException("DB locked timeout")

            val context = ApplicationProvider.getApplicationContext<Context>()
            val worker =
                TestListenableWorkerBuilder<EmbeddingRegenerationWorker>(context)
                    .setWorkerFactory(
                        object : androidx.work.WorkerFactory() {
                            override fun createWorker(
                                appContext: Context,
                                workerClassName: String,
                                workerParameters: WorkerParameters,
                            ) = EmbeddingRegenerationWorker(
                                appContext,
                                workerParameters,
                                coordinator,
                                messageRepository,
                                memoriesRepository,
                                messageDao,
                                memoriesDao,
                                documentDao,
                                embeddingDao,
                                embeddingEngine,
                                progressManager,
                                loggingPort,
                                localEncryption,
                            )
                        },
                    ).build()

            val result = worker.doWork()
            // RuntimeException (non-cancellation) from message processing must surface as retry
            assertEquals(ListenableWorker.Result.retry(), result)
        }

    @Test
    fun `SCENARIO-12 Embedding worker writes progress through WorkManager progress data`() =
        runTest {
            val coordinator = mockk<EmbeddingRegenerationCoordinator>(relaxed = true)
            val messageRepository = mockk<MessageRepository>(relaxed = true)
            val memoriesRepository = mockk<MemoriesRepository>(relaxed = true)
            val messageDao = mockk<com.browntowndev.enclave.core.data.local.MessageDao>(relaxed = true)
            val memoriesDao = mockk<com.browntowndev.enclave.core.data.local.MemoriesDao>(relaxed = true)
            val documentDao = mockk<com.browntowndev.enclave.core.data.local.DocumentDao>(relaxed = true)
            val embeddingDao = mockk<com.browntowndev.enclave.core.data.local.EmbeddingDao>(relaxed = true)
            val embeddingEngine = mockk<EmbeddingEnginePort>(relaxed = true)
            val progressManager = mockk<EmbeddingProgressManager>(relaxed = true)
            val loggingPort = mockk<LoggingPort>(relaxed = true)
            val localEncryption = mockk<LocalDataEncryptionService>(relaxed = true)

            val params = mockk<WorkerParameters>(relaxed = true)
            val progressUpdater = mockk<androidx.work.ProgressUpdater>(relaxed = true)
            every { params.progressUpdater } returns progressUpdater
            // CoroutineWorker.setProgress() calls progressUpdater.updateProgress(...).await().
            // Must return a completed ListenableFuture — not a GMS Task — so await() resolves.
            @Suppress("UNCHECKED_CAST")
            every { progressUpdater.updateProgress(any(), any(), any()) } returns (Futures.immediateFuture(null) as ListenableFuture<Void>)

            coEvery { coordinator.consumePending() } returnsMany
                listOf(
                    PendingWork(messageIds = listOf("m1", "m2", "m3", "m4"), memoryIds = emptyList(), documentIds = emptyList()),
                    PendingWork(emptyList(), emptyList(), emptyList()),
                )
            val mockMsg = mockk<com.browntowndev.enclave.domain.model.chat.Message>(relaxed = true)
            every { mockMsg.content.text } returns "hello"
            coEvery { messageRepository.getMessageById(any()) } returns mockMsg
            coEvery { embeddingEngine.getEmbedding(any()) } returns floatArrayOf(0f)
            coEvery { messageRepository.saveEmbedding(any(), any()) } just runs

            val context = mockk<android.content.Context>(relaxed = true)
            val worker =
                EmbeddingRegenerationWorker(
                    context,
                    params,
                    coordinator,
                    messageRepository,
                    memoriesRepository,
                    messageDao,
                    memoriesDao,
                    documentDao,
                    embeddingDao,
                    embeddingEngine,
                    progressManager,
                    loggingPort,
                    localEncryption,
                )

            worker.doWork()

            coVerify {
                progressUpdater.updateProgress(
                    any(),
                    any(),
                    match { data ->
                        data.getInt("current", -1) == 2 &&
                            data.getInt("total", -1) == 4 &&
                            data.getFloat("fraction", -1f) == 0.5f
                    },
                )
            }

            // Verify worker did not call updateProgress directly on EmbeddingProgressManager
            verify(exactly = 0) { progressManager.updateProgress(any<Int>(), any<Int>()) }
        }

    @Test
    fun `SCENARIO-13 Embedding worker skips malformed documents without failing the worker`() =
        runTest {
            val coordinator = mockk<EmbeddingRegenerationCoordinator>(relaxed = true)
            val messageRepository = mockk<MessageRepository>(relaxed = true)
            val memoriesRepository = mockk<MemoriesRepository>(relaxed = true)
            val messageDao = mockk<com.browntowndev.enclave.core.data.local.MessageDao>(relaxed = true)
            val memoriesDao = mockk<com.browntowndev.enclave.core.data.local.MemoriesDao>(relaxed = true)
            val documentDao = mockk<DocumentDao>(relaxed = true)
            val embeddingDao = mockk<EmbeddingDao>(relaxed = true)
            val embeddingEngine = mockk<EmbeddingEnginePort>(relaxed = true)
            val progressManager = mockk<EmbeddingProgressManager>(relaxed = true)
            val loggingPort = mockk<LoggingPort>(relaxed = true)
            val localEncryption = mockk<LocalDataEncryptionService>(relaxed = true)

            coEvery { coordinator.consumePending() } returnsMany
                listOf(
                    PendingWork(messageIds = emptyList(), memoryIds = emptyList(), documentIds = listOf("doc1", "doc2", "doc3")),
                    PendingWork(emptyList(), emptyList(), emptyList()),
                )

            val doc1 =
                DocumentEntity(
                    "doc1",
                    com.browntowndev.enclave.domain.model.chat
                        .MessageId("m1"),
                    "json1",
                    "title1",
                )
            val doc2 =
                DocumentEntity(
                    "doc2",
                    com.browntowndev.enclave.domain.model.chat
                        .MessageId("m2"),
                    "json2",
                    "title2",
                )
            val doc3 =
                DocumentEntity(
                    "doc3",
                    com.browntowndev.enclave.domain.model.chat
                        .MessageId("m3"),
                    "json3",
                    "title3",
                )

            coEvery { documentDao.getDocumentById("doc1") } returns doc1
            coEvery { documentDao.getDocumentById("doc2") } returns doc2
            coEvery { documentDao.getDocumentById("doc3") } returns doc3

            every { localEncryption.decryptString("json1", any()) } returns """{"blocks": [{"type":"paragraph","text":"content1"}]}"""
            every { localEncryption.decryptString("json3", any()) } returns """{"blocks": [{"type":"paragraph","text":"content3"}]}"""
            every { localEncryption.decryptString("json2", any()) } throws RuntimeException("Decryption failed")

            val context = mockk<android.content.Context>(relaxed = true)
            val worker =
                EmbeddingRegenerationWorker(
                    context,
                    makeWorkerParams(),
                    coordinator,
                    messageRepository,
                    memoriesRepository,
                    messageDao,
                    memoriesDao,
                    documentDao,
                    embeddingDao,
                    embeddingEngine,
                    progressManager,
                    loggingPort,
                    localEncryption,
                )

            val result = worker.doWork()

            assertEquals(
                androidx.work.ListenableWorker.Result
                    .success(),
                result,
            )
            verify(exactly = 1) { loggingPort.error(any(), match { it.contains("Failed to parse document JSON") }, any()) }
            coVerify(exactly = 1) { embeddingEngine.getEmbedding("content1") }
            coVerify(exactly = 1) { embeddingEngine.getEmbedding("content3") }
            coVerify(exactly = 0) { embeddingEngine.getEmbedding("content2") }
        }

    @Test
    fun `SCENARIO-14 Embedding worker serializes engine calls by default`() =
        runTest {
            val coordinator = mockk<EmbeddingRegenerationCoordinator>(relaxed = true)
            val messageRepository = mockk<MessageRepository>(relaxed = true)
            val memoriesRepository = mockk<MemoriesRepository>(relaxed = true)
            val messageDao = mockk<com.browntowndev.enclave.core.data.local.MessageDao>(relaxed = true)
            val memoriesDao = mockk<com.browntowndev.enclave.core.data.local.MemoriesDao>(relaxed = true)
            val documentDao = mockk<DocumentDao>(relaxed = true)
            val embeddingDao = mockk<EmbeddingDao>(relaxed = true)
            val embeddingEngine = mockk<EmbeddingEnginePort>(relaxed = true)
            val progressManager = mockk<EmbeddingProgressManager>(relaxed = true)
            val loggingPort = mockk<LoggingPort>(relaxed = true)
            val localEncryption = mockk<LocalDataEncryptionService>(relaxed = true)

            every { embeddingEngine.maxConcurrentEmbeddings } returns 1

            coEvery { coordinator.consumePending() } returnsMany
                listOf(
                    PendingWork(messageIds = listOf("m1", "m2", "m3", "m4", "m5"), memoryIds = emptyList(), documentIds = emptyList()),
                    PendingWork(emptyList(), emptyList(), emptyList()),
                )

            val mockMsg = mockk<com.browntowndev.enclave.domain.model.chat.Message>(relaxed = true)
            every { mockMsg.content.text } returns "hello"
            coEvery { messageRepository.getMessageById(any()) } returns mockMsg

            var activeCalls = 0
            var maxConcurrentActiveCalls = 0
            val lock = Any()

            coEvery { embeddingEngine.getEmbedding(any()) } coAnswers {
                synchronized(lock) {
                    activeCalls++
                    maxConcurrentActiveCalls = maxOf(maxConcurrentActiveCalls, activeCalls)
                }
                kotlinx.coroutines.delay(50)
                synchronized(lock) {
                    activeCalls--
                }
                floatArrayOf()
            }

            val context = mockk<android.content.Context>(relaxed = true)
            val worker =
                EmbeddingRegenerationWorker(
                    context,
                    makeWorkerParams(),
                    coordinator,
                    messageRepository,
                    memoriesRepository,
                    messageDao,
                    memoriesDao,
                    documentDao,
                    embeddingDao,
                    embeddingEngine,
                    progressManager,
                    loggingPort,
                    localEncryption,
                )

            worker.doWork()

            assertEquals(1, maxConcurrentActiveCalls)
        }

    @Test
    fun `SCENARIO-15 Embedding worker processes in parallel only when engine supports concurrent embeddings`() =
        runTest {
            val coordinator = mockk<EmbeddingRegenerationCoordinator>(relaxed = true)
            val messageRepository = mockk<MessageRepository>(relaxed = true)
            val memoriesRepository = mockk<MemoriesRepository>(relaxed = true)
            val messageDao = mockk<com.browntowndev.enclave.core.data.local.MessageDao>(relaxed = true)
            val memoriesDao = mockk<com.browntowndev.enclave.core.data.local.MemoriesDao>(relaxed = true)
            val documentDao = mockk<DocumentDao>(relaxed = true)
            val embeddingDao = mockk<EmbeddingDao>(relaxed = true)
            val embeddingEngine = mockk<EmbeddingEnginePort>(relaxed = true)
            val progressManager = mockk<EmbeddingProgressManager>(relaxed = true)
            val loggingPort = mockk<LoggingPort>(relaxed = true)
            val localEncryption = mockk<LocalDataEncryptionService>(relaxed = true)

            every { embeddingEngine.maxConcurrentEmbeddings } returns 4

            coEvery { coordinator.consumePending() } returnsMany
                listOf(
                    PendingWork(messageIds = (1..10).map { "m$it" }, memoryIds = emptyList(), documentIds = emptyList()),
                    PendingWork(emptyList(), emptyList(), emptyList()),
                )

            val mockMsg = mockk<com.browntowndev.enclave.domain.model.chat.Message>(relaxed = true)
            every { mockMsg.content.text } returns "hello"
            coEvery { messageRepository.getMessageById(any()) } returns mockMsg

            var activeCalls = 0
            var maxConcurrentActiveCalls = 0
            val lock = Any()

            coEvery { embeddingEngine.getEmbedding(any()) } coAnswers {
                synchronized(lock) {
                    activeCalls++
                    maxConcurrentActiveCalls = maxOf(maxConcurrentActiveCalls, activeCalls)
                }
                kotlinx.coroutines.delay(50)
                synchronized(lock) {
                    activeCalls--
                }
                floatArrayOf()
            }

            val context = mockk<android.content.Context>(relaxed = true)
            val worker =
                EmbeddingRegenerationWorker(
                    context,
                    makeWorkerParams(),
                    coordinator,
                    messageRepository,
                    memoriesRepository,
                    messageDao,
                    memoriesDao,
                    documentDao,
                    embeddingDao,
                    embeddingEngine,
                    progressManager,
                    loggingPort,
                    localEncryption,
                )

            worker.doWork()

            assertTrue(maxConcurrentActiveCalls > 1, "Should run concurrently")
            assertTrue(maxConcurrentActiveCalls <= 4, "Should not exceed max concurrency of 4")
        }
}
