package com.browntowndev.enclave.core.data.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.browntowndev.enclave.core.testing.FakeCloudSyncPort
import com.browntowndev.enclave.core.testing.FakeLoggingPort
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [34])
class SyncWorkerTest {
    private lateinit var context: Context
    private val cloudSyncPort = FakeCloudSyncPort()
    private val loggingPort = FakeLoggingPort()

    @BeforeEach
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun `doWork delegates to syncPendingChanges`() =
        runTest {
            cloudSyncPort.syncPendingResult = Result.success(Unit)

            val worker =
                TestListenableWorkerBuilder<SyncWorker>(context)
                    .setWorkerFactory(SyncWorkerFactory())
                    .build()

            val result = worker.doWork()

            assertEquals(ListenableWorker.Result.success(), result)
            assertEquals(1, cloudSyncPort.count("syncPending"))
        }

    @Test
    fun `doWork returns retry when syncPendingChanges returns failure`() =
        runTest {
            cloudSyncPort.syncPendingResult = Result.failure(RuntimeException("Vault locked"))

            val worker =
                TestListenableWorkerBuilder<SyncWorker>(context)
                    .setWorkerFactory(SyncWorkerFactory())
                    .build()

            val result = worker.doWork()

            assertEquals(ListenableWorker.Result.retry(), result)
        }

    @Test
    fun `doWork returns retry when syncPendingChanges throws`() =
        runTest {
            cloudSyncPort.syncPendingThrowable = RuntimeException("Unexpected")

            val worker =
                TestListenableWorkerBuilder<SyncWorker>(context)
                    .setWorkerFactory(SyncWorkerFactory())
                    .build()

            val result = worker.doWork()

            assertEquals(ListenableWorker.Result.retry(), result)
        }

    private inner class SyncWorkerFactory : androidx.work.WorkerFactory() {
        override fun createWorker(
            appContext: Context,
            workerClassName: String,
            workerParameters: WorkerParameters,
        ): ListenableWorker? = SyncWorker(appContext, workerParameters, cloudSyncPort, loggingPort)
    }
}
