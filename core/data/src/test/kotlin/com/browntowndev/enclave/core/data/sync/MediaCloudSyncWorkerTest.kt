package com.browntowndev.enclave.core.data.sync

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import com.browntowndev.enclave.core.data.local.StudioMediaDao
import com.browntowndev.enclave.core.data.local.StudioMediaEntity
import com.browntowndev.enclave.core.data.repository.FakeLocalDataEncryptionService
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import com.browntowndev.enclave.domain.port.security.CryptoManagerPort
import com.google.android.gms.tasks.Tasks
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import com.google.firebase.storage.StorageReference
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [34])
class MediaCloudSyncWorkerTest {
    private val context = mockk<Context>(relaxed = true)
    private val workerParams = mockk<WorkerParameters>(relaxed = true)
    private val studioMediaDao = mockk<StudioMediaDao>(relaxed = true)
    private val storage = mockk<FirebaseStorage>(relaxed = true)
    private val loggingPort = mockk<LoggingPort>(relaxed = true)
    private val cryptoManager = mockk<CryptoManagerPort>(relaxed = true)
    private val syncTrigger = mockk<CloudSyncTrigger>(relaxed = true)
    private val localEncryption = FakeLocalDataEncryptionService()
    private val userIdProvider = { "user123" }

    private lateinit var worker: MediaCloudSyncWorker

    @BeforeEach
    fun setup() {
        mockkStatic("kotlinx.coroutines.tasks.TasksKt")
        worker =
            MediaCloudSyncWorker(
                context,
                workerParams,
                studioMediaDao,
                storage,
                loggingPort,
                cryptoManager,
                userIdProvider,
                syncTrigger,
                localEncryption,
            )
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic("kotlinx.coroutines.tasks.TasksKt")
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

            coEvery { studioMediaDao.getPendingUploads() } returns listOf(media)
            coEvery { studioMediaDao.getPendingDeletions() } returns emptyList()

            val storageRef = mockk<StorageReference>(relaxed = true)
            val metadata = mockk<StorageMetadata>(relaxed = true)

            every { storage.reference.child("users/user123/media/$mediaId") } returns storageRef
            // Mock storageRef.metadata.await() returns metadata
            every { storageRef.metadata } returns Tasks.forResult(metadata)
            // Mock storageRef.downloadUrl.await() returns stagingUrl
            every { storageRef.downloadUrl } returns Tasks.forResult(android.net.Uri.parse(stagingUrl))

            // Act
            val result = worker.doWork()

            // Assert
            assertEquals(ListenableWorker.Result.success(), result)
            // Verify we DID NOT try to read the file or encrypt it (minimal code would just use the URL)
            coVerify(exactly = 0) { cryptoManager.encryptBytes(any()) }
            // Verify we DID NOT try to upload
            coVerify(exactly = 0) { storageRef.putBytes(any()) }
            // Verify we marked it uploaded with the recovered URL
            coVerify { studioMediaDao.markUploaded(mediaId, "PCENC:v1:aes-gcm:$stagingUrl", any()) }
        }
}
