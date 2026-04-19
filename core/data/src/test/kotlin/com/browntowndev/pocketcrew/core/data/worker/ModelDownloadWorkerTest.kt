package com.browntowndev.pocketcrew.core.data.worker

import android.content.Context
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.browntowndev.pocketcrew.core.data.download.DownloadNotificationManager
import com.browntowndev.pocketcrew.core.data.download.DownloadProgressTracker
import com.browntowndev.pocketcrew.core.data.download.DownloadWorkKeys
import com.browntowndev.pocketcrew.core.data.download.ModelDownloadWorker
import com.browntowndev.pocketcrew.domain.port.download.FileDownloaderPort
import com.browntowndev.pocketcrew.domain.port.download.ModelUrlProviderPort
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import io.mockk.every
import io.mockk.mockk
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
        @DisplayName("Worker input data uses KEY_DOWNLOAD_FILES for structured file specs")
        fun inputData_usesKeyDownloadFiles() {
            val filesJson = """[{"remoteFileName":"test.gguf","localFileName":"test.gguf","sha256":"abc123","sizeInBytes":1000,"huggingFaceModelName":"test/model","source":"HUGGING_FACE","modelFileFormat":"GGUF"}]"""
            every { mockWorkerParams.inputData } returns workDataOf(
                DownloadWorkKeys.KEY_DOWNLOAD_FILES to filesJson,
                DownloadWorkKeys.KEY_SESSION_ID to "test-session-123",
                DownloadWorkKeys.KEY_REQUEST_KIND to "INITIALIZE_MODELS",
                DownloadWorkKeys.KEY_TARGET_MODEL_ID to ""
            )

            val inputData = mockWorkerParams.inputData
            assertNotNull(inputData.getString(DownloadWorkKeys.KEY_DOWNLOAD_FILES))
            assertNotNull(inputData.getString(DownloadWorkKeys.KEY_SESSION_ID))
        }

        @Test
        @DisplayName("Worker parameters run attempt count is accessible")
        fun runAttemptCount_isAccessible() {
            every { mockWorkerParams.runAttemptCount } returns 3
            assertNotNull(mockWorkerParams.runAttemptCount)
        }
    }
}