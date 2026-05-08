package com.browntowndev.pocketcrew.core.data.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.repository.CloudSyncPort
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SyncWorkerTest {
    private lateinit var context: Context
    private val cloudSyncPort = mockk<CloudSyncPort>(relaxed = true)
    private val loggingPort = mockk<LoggingPort>(relaxed = true)

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun `doWork delegates to syncPendingChanges`() = runTest {
        coEvery { cloudSyncPort.syncPendingChanges() } returns Result.success(Unit)

        val worker = TestListenableWorkerBuilder<SyncWorker>(context)
            .setWorkerFactory(SyncWorkerFactory())
            .build()

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        coVerify(exactly = 1) { cloudSyncPort.syncPendingChanges() }
    }

    @Test
    fun `doWork returns retry when syncPendingChanges returns failure`() = runTest {
        coEvery { cloudSyncPort.syncPendingChanges() } returns Result.failure(RuntimeException("Vault locked"))

        val worker = TestListenableWorkerBuilder<SyncWorker>(context)
            .setWorkerFactory(SyncWorkerFactory())
            .build()

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.retry(), result)
    }

    @Test
    fun `doWork returns retry when syncPendingChanges throws`() = runTest {
        coEvery { cloudSyncPort.syncPendingChanges() } throws RuntimeException("Unexpected")

        val worker = TestListenableWorkerBuilder<SyncWorker>(context)
            .setWorkerFactory(SyncWorkerFactory())
            .build()

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.retry(), result)
    }

    private inner class SyncWorkerFactory : androidx.work.WorkerFactory() {
        override fun createWorker(
            appContext: Context,
            workerClassName: String,
            workerParameters: WorkerParameters
        ): ListenableWorker? {
            return SyncWorker(appContext, workerParameters, cloudSyncPort, loggingPort)
        }
    }
}
