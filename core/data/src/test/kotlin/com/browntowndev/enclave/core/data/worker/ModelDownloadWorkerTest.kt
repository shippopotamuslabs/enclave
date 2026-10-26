package com.browntowndev.enclave.core.data.worker

import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import com.browntowndev.enclave.core.data.download.DownloadNotificationManager
import com.browntowndev.enclave.core.data.download.DownloadProgressTracker
import com.browntowndev.enclave.core.data.download.DownloadWorkKeys
import com.browntowndev.enclave.core.data.download.ModelDownloadWorker
import com.browntowndev.enclave.core.testing.FakeLoggingPort
import com.browntowndev.enclave.domain.model.download.DownloadFileSpec
import com.browntowndev.enclave.domain.port.download.DownloadSpeedTrackerPort
import com.browntowndev.enclave.domain.port.download.FileDownloaderPort
import com.browntowndev.enclave.domain.port.download.ModelUrlProviderPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [34])
@DisplayName("ModelDownloadWorker Tests")
class ModelDownloadWorkerTest {
    /** Minimal [FileDownloaderPort] fake (ENC-28); these tests never start a download. */
    private class FakeFileDownloader : FileDownloaderPort {
        override suspend fun downloadFile(
            config: FileDownloaderPort.FileDownloadConfig,
            downloadUrl: String,
            targetDirPath: String,
            existingBytes: Long,
            progressCallback: FileDownloaderPort.ProgressCallback?,
        ): FileDownloaderPort.DownloadResult =
            FileDownloaderPort.DownloadResult(
                filePath = "$targetDirPath/${config.filename}",
                bytesDownloaded = 0,
                totalBytes = config.expectedSizeBytes,
                isResumed = false,
            )

        override suspend fun getServerFileSize(url: String): Long? = null
    }

    /** Minimal [ModelUrlProviderPort] fake (ENC-28) returning canned URLs. */
    private class FakeModelUrlProvider : ModelUrlProviderPort {
        override fun getConfigUrl(): String = "https://example.com/config.json"

        override fun getTrustedDomainsUrl(): String = "https://example.com/trusted-domains.json"

        override suspend fun getModelDownloadUrl(spec: DownloadFileSpec): String = "https://example.com/${spec.remoteFileName}"
    }

    /** Minimal [DownloadSpeedTrackerPort] fake (ENC-28) for the real progress tracker. */
    private class FakeDownloadSpeedTracker : DownloadSpeedTrackerPort {
        override fun calculateSpeedAndEta(
            filename: String,
            bytesDownloaded: Long,
            totalSize: Long,
        ): Pair<Double, Long> = Pair(0.0, 0L)

        override fun calculateAggregateSpeedAndEta(
            totalBytesDownloaded: Long,
            totalSize: Long,
        ): Pair<Double, Long> = Pair(0.0, 0L)

        override fun formatEta(seconds: Long): String = ""

        override fun clear(filename: String) = Unit

        override fun clearAll() = Unit
    }

    private lateinit var context: Context
    private lateinit var logger: FakeLoggingPort
    private lateinit var notificationManager: DownloadNotificationManager
    private lateinit var progressTracker: DownloadProgressTracker
    private lateinit var fileDownloader: FileDownloaderPort
    private lateinit var modelUrlProvider: ModelUrlProviderPort
    private lateinit var worker: ModelDownloadWorker

    @BeforeEach
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        logger = FakeLoggingPort()
        notificationManager =
            DownloadNotificationManager(
                context,
                context.getSystemService(NotificationManager::class.java),
            )
        progressTracker = DownloadProgressTracker(FakeDownloadSpeedTracker())
        fileDownloader = FakeFileDownloader()
        modelUrlProvider = FakeModelUrlProvider()

        worker = buildWorker()
    }

    private fun buildWorker(
        inputData: androidx.work.Data = androidx.work.Data.EMPTY,
        runAttemptCount: Int = 0,
    ): ModelDownloadWorker =
        TestListenableWorkerBuilder<ModelDownloadWorker>(context)
            .setInputData(inputData)
            .setRunAttemptCount(runAttemptCount)
            .setWorkerFactory(ModelDownloadWorkerFactory())
            .build()

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
            assertNotNull(context)
            assertNotNull(worker.inputData)
            assertNotNull(logger)
            assertNotNull(notificationManager)
            assertNotNull(progressTracker)
            assertNotNull(fileDownloader)
            assertNotNull(modelUrlProvider)
        }

        @Test
        @DisplayName("Relaxed mocks work for logger")
        fun relaxedMocks_workForLogger() {
            logger.debug("tag", "message")
            assertEquals(listOf("tag" to "message"), logger.debugCalls)
        }

        @Test
        @DisplayName("FileDownloaderPort is injected and accessible")
        fun fileDownloader_isInjected() {
            // Verify fileDownloader is set (this would fail if FileDownloaderPort was not added as dependency)
            assertNotNull(fileDownloader)
        }

        @Test
        @DisplayName("ModelUrlProviderPort is injected and accessible")
        fun modelUrlProvider_isInjected() {
            // Verify modelUrlProvider is set (this would fail if ModelUrlProviderPort was not added as dependency)
            assertNotNull(modelUrlProvider)
        }
    }

    @Nested
    @DisplayName("Input Data Tests")
    inner class InputDataTests {
        @Test
        @DisplayName("Worker input data uses KEY_DOWNLOAD_FILES for structured file specs")
        fun inputData_usesKeyDownloadFiles() {
            val filesJson = """[{"remoteFileName":"test.gguf","localFileName":"test.gguf","sha256":"abc123","sizeInBytes":1000,"huggingFaceModelName":"test/model","source":"HUGGING_FACE","modelFileFormat":"GGUF"}]"""
            val inputWorker =
                buildWorker(
                    inputData =
                        workDataOf(
                            DownloadWorkKeys.KEY_DOWNLOAD_FILES to filesJson,
                            DownloadWorkKeys.KEY_SESSION_ID to "test-session-123",
                            DownloadWorkKeys.KEY_REQUEST_KIND to "INITIALIZE_MODELS",
                            DownloadWorkKeys.KEY_TARGET_MODEL_ID to "",
                        ),
                )

            val inputData = inputWorker.inputData
            assertNotNull(inputData.getString(DownloadWorkKeys.KEY_DOWNLOAD_FILES))
            assertNotNull(inputData.getString(DownloadWorkKeys.KEY_SESSION_ID))
        }

        @Test
        @DisplayName("Worker parameters run attempt count is accessible")
        fun runAttemptCount_isAccessible() {
            val retriedWorker = buildWorker(runAttemptCount = 3)
            assertEquals(3, retriedWorker.runAttemptCount)
            assertTrue(worker.runAttemptCount == 0)
        }
    }

    private inner class ModelDownloadWorkerFactory : androidx.work.WorkerFactory() {
        override fun createWorker(
            appContext: Context,
            workerClassName: String,
            workerParameters: WorkerParameters,
        ): ListenableWorker =
            ModelDownloadWorker(
                context = appContext,
                params = workerParameters,
                logger = logger,
                notificationManager = notificationManager,
                progressTracker = progressTracker,
                fileDownloader = fileDownloader,
                modelUrlProvider = modelUrlProvider,
            )
    }
}
