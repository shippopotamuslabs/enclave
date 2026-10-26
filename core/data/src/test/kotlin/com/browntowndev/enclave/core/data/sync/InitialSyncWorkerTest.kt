package com.browntowndev.enclave.core.data.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import androidx.work.workDataOf
import com.browntowndev.enclave.core.testing.FakeCloudSyncPort
import com.browntowndev.enclave.core.testing.FakeEntitlementPort
import com.browntowndev.enclave.core.testing.FakeLoggingPort
import com.browntowndev.enclave.domain.model.entitlement.EntitlementState
import com.browntowndev.enclave.domain.usecase.security.StartCloudSyncListeningUseCase
import kotlinx.coroutines.test.runTest
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
class InitialSyncWorkerTest {
    private lateinit var context: Context
    private lateinit var workManager: WorkManager
    private val cloudSyncPort = FakeCloudSyncPort()
    private val loggingPort = FakeLoggingPort()
    private lateinit var startCloudSyncListeningUseCase: StartCloudSyncListeningUseCase
    private lateinit var cloudSyncTrigger: CloudSyncTrigger

    @BeforeEach
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        // Holding background executor: triggered work stays ENQUEUED and observable.
        val config =
            Configuration
                .Builder()
                .setTaskExecutor(androidx.work.impl.utils.SynchronousExecutor())
                .setExecutor(Executor { })
                .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)
        workManager = WorkManager.getInstance(context)
        val entitlementPort = FakeEntitlementPort(EntitlementState.Entitled())
        startCloudSyncListeningUseCase = StartCloudSyncListeningUseCase(cloudSyncPort, entitlementPort)
        cloudSyncTrigger = CloudSyncTrigger(workManager, loggingPort, FakeEntitlementPort(EntitlementState.Entitled()))
    }

    private fun mediaDownloadWorkCount(): Int = workManager.getWorkInfosForUniqueWork("enclave_media_download").get().size

    @Test
    fun `doWork delegates to startCloudSyncListeningUseCase and returns success`() =
        runTest {
            cloudSyncPort.syncAllResult = Result.success(Unit)

            val worker =
                TestListenableWorkerBuilder<InitialSyncWorker>(context)
                    .setWorkerFactory(InitialSyncWorkerFactory())
                    .build()

            val result = worker.doWork()

            assertEquals(ListenableWorker.Result.success(), result)
            // The real use case ran exactly one upload-first pass.
            assertEquals(1, cloudSyncPort.count("syncAll"))
            assertEquals(1, mediaDownloadWorkCount())
        }

    @Test
    fun `doWork respects input data for uploadLocalFirst`() =
        runTest {
            cloudSyncPort.syncAllResult = Result.success(Unit)

            val worker =
                TestListenableWorkerBuilder<InitialSyncWorker>(context)
                    .setInputData(workDataOf(InitialSyncWorker.KEY_UPLOAD_LOCAL_FIRST to false))
                    .setWorkerFactory(InitialSyncWorkerFactory())
                    .build()

            val result = worker.doWork()

            assertEquals(ListenableWorker.Result.success(), result)
            // uploadLocalFirst = false skips the upload pass entirely.
            assertEquals(0, cloudSyncPort.count("syncAll"))
            assertTrue(cloudSyncPort.count("startListening") == 1)
        }

    @Test
    fun `doWork returns failure when startCloudSyncListeningUseCase fails`() =
        runTest {
            cloudSyncPort.syncAllResult = Result.failure(RuntimeException("Sync error"))

            val worker =
                TestListenableWorkerBuilder<InitialSyncWorker>(context)
                    .setWorkerFactory(InitialSyncWorkerFactory())
                    .build()

            val result = worker.doWork()

            assertEquals(ListenableWorker.Result.failure(workDataOf(InitialSyncWorker.KEY_PROGRESS_STATUS to "Sync error")), result)
            assertEquals(0, mediaDownloadWorkCount())
        }

    @Test
    fun `doWork returns success without media download when subscription required`() =
        runTest {
            workerUseCase =
                StartCloudSyncListeningUseCase(
                    cloudSyncPort,
                    FakeEntitlementPort(EntitlementState.NotEntitled),
                )

            val worker =
                TestListenableWorkerBuilder<InitialSyncWorker>(context)
                    .setWorkerFactory(InitialSyncWorkerFactory())
                    .build()

            val result = worker.doWork()

            assertEquals(ListenableWorker.Result.success(), result)
            assertEquals(0, cloudSyncPort.count("syncAll"))
            assertEquals(0, mediaDownloadWorkCount())
        }

    private var workerUseCase: StartCloudSyncListeningUseCase? = null

    private inner class InitialSyncWorkerFactory : androidx.work.WorkerFactory() {
        override fun createWorker(
            appContext: Context,
            workerClassName: String,
            workerParameters: WorkerParameters,
        ): ListenableWorker? =
            InitialSyncWorker(
                appContext,
                workerParameters,
                workerUseCase ?: startCloudSyncListeningUseCase,
                loggingPort,
                cloudSyncTrigger,
            )
    }
}
