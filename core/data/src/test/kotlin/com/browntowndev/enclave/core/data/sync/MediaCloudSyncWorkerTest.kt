@file:Suppress("ForbiddenMockKInTests") // FirebaseStorage/StorageReference are Firebase SDK types kept as mocks; all Enclave-owned collaborators use real DAOs and :core:testing fakes.

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
import com.browntowndev.enclave.core.data.local.StudioMediaEntity
import com.browntowndev.enclave.core.data.repository.FakeLocalDataEncryptionService
import com.browntowndev.enclave.core.testing.FakeCryptoManagerPort
import com.browntowndev.enclave.core.testing.FakeLoggingPort
import com.google.android.gms.tasks.Tasks
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import com.google.firebase.storage.StorageReference
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension
import java.util.concurrent.Executor

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [34])
class MediaCloudSyncWorkerTest {
    private lateinit var context: Context
    private lateinit var database: EnclaveDatabase
    private val storage = mockk<FirebaseStorage>(relaxed = true)
    private val loggingPort = FakeLoggingPort()
    private val cryptoManager = FakeCryptoManagerPort()
    private val localEncryption = FakeLocalDataEncryptionService()
    private val userIdProvider = { "user123" }
    private lateinit var syncTrigger: CloudSyncTrigger

    private lateinit var worker: MediaCloudSyncWorker

    @BeforeEach
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        val config =
            Configuration
                .Builder()
                .setTaskExecutor(androidx.work.impl.utils.SynchronousExecutor())
                .setExecutor(Executor { })
                .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)
        syncTrigger = CloudSyncTrigger(WorkManager.getInstance(context), loggingPort)
        database =
            Room
                .inMemoryDatabaseBuilder(context, EnclaveDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        worker =
            TestListenableWorkerBuilder<MediaCloudSyncWorker>(context)
                .setWorkerFactory(MediaCloudSyncWorkerFactory())
                .build()
    }

    @AfterEach
    fun tearDown() {
        database.close()
    }

    @Test
    fun `doWork recovers from stagingRemoteUrl if present`() =
        runBlocking {
            // Arrange
            val mediaId = "media1"
            val stagingUrl = "https://firebasestorage.googleapis.com/v0/b/bucket/o/users%2Fuser123%2Fmedia%2Fmedia1?alt=media"
            val media =
                StudioMediaEntity(
                    id = mediaId,
                    prompt = "test",
                    mediaUri = "file:///tmp/media1.jpg",
                    mediaType = "IMAGE",
                    stagingRemoteUrl = "PCENC:v1:aes-gcm:$stagingUrl",
                )
            database.studioMediaDao().insertMedia(media)

            val storageRef = mockk<StorageReference>(relaxed = true)
            val metadata = StorageMetadata.Builder().build()

            every { storage.reference.child("users/user123/media/$mediaId") } returns storageRef
            every { storageRef.metadata } returns Tasks.forResult(metadata)
            every { storageRef.downloadUrl } returns Tasks.forResult(android.net.Uri.parse(stagingUrl))

            // Act
            val result = worker.doWork()

            // Assert
            assertEquals(ListenableWorker.Result.success(), result)
            // We did NOT try to read the file or encrypt it (the recovered URL is used as-is)
            assertTrue(cryptoManager.encryptBytesCalls.isEmpty())
            // We did NOT try to upload
            verify(exactly = 0) { storageRef.putBytes(any()) }
            // It was marked uploaded with the recovered URL and the staging URL cleared
            val stored = database.studioMediaDao().getMediaById(mediaId)
            assertEquals("PCENC:v1:aes-gcm:$stagingUrl", stored?.remoteUrl)
            assertEquals("UPLOADED", stored?.syncStatus)
            assertNull(stored?.stagingRemoteUrl)
        }

    private inner class MediaCloudSyncWorkerFactory : androidx.work.WorkerFactory() {
        override fun createWorker(
            appContext: Context,
            workerClassName: String,
            workerParameters: WorkerParameters,
        ): ListenableWorker =
            MediaCloudSyncWorker(
                appContext,
                workerParameters,
                database.studioMediaDao(),
                storage,
                loggingPort,
                cryptoManager,
                userIdProvider,
                syncTrigger,
                localEncryption,
            )
    }
}
