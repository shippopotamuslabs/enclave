package com.browntowndev.enclave.core.data.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import com.browntowndev.enclave.core.data.local.DocumentEntity
import com.browntowndev.enclave.core.data.local.EnclaveDatabase
import com.browntowndev.enclave.core.data.local.MessageDao
import com.browntowndev.enclave.core.data.local.dao.message.MessageDaoPersist
import com.browntowndev.enclave.core.data.repository.FakeLocalDataEncryptionService
import com.browntowndev.enclave.core.testing.FakeEmbeddingEngine
import com.browntowndev.enclave.core.testing.FakeLoggingPort
import com.browntowndev.enclave.core.testing.FakeMemoriesRepository
import com.browntowndev.enclave.core.testing.FakeMessageRepository
import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.Content
import com.browntowndev.enclave.domain.model.chat.Message
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.port.inference.EmbeddingEnginePort
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [34])
class EmbeddingRegenerationTddRedTest {
    private lateinit var context: Context
    private lateinit var workManager: WorkManager
    private lateinit var database: EnclaveDatabase

    /** Scope driving worker-test coordinators; settled explicitly per test. */
    private val workerCoordinatorScope = TestScope()
    private val loggingPort = FakeLoggingPort()

    @BeforeEach
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        // Both executors synchronous (the DownloadWorkScheduler / TestDoubles house
        // pattern): scheduled work stays ENQUEUED under the test driver, so
        // unique-work scheduling is observable as WorkInfo state on the real
        // WorkManager. A no-op main executor drops WorkManager's main-thread
        // state/constraint posts and lets work start mid-test.
        val config =
            Configuration
                .Builder()
                .setTaskExecutor(androidx.work.impl.utils.SynchronousExecutor())
                .setExecutor(androidx.work.impl.utils.SynchronousExecutor())
                .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)
        workManager = WorkManager.getInstance(context)
        database =
            Room
                .inMemoryDatabaseBuilder(context, EnclaveDatabase::class.java)
                .allowMainThreadQueries()
                .build()
    }

    @AfterEach
    fun tearDown() {
        database.close()
    }

    private fun messageDao(): MessageDao =
        MessageDao(
            database.messageQueryDao(),
            database.messageCommandDao(),
            database.messageSyncCommandDao(),
            MessageDaoPersist(database, database.messageCommandDao(), database.messageSyncCommandDao()),
        )

    private fun regenWorkCount(): Int = workManager.getWorkInfosForUniqueWork("enclave_embedding_regen").get().size

    private fun newWorkerCoordinator(): EmbeddingRegenerationCoordinator =
        EmbeddingRegenerationCoordinator(workManager, workerCoordinatorScope)

    private fun settleCoordinator() = workerCoordinatorScope.testScheduler.advanceUntilIdle()

    private fun buildWorker(
        coordinator: EmbeddingRegenerationCoordinator,
        messageRepository: FakeMessageRepository = FakeMessageRepository(),
        embeddingEngine: EmbeddingEnginePort = FakeEmbeddingEngine(),
        documentDao: FakeDocumentDao = FakeDocumentDao(),
        progressManager: EmbeddingProgressManager = EmbeddingProgressManager(workManager, TestScope()),
        params: WorkerParameters = realWorkerParameters(),
    ): EmbeddingRegenerationWorker =
        EmbeddingRegenerationWorker(
            context,
            params,
            coordinator,
            messageRepository,
            FakeMemoriesRepository(),
            messageDao(),
            database.memoriesDao(),
            documentDao,
            FakeEmbeddingDao(),
            embeddingEngine,
            progressManager,
            loggingPort,
            FakeLocalDataEncryptionService(),
        )

    /** [EmbeddingProgressManager] subclass (ENC-28 open seam) recording direct progress calls. */
    private class RecordingProgressManager(
        workManager: WorkManager,
        scope: CoroutineScope,
    ) : EmbeddingProgressManager(workManager, scope) {
        val updateProgressCalls = mutableListOf<Pair<Int, Int>>()

        override fun updateProgress(
            current: Int,
            total: Int,
        ) {
            updateProgressCalls += current to total
            super.updateProgress(current, total)
        }
    }

    @Test
    fun `SCENARIO-09 Embedding regeneration coordinator debounces worker scheduling`() =
        runTest {
            val coordinator = EmbeddingRegenerationCoordinator(workManager, this)

            coordinator.enqueueMessage("m1")
            testScheduler.advanceTimeBy(100)
            coordinator.enqueueMessage("m2")
            testScheduler.advanceTimeBy(100)
            coordinator.enqueueDocument("d1")

            // 200ms has elapsed since the first enqueue. Delay for 499ms more.
            testScheduler.advanceTimeBy(499)
            // No unique work scheduled yet: the 500ms debounce has not fired.
            assertEquals(0, regenWorkCount())

            // Advance 2ms more to cross the 500ms debounce window from last enqueue.
            testScheduler.advanceTimeBy(2)
            assertEquals(1, regenWorkCount())

            val pending = coordinator.consumePending()
            assertEquals(listOf("m1", "m2"), pending.messageIds)
            assertEquals(listOf("d1"), pending.documentIds)
            assertFalse(pending.fullScanPending)
        }

    @Test
    fun `SCENARIO-10 Embedding coordinator retains failed pending work`() =
        runTest {
            val coordinator = EmbeddingRegenerationCoordinator(workManager, this)

            coordinator.enqueueMessage("m1")
            coordinator.enqueueDocument("d1")
            testScheduler.advanceTimeBy(501)
            // First debounce fires → one unique work request scheduled.
            assertEquals(1, regenWorkCount())
            val firstWorkId = workManager.getWorkInfosForUniqueWork("enclave_embedding_regen").get().single().id

            // Simulate: worker consumes, processing fails → items re-queued via markFailed.
            val pending = coordinator.consumePending()
            assertEquals(listOf("m1"), pending.messageIds)
            assertEquals(listOf("d1"), pending.documentIds)

            coordinator.markFailed(pending) // m1 and d1 are back in the pending queue

            // The scheduled worker has finished on the WorkManager side (terminal
            // state), then the coordinator is notified as production does.
            workManager.cancelUniqueWork("enclave_embedding_regen")
            coordinator.onWorkerFinished()

            // 499ms in — second debounce has not yet fired, still one work record
            // (the cancelled first request; nothing new scheduled yet).
            testScheduler.advanceTimeBy(499)
            assertEquals(1, regenWorkCount())

            // Cross the 500ms window → second debounce fires → a second request is
            // scheduled. Against a real WorkManager the cancelled first request's
            // WorkSpec row is deleted by this follow-up enqueue (EnqueueRunnable
            // prune), so the record count stays 1 — the FakeWorkManager-era
            // expectation of 2 records cannot occur. The second scheduling is
            // asserted in its observable form: the single remaining record is
            // ENQUEUED and has a different id than the first request.
            testScheduler.advanceTimeBy(2)
            assertEquals(1, regenWorkCount())
            val secondWork = workManager.getWorkInfosForUniqueWork("enclave_embedding_regen").get().single()
            assertEquals(WorkInfo.State.ENQUEUED, secondWork.state)
            assertTrue(secondWork.id != firstWorkId)
        }

    @Test
    fun `SCENARIO-11 Embedding worker propagates cancellation and retries recoverable failures`() =
        runTest {
            val coordinator = newWorkerCoordinator()
            val messageRepository = FakeMessageRepository()
            // Recoverable database error: the read itself throws.
            messageRepository.getMessageByIdFailure = RuntimeException("DB locked timeout")
            coordinator.enqueueMessage("msg1")
            settleCoordinator()

            val worker =
                TestListenableWorkerBuilder<EmbeddingRegenerationWorker>(context)
                    .setWorkerFactory(
                        object : androidx.work.WorkerFactory() {
                            override fun createWorker(
                                appContext: Context,
                                workerClassName: String,
                                workerParameters: WorkerParameters,
                            ) = buildWorker(
                                coordinator = coordinator,
                                messageRepository = messageRepository,
                                params = workerParameters,
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
            val coordinator = newWorkerCoordinator()
            val messageRepository = FakeMessageRepository()
            messageRepository.seed(
                listOf("m1", "m2", "m3", "m4").map { Message(MessageId(it), ChatId("chat1"), Content("hello")) },
            )
            val embeddingEngine = FakeEmbeddingEngine()
            embeddingEngine.uniformEmbedding = floatArrayOf(0f)
            listOf("m1", "m2", "m3", "m4").forEach { coordinator.enqueueMessage(it) }
            settleCoordinator()

            // CoroutineWorker.setProgress() calls progressUpdater.updateProgress(...),
            // which the recording updater captures with an already-completed future.
            val progressUpdater = RecordingProgressUpdater()
            val progressManager = RecordingProgressManager(workManager, TestScope())
            val worker =
                buildWorker(
                    coordinator = coordinator,
                    messageRepository = messageRepository,
                    embeddingEngine = embeddingEngine,
                    progressManager = progressManager,
                    params = realWorkerParameters(progressUpdater),
                )

            worker.doWork()

            assertTrue(
                progressUpdater.updates.any { data ->
                    data.getInt("current", -1) == 2 &&
                        data.getInt("total", -1) == 4 &&
                        data.getFloat("fraction", -1f) == 0.5f
                },
            )

            // Worker did not call updateProgress directly on EmbeddingProgressManager
            assertEquals(0, progressManager.updateProgressCalls.size)
        }

    @Test
    fun `SCENARIO-13 Embedding worker skips malformed documents without failing the worker`() =
        runTest {
            val coordinator = newWorkerCoordinator()
            // Stored payloads pass through the fake encryption service unchanged:
            // doc1/doc3 hold valid block JSON; doc2's payload is not valid JSON, so
            // parsing it fails exactly where a decryption failure surfaced before.
            val documentDao = FakeDocumentDao()
            documentDao.documents["doc1"] =
                DocumentEntity(
                    "doc1",
                    MessageId("m1"),
                    """{"blocks": [{"type":"paragraph","text":"content1"}]}""",
                    "title1",
                )
            documentDao.documents["doc2"] =
                DocumentEntity(
                    "doc2",
                    MessageId("m2"),
                    "json2",
                    "title2",
                )
            documentDao.documents["doc3"] =
                DocumentEntity(
                    "doc3",
                    MessageId("m3"),
                    """{"blocks": [{"type":"paragraph","text":"content3"}]}""",
                    "title3",
                )
            val embeddingEngine = FakeEmbeddingEngine()
            listOf("doc1", "doc2", "doc3").forEach { coordinator.enqueueDocument(it) }
            settleCoordinator()

            val worker =
                buildWorker(
                    coordinator = coordinator,
                    embeddingEngine = embeddingEngine,
                    documentDao = documentDao,
                )

            val result = worker.doWork()

            assertEquals(
                androidx.work.ListenableWorker.Result
                    .success(),
                result,
            )
            assertEquals(
                1,
                loggingPort.errorCalls.count { it.message.contains("Failed to parse document JSON") },
            )
            assertEquals(1, embeddingEngine.queries.count { it == "content1" })
            assertEquals(1, embeddingEngine.queries.count { it == "content3" })
            assertEquals(0, embeddingEngine.queries.count { it == "content2" })
        }

    @Test
    fun `SCENARIO-14 Embedding worker serializes engine calls by default`() =
        runTest {
            val coordinator = newWorkerCoordinator()
            val messageRepository = FakeMessageRepository()
            messageRepository.seed(
                listOf("m1", "m2", "m3", "m4", "m5").map { Message(MessageId(it), ChatId("chat1"), Content("hello")) },
            )
            val embeddingEngine = ConcurrencyTrackingEmbeddingEngine(maxConcurrentEmbeddings = 1)
            listOf("m1", "m2", "m3", "m4", "m5").forEach { coordinator.enqueueMessage(it) }
            settleCoordinator()

            val worker =
                buildWorker(
                    coordinator = coordinator,
                    messageRepository = messageRepository,
                    embeddingEngine = embeddingEngine,
                )

            worker.doWork()

            assertEquals(1, embeddingEngine.maxConcurrentActiveCalls)
        }

    @Test
    fun `SCENARIO-15 Embedding worker processes in parallel only when engine supports concurrent embeddings`() =
        runTest {
            val coordinator = newWorkerCoordinator()
            val messageRepository = FakeMessageRepository()
            messageRepository.seed(
                (1..10).map { Message(MessageId("m$it"), ChatId("chat1"), Content("hello")) },
            )
            val embeddingEngine = ConcurrencyTrackingEmbeddingEngine(maxConcurrentEmbeddings = 4)
            (1..10).forEach { coordinator.enqueueMessage("m$it") }
            settleCoordinator()

            val worker =
                buildWorker(
                    coordinator = coordinator,
                    messageRepository = messageRepository,
                    embeddingEngine = embeddingEngine,
                )

            worker.doWork()

            assertTrue(embeddingEngine.maxConcurrentActiveCalls > 1, "Should run concurrently")
            assertTrue(embeddingEngine.maxConcurrentActiveCalls <= 4, "Should not exceed max concurrency of 4")
        }
}
