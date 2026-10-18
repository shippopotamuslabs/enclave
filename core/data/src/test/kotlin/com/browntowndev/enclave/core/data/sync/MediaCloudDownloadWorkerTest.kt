@file:Suppress("ForbiddenMockKInTests") // FirebaseStorage/StorageReference are Firebase SDK types kept as mocks; all Enclave-owned collaborators use :core:testing fakes.

package com.browntowndev.enclave.core.data.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.browntowndev.enclave.core.testing.FakeCryptoManagerPort
import com.browntowndev.enclave.core.testing.FakeLoggingPort
import com.browntowndev.enclave.core.testing.FakeStudioRepository
import com.browntowndev.enclave.domain.port.repository.StudioMediaAsset
import com.google.android.gms.tasks.Tasks
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [34])
class MediaCloudDownloadWorkerTest {
    private lateinit var context: Context
    private val studioRepository = FakeStudioRepository()
    private val storage = mockk<FirebaseStorage>(relaxed = true)
    private val loggingPort = FakeLoggingPort()
    private val cryptoManager = FakeCryptoManagerPort()
    private val userIdProvider = { "user123" }

    private lateinit var worker: MediaCloudDownloadWorker

    @BeforeEach
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        cryptoManager.hasCachedKeyValue = true
        worker =
            TestListenableWorkerBuilder<MediaCloudDownloadWorker>(context)
                .setWorkerFactory(MediaCloudDownloadWorkerFactory())
                .build()
    }

    private fun pendingAsset(id: String) =
        StudioMediaAsset(
            id = id,
            localUri = "https://firebasestorage.googleapis.com/v0/b/bucket/o/users%2Fuser123%2Fmedia%2F$id?alt=media",
            remoteUrl = "https://firebasestorage.googleapis.com/v0/b/bucket/o/users%2Fuser123%2Fmedia%2F$id?alt=media",
            prompt = "test",
            mediaType = "IMAGE",
            createdAt = 1L,
        )

    @Test
    fun `doWork downloads decrypts and materializes pending media`() =
        runBlocking {
            val mediaId = "media1"
            val encrypted = byteArrayOf(1, 2, 3)
            val decrypted = byteArrayOf(9, 8, 7)
            studioRepository.pendingDownload = listOf(pendingAsset(mediaId))

            val storageRef = mockk<StorageReference>(relaxed = true)
            every { storage.reference.child("users/user123/media/$mediaId") } returns storageRef
            every { storageRef.getBytes(any()) } returns Tasks.forResult(encrypted)
            cryptoManager.decryptBytesResult = Result.success(decrypted)

            val result = worker.doWork()

            assertEquals(ListenableWorker.Result.success(), result)
            assertArrayEquals(decrypted, studioRepository.mediaBytes[mediaId])
            assertEquals(1, cryptoManager.decryptBytesCalls.size)
        }

    @Test
    fun `doWork returns success without touching storage when nothing pending`() =
        runBlocking {
            studioRepository.pendingDownload = emptyList()

            val result = worker.doWork()

            assertEquals(ListenableWorker.Result.success(), result)
            verify(exactly = 0) { storage.reference }
        }

    @Test
    fun `doWork retries when vault is locked`() =
        runBlocking {
            cryptoManager.hasCachedKeyValue = false

            val result = worker.doWork()

            assertEquals(ListenableWorker.Result.retry(), result)
            assertEquals(0, studioRepository.getMediaPendingDownloadCallCount)
        }

    @Test
    fun `doWork retries when decryption fails`() =
        runBlocking {
            val mediaId = "media2"
            val encrypted = byteArrayOf(4, 5, 6)
            studioRepository.pendingDownload = listOf(pendingAsset(mediaId))

            val storageRef = mockk<StorageReference>(relaxed = true)
            every { storage.reference.child("users/user123/media/$mediaId") } returns storageRef
            every { storageRef.getBytes(any()) } returns Tasks.forResult(encrypted)
            cryptoManager.decryptBytesResult = Result.failure(RuntimeException("bad key"))

            val result = worker.doWork()

            assertEquals(ListenableWorker.Result.retry(), result)
            assertTrue(studioRepository.mediaBytes.isEmpty())
        }

    private inner class MediaCloudDownloadWorkerFactory : androidx.work.WorkerFactory() {
        override fun createWorker(
            appContext: Context,
            workerClassName: String,
            workerParameters: WorkerParameters,
        ): ListenableWorker =
            MediaCloudDownloadWorker(
                appContext,
                workerParameters,
                studioRepository,
                storage,
                loggingPort,
                cryptoManager,
                userIdProvider,
            )
    }
}
