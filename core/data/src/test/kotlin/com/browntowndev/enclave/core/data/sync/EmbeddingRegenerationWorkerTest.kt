package com.browntowndev.enclave.core.data.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
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
import com.browntowndev.enclave.domain.model.memory.Memory
import com.browntowndev.enclave.domain.model.memory.MemoryCategory
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension
import java.util.concurrent.Executor

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [34])
class EmbeddingRegenerationWorkerTest {
    private lateinit var context: Context
    private lateinit var workManager: WorkManager
    private lateinit var database: EnclaveDatabase

    private val coordinatorScope = TestScope()
    private lateinit var coordinator: EmbeddingRegenerationCoordinator
    private lateinit var progressManager: EmbeddingProgressManager

    private val embeddingEngine = FakeEmbeddingEngine()
    private val messageRepository = FakeMessageRepository()
    private val memoriesRepository = FakeMemoriesRepository()
    private val documentDao = FakeDocumentDao()
    private val embeddingDao = FakeEmbeddingDao()
    private val loggingPort = FakeLoggingPort()

    @BeforeEach
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        // Holding background executor: coordinator-scheduled work stays ENQUEUED.
        val config =
            Configuration
                .Builder()
                .setTaskExecutor(androidx.work.impl.utils.SynchronousExecutor())
                .setExecutor(Executor { })
                .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)
        workManager = WorkManager.getInstance(context)
        database =
            Room
                .inMemoryDatabaseBuilder(context, EnclaveDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        coordinator = EmbeddingRegenerationCoordinator(workManager, coordinatorScope)
        progressManager = EmbeddingProgressManager(workManager, TestScope())
        embeddingEngine.uniformEmbedding = floatArrayOf(1.0f, 2.0f)

        messageRepository.seed(
            listOf(
                Message(MessageId("msg1"), ChatId("chat1"), Content("test content")),
                Message(MessageId("msg2"), ChatId("chat1"), Content("test content")),
            ),
        )
        memoriesRepository.seed(
            listOf(Memory(id = "mem1", category = MemoryCategory.FACTS, content = "memory content")),
        )
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

    /** Runs the coordinator scope until the enqueues (and debounce) settle in-memory. */
    private fun settleCoordinator() = coordinatorScope.testScheduler.advanceUntilIdle()

    @Test
    fun `worker drains coordinator queue`() =
        runTest {
            coordinator.enqueueMessage("msg1")
            coordinator.enqueueMemory("mem1")
            settleCoordinator()

            val worker = createWorker()
            val result = worker.doWork()

            assertEquals(ListenableWorker.Result.success(), result)
            // The worker consumed the queue: nothing remains pending afterwards.
            assertTrue(coordinator.consumePending().isEmpty())
        }

    @Test
    fun `worker handles empty queue gracefully`() =
        runTest {
            val worker = createWorker()
            val result = worker.doWork()

            assertEquals(ListenableWorker.Result.success(), result)
        }

    @Test
    fun `batches multiple changes into a single engine session`() =
        runTest {
            coordinator.enqueueMessage("msg1")
            coordinator.enqueueMessage("msg2")
            coordinator.enqueueMemory("mem1")
            settleCoordinator()

            val worker = createWorker()
            worker.doWork()

            // It processed everything in the one session: metadata exists for all three ids.
            assertEquals(setOf("msg1", "msg2", "mem1"), embeddingDao.metadata.keys)
        }

    @Test
    fun `engine failure triggers retry and re-enqueues items`() =
        runTest {
            embeddingEngine.failure = RuntimeException("Engine error")
            coordinator.enqueueMessage("msg1")
            settleCoordinator()

            val worker = createWorker()
            val result = worker.doWork()

            assertEquals(ListenableWorker.Result.retry(), result)
            // markFailed put the failed items back into the coordinator's pending queue.
            assertTrue(coordinator.consumePending().messageIds.contains("msg1"))
        }

    private fun createWorker(): EmbeddingRegenerationWorker =
        TestListenableWorkerBuilder<EmbeddingRegenerationWorker>(context)
            .setWorkerFactory(EmbeddingRegenerationWorkerFactory())
            .build()

    private inner class EmbeddingRegenerationWorkerFactory : androidx.work.WorkerFactory() {
        override fun createWorker(
            appContext: Context,
            workerClassName: String,
            workerParameters: WorkerParameters,
        ): ListenableWorker =
            EmbeddingRegenerationWorker(
                appContext,
                workerParameters,
                coordinator,
                messageRepository,
                memoriesRepository,
                messageDao(),
                database.memoriesDao(),
                documentDao,
                embeddingDao,
                embeddingEngine,
                progressManager,
                loggingPort,
                FakeLocalDataEncryptionService(),
            )
    }
}
