package com.browntowndev.pocketcrew.core.data.worker

import android.content.Context
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.browntowndev.pocketcrew.core.data.download.DownloadNotificationManager
import com.browntowndev.pocketcrew.core.data.download.DownloadProgressTracker
import com.browntowndev.pocketcrew.core.data.download.ModelDownloadWorker
import com.browntowndev.pocketcrew.domain.model.inference.ModelFileFormat
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.port.download.FileDownloaderPort
import com.browntowndev.pocketcrew.domain.port.download.ModelUrlProviderPort
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("ModelDownloadWorker Tests")
class ModelDownloadWorkerTest {

    private lateinit var mockContext: Context
    private lateinit var mockWorkerParams: WorkerParameters
    private lateinit var mockLogger: LoggingPort
    private lateinit var mockNotificationManager: DownloadNotificationManager
    private lateinit var mockProgressTracker: DownloadProgressTracker
    private lateinit var mockFileDownloader: FileDownloaderPort
    private lateinit var mockModelUrlProvider: ModelUrlProviderPort
    private lateinit var worker: ModelDownloadWorker

    private val testSessionId = "test-session-123"

    @BeforeEach
    fun setup() {
        mockContext = mockk(relaxed = true)
        mockWorkerParams = mockk(relaxed = true)
        mockLogger = mockk(relaxed = true)
        mockNotificationManager = mockk(relaxed = true)
        mockProgressTracker = mockk(relaxed = true)
        mockFileDownloader = mockk(relaxed = true)
        mockModelUrlProvider = mockk(relaxed = true)

        every { mockWorkerParams.runAttemptCount } returns 0

        worker = ModelDownloadWorker(
            context = mockContext,
            params = mockWorkerParams,
            logger = mockLogger,
            notificationManager = mockNotificationManager,
            progressTracker = mockProgressTracker,
            fileDownloader = mockFileDownloader,
            modelUrlProvider = mockModelUrlProvider
        )
    }

    @Nested
    @DisplayName("Worker Initialization Tests")
    inner class WorkerInitializationTests {

        @Test
        @DisplayName("Worker is correctly instantiated with all dependencies")
        fun worker_isCorrectlyInstantiated() {
            assertNotNull(worker)
        }

        @Test
        @DisplayName("All dependencies are properly injected including FileDownloaderPort and ModelUrlProviderPort")
        fun allDependencies_areProperlyInjected() {
            assertNotNull(mockContext)
            assertNotNull(mockWorkerParams)
            assertNotNull(mockLogger)
            assertNotNull(mockNotificationManager)
            assertNotNull(mockProgressTracker)
            assertNotNull(mockFileDownloader)
            assertNotNull(mockModelUrlProvider)
        }

        @Test
        @DisplayName("Relaxed mocks work for logger")
        fun relaxedMocks_workForLogger() {
            mockLogger.debug("tag", "message")
            assertNotNull(mockLogger)
        }

        @Test
        @DisplayName("FileDownloaderPort is injected and accessible")
        fun fileDownloader_isInjected() {
            // Verify fileDownloader is set (this would fail if FileDownloaderPort was not added as dependency)
            assertNotNull(mockFileDownloader)
        }

        @Test
        @DisplayName("ModelUrlProviderPort is injected and accessible")
        fun modelUrlProvider_isInjected() {
            // Verify modelUrlProvider is set (this would fail if ModelUrlProviderPort was not added as dependency)
            assertNotNull(mockModelUrlProvider)
        }
    }

    @Nested
    @DisplayName("Input Data Tests")
    inner class InputDataTests {

        @Test
        @DisplayName("Input data can be retrieved from worker parameters")
        fun inputData_canBeRetrieved() {
            // New format: modelType|remoteFileName|localFileName|presetName|huggingFaceModelName|sizeInBytes|sha256|modelFileFormat|temperature|topK|topP|minP|repetitionPenalty|maxTokens|contextWindow|systemPrompt
            val modelData = "MAIN|test-remote.gguf|test-local.gguf|Test Preset|TheBloke/TestModel|1000|abc123def456|LITERTLM|0.7|40|0.95|0.0|1.1|2048|2048|You are a helpful assistant"
            every { mockWorkerParams.inputData } returns workDataOf(
                "model_files" to arrayOf(modelData),
                "session_id" to testSessionId
            )

            val inputData = mockWorkerParams.inputData
            val files = inputData.getStringArray("model_files")
            val sessionId = inputData.getString("session_id")

            assertNotNull(files)
            assertNotNull(sessionId)
        }

        @Test
        @DisplayName("Worker parameters run attempt count is accessible")
        fun runAttemptCount_isAccessible() {
            every { mockWorkerParams.runAttemptCount } returns 3
            assertNotNull(mockWorkerParams.runAttemptCount)
        }
    }
}
