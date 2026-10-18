package com.browntowndev.enclave.core.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.ExistingWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.impl.utils.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.browntowndev.enclave.core.data.download.ModelDownloadWorker
import com.browntowndev.enclave.domain.model.download.ModelConfig
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension
import java.util.concurrent.TimeUnit

/**
 * ENC-28: runs against a real test [WorkManager] (work-runtime 2.11 made the
 * WorkManager constructor internal, so the former subclass fake cannot
 * exist). Worker outcomes are scripted through a [WorkerFactory]: immediate
 * success/failure, or a blocked worker whose future the test completes by
 * hand (which is what keeps its WorkInfo in RUNNING).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(RobolectricExtension::class)
@Config(sdk = [34])
class DownloadWorkRepositoryTest {
    private lateinit var workManager: WorkManager
    private lateinit var repository: DownloadWorkRepository
    private val workerFactory = ScriptedWorkerFactory()

    @BeforeEach
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val config =
            Configuration
                .Builder()
                .setTaskExecutor(SynchronousExecutor())
                .setExecutor(SynchronousExecutor())
                .setWorkerFactory(workerFactory)
                .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)
        workManager = WorkManager.getInstance(context)
        // Inject UnconfinedTestDispatcher to bypass threading issues in tests
        repository = DownloadWorkRepository(workManager, UnconfinedTestDispatcher())
    }

    private fun downloadRequest(configure: OneTimeWorkRequest.Builder.() -> Unit = {}): OneTimeWorkRequest =
        OneTimeWorkRequest.Builder(ModelDownloadWorker::class.java)
            .addTag(ModelConfig.WORK_TAG)
            .apply(configure)
            .build()

    private fun enqueue(request: OneTimeWorkRequest) {
        workManager
            .enqueueUniqueWork(ModelConfig.WORK_TAG, ExistingWorkPolicy.REPLACE, request)
            .result
            .get()
    }

    @Test
    fun observeDownloadProgress_emitsWorkInfo_whenWorkRunning() =
        runTest {
            workerFactory.block(ModelDownloadWorker::class.java.name)
            enqueue(downloadRequest())

            val result = repository.observeDownloadProgress().first()

            assertEquals(WorkInfo.State.RUNNING, result?.state)
        }

    @Test
    fun observeDownloadProgress_emitsTerminalState_whenWorkSucceeds() =
        runTest {
            workerFactory.succeed(ModelDownloadWorker::class.java.name)
            enqueue(downloadRequest())

            val result = repository.observeDownloadProgress().first()

            assertEquals(WorkInfo.State.SUCCEEDED, result?.state)
        }

    @Test
    fun observeDownloadProgress_emitsTerminalState_whenWorkFails() =
        runTest {
            workerFactory.fail(ModelDownloadWorker::class.java.name)
            enqueue(downloadRequest())

            val result = repository.observeDownloadProgress().first()

            assertEquals(WorkInfo.State.FAILED, result?.state)
        }

    @Test
    fun observeDownloadProgress_returnsNull_whenWorkListEmpty() =
        runTest {
            val result = repository.observeDownloadProgress().first()

            assertEquals(null, result)
        }

    @Test
    fun getWorkId_returnsWorkId_whenWorkRunning() =
        runTest {
            // Arrange
            workerFactory.block(ModelDownloadWorker::class.java.name)
            val request = downloadRequest()
            enqueue(request)

            // Act
            val result = repository.getWorkId()

            // Assert
            assertEquals(request.id, result)
        }

    @Test
    fun getWorkId_returnsNull_whenNoWorkRunning() =
        runTest {
            // Act
            val result = repository.getWorkId()

            // Assert
            assertEquals(null, result)
        }

    @Test
    fun cancelDownload_callsWorkManager() {
        // Arrange
        workerFactory.block(ModelDownloadWorker::class.java.name)
        enqueue(downloadRequest())

        // Act
        repository.cancelDownload()

        // Assert: the unique work is cancelled in the real WorkManager.
        val states =
            workManager
                .getWorkInfosForUniqueWork(ModelConfig.WORK_TAG)
                .get()
                .map { it.state }
        assertEquals(listOf(WorkInfo.State.CANCELLED), states)
    }

    @ParameterizedTest
    @CsvSource(
        "ENQUEUED, true",
        "RUNNING, true",
        "BLOCKED, true",
        "SUCCEEDED, false",
        "FAILED, false",
        "CANCELLED, false",
    )
    fun isWorkRunning_returnsExpectedResult_basedOnState(
        state: WorkInfo.State,
        expected: Boolean,
    ) = runTest {
        // Arrange
        arrangeWorkInState(state)

        // Act
        val result = repository.isWorkRunning()

        // Assert
        assertEquals(expected, result)
    }

    @Test
    fun isWorkRunning_returnsFalse_whenNoWorkInfo() =
        runTest {
            // Act
            val result = repository.isWorkRunning()

            // Assert
            assertEquals(false, result)
        }

    // ENC-28: `isWorkRunning_returnsFalse_whenExceptionThrown` was removed. It
    // stubbed the unique-work Flow to throw through the hand-written
    // WorkManager fake; against the real test WorkManager the query Flow
    // cannot be made to fail, so that premise is not expressible anymore.

    /** Enqueues real work whose WorkInfo ends up in [state]. */
    private fun arrangeWorkInState(state: WorkInfo.State) {
        val workerName = ModelDownloadWorker::class.java.name
        when (state) {
            WorkInfo.State.ENQUEUED -> {
                // An initial delay is never met inside the test WorkManager,
                // so the work stays queued and the worker never starts.
                enqueue(downloadRequest { setInitialDelay(1, TimeUnit.HOURS) })
            }

            WorkInfo.State.RUNNING -> {
                workerFactory.block(workerName)
                enqueue(downloadRequest())
            }

            WorkInfo.State.BLOCKED -> {
                // Second link of a chain stays BLOCKED behind the running first link.
                workerFactory.block(workerName)
                val first = downloadRequest()
                val second = downloadRequest()
                workManager
                    .beginUniqueWork(ModelConfig.WORK_TAG, ExistingWorkPolicy.REPLACE, first)
                    .then(second)
                    .enqueue()
                    .result
                    .get()
            }

            WorkInfo.State.SUCCEEDED -> {
                workerFactory.succeed(workerName)
                enqueue(downloadRequest())
            }

            WorkInfo.State.FAILED -> {
                workerFactory.fail(workerName)
                enqueue(downloadRequest())
            }

            WorkInfo.State.CANCELLED -> {
                workerFactory.block(workerName)
                val request = downloadRequest()
                enqueue(request)
                workManager.cancelWorkById(request.id).result.get()
            }
        }
    }

    /**
     * [WorkerFactory] scripting worker outcomes per class name: an immediate
     * result, or a blocked worker held in RUNNING by an uncompleted future.
     */
    private class ScriptedWorkerFactory : WorkerFactory() {
        private val immediateResults = mutableMapOf<String, ListenableWorker.Result>()
        private val blockedClasses = mutableSetOf<String>()

        fun succeed(workerClassName: String) {
            immediateResults[workerClassName] = ListenableWorker.Result.success()
        }

        fun fail(workerClassName: String) {
            immediateResults[workerClassName] = ListenableWorker.Result.failure()
        }

        fun block(workerClassName: String) {
            blockedClasses += workerClassName
        }

        override fun createWorker(
            appContext: Context,
            workerClassName: String,
            workerParameters: WorkerParameters,
        ): ListenableWorker =
            object : ListenableWorker(appContext, workerParameters) {
                override fun startWork(): ListenableFuture<ListenableWorker.Result> =
                    if (workerClassName in blockedClasses) {
                        SettableFuture.create<ListenableWorker.Result>()
                    } else {
                        Futures.immediateFuture(
                            immediateResults[workerClassName] ?: ListenableWorker.Result.success(),
                        )
                    }
            }
    }
}
