package com.browntowndev.enclave.core.data.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import com.browntowndev.enclave.domain.usecase.security.StartCloudSyncListeningUseCase
import io.mockk.coEvery
import io.mockk.coVerify
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
class InitialSyncWorkerTest {
    private lateinit var context: Context
    private val startCloudSyncListeningUseCase = mockk<StartCloudSyncListeningUseCase>()
    private val loggingPort = mockk<LoggingPort>(relaxed = true)
    private val cloudSyncTrigger = mockk<CloudSyncTrigger>(relaxed = true)

    @BeforeEach
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun `doWork delegates to startCloudSyncListeningUseCase and returns success`() =
        runTest {
            coEvery { startCloudSyncListeningUseCase(uploadLocalFirst = true, any()) } returns Result.success(Unit)

            val worker =
                TestListenableWorkerBuilder<InitialSyncWorker>(context)
                    .setWorkerFactory(InitialSyncWorkerFactory())
                    .build()

            val result = worker.doWork()

            assertEquals(ListenableWorker.Result.success(), result)
            coVerify(exactly = 1) { startCloudSyncListeningUseCase(uploadLocalFirst = true, any()) }
            io.mockk.verify(exactly = 1) { cloudSyncTrigger.triggerMediaDownload() }
        }

    @Test
    fun `doWork respects input data for uploadLocalFirst`() =
        runTest {
            coEvery { startCloudSyncListeningUseCase(uploadLocalFirst = false, any()) } returns Result.success(Unit)

            val worker =
                TestListenableWorkerBuilder<InitialSyncWorker>(context)
                    .setInputData(workDataOf(InitialSyncWorker.KEY_UPLOAD_LOCAL_FIRST to false))
                    .setWorkerFactory(InitialSyncWorkerFactory())
                    .build()

            val result = worker.doWork()

            assertEquals(ListenableWorker.Result.success(), result)
            coVerify(exactly = 1) { startCloudSyncListeningUseCase(uploadLocalFirst = false, any()) }
        }

    @Test
    fun `doWork returns failure when startCloudSyncListeningUseCase fails`() =
        runTest {
            coEvery { startCloudSyncListeningUseCase(any(), any()) } returns Result.failure(RuntimeException("Sync error"))

            val worker =
                TestListenableWorkerBuilder<InitialSyncWorker>(context)
                    .setWorkerFactory(InitialSyncWorkerFactory())
                    .build()

            val result = worker.doWork()

            assertEquals(ListenableWorker.Result.failure(workDataOf(InitialSyncWorker.KEY_PROGRESS_STATUS to "Sync error")), result)
            io.mockk.verify(exactly = 0) { cloudSyncTrigger.triggerMediaDownload() }
        }

    private inner class InitialSyncWorkerFactory : androidx.work.WorkerFactory() {
        override fun createWorker(
            appContext: Context,
            workerClassName: String,
            workerParameters: WorkerParameters,
        ): ListenableWorker? =
            InitialSyncWorker(
                appContext,
                workerParameters,
                startCloudSyncListeningUseCase,
                loggingPort,
                cloudSyncTrigger,
            )
    }
}
