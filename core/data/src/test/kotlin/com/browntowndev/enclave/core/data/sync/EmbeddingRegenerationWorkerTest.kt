package com.browntowndev.enclave.core.data.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.browntowndev.enclave.core.data.local.DocumentDao
import com.browntowndev.enclave.core.data.local.EmbeddingDao
import com.browntowndev.enclave.core.data.repository.FakeLocalDataEncryptionService
import com.browntowndev.enclave.domain.port.inference.EmbeddingEnginePort
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import com.browntowndev.enclave.domain.port.repository.MemoriesRepository
import com.browntowndev.enclave.domain.port.repository.MessageRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [34])
class EmbeddingRegenerationWorkerTest {
    private lateinit var context: Context
    private val coordinator = mockk<EmbeddingRegenerationCoordinator>(relaxed = true)
    private val embeddingEngine = mockk<EmbeddingEnginePort>(relaxed = true)
    private val messageRepository = mockk<MessageRepository>(relaxed = true)
    private val memoriesRepository = mockk<MemoriesRepository>(relaxed = true)
    private val messageDao = mockk<com.browntowndev.enclave.core.data.local.MessageDao>(relaxed = true)
    private val memoriesDao = mockk<com.browntowndev.enclave.core.data.local.MemoriesDao>(relaxed = true)
    private val documentDao = mockk<DocumentDao>(relaxed = true)
    private val embeddingDao = mockk<EmbeddingDao>(relaxed = true)
    private val progressManager = mockk<EmbeddingProgressManager>(relaxed = true)
    private val loggingPort = mockk<LoggingPort>(relaxed = true)

    @BeforeEach
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        val mockMessage = mockk<com.browntowndev.enclave.domain.model.chat.Message>(relaxed = true)
        val mockContent = mockk<com.browntowndev.enclave.domain.model.chat.Content>(relaxed = true)
        every { mockMessage.content } returns mockContent
        every { mockContent.text } returns "test content"
        every { mockMessage.id } returns
            com.browntowndev.enclave.domain.model.chat
                .MessageId("test-id")

        coEvery { embeddingEngine.getEmbedding(any()) } returns floatArrayOf(1.0f, 2.0f)
        every { memoriesRepository.getAllMemoriesFlow() } returns
            kotlinx.coroutines.flow.flowOf(emptyList<com.browntowndev.enclave.domain.model.memory.Memory>())
        coEvery { messageRepository.getMessageById(any()) } returns mockMessage
        coEvery { memoriesRepository.getMemoryById(any()) } returns mockk(relaxed = true)
        coEvery { coordinator.consumePending() } returns PendingWork(emptyList(), emptyList(), emptyList())
    }

    @Test
    fun `worker drains coordinator queue`() =
        runTest {
            coEvery { coordinator.consumePending() } returns
                PendingWork(
                    messageIds = listOf("msg1"),
                    memoryIds = listOf("mem1"),
                    documentIds = emptyList(),
                ) andThen PendingWork(emptyList(), emptyList(), emptyList())

            val worker = createWorker()
            val result = worker.doWork()

            assertEquals(ListenableWorker.Result.success(), result)
            coVerify { coordinator.consumePending() }
        }

    @Test
    fun `worker handles empty queue gracefully`() =
        runTest {
            coEvery { coordinator.consumePending() } returns PendingWork(emptyList(), emptyList(), emptyList())

            val worker = createWorker()
            val result = worker.doWork()

            assertEquals(ListenableWorker.Result.success(), result)
        }

    @Test
    fun `batches multiple changes into a single engine session`() =
        runTest {
            coEvery { coordinator.consumePending() } returns
                PendingWork(
                    messageIds = listOf("msg1", "msg2"),
                    memoryIds = listOf("mem1"),
                    documentIds = emptyList(),
                )

            val worker = createWorker()
            worker.doWork()

            // Verify it processed everything
            coVerify(atLeast = 1) { messageRepository.getMessageById(any()) }
            coVerify(atLeast = 1) { memoriesRepository.getMemoryById(any()) }
        }

    @Test
    fun `engine failure triggers retry and re-enqueues items`() =
        runTest {
            coEvery { coordinator.consumePending() } returns
                PendingWork(
                    messageIds = listOf("msg1"),
                    memoryIds = emptyList(),
                    documentIds = emptyList(),
                )
            coEvery { embeddingEngine.getEmbedding(any()) } throws RuntimeException("Engine error")

            val worker = createWorker()
            val result = worker.doWork()

            assertEquals(ListenableWorker.Result.retry(), result)
            coVerify { coordinator.markFailed(any()) }
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
                messageDao,
                memoriesDao,
                documentDao,
                embeddingDao,
                embeddingEngine,
                progressManager,
                loggingPort,
                FakeLocalDataEncryptionService(),
            )
    }
}
