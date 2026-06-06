package com.browntowndev.enclave.core.data.sync

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import com.browntowndev.enclave.domain.port.repository.StudioMediaAsset
import com.browntowndev.enclave.domain.port.repository.StudioRepositoryPort
import com.browntowndev.enclave.domain.port.security.CryptoManagerPort
import com.google.android.gms.tasks.Tasks
import com.google.firebase.storage.FirebaseStorage
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
class MediaCloudDownloadWorkerTest {
    private val context = mockk<Context>(relaxed = true)
    private val workerParams = mockk<WorkerParameters>(relaxed = true)
    private val studioRepository = mockk<StudioRepositoryPort>(relaxed = true)
    private val storage = mockk<FirebaseStorage>(relaxed = true)
    private val loggingPort = mockk<LoggingPort>(relaxed = true)
    private val cryptoManager = mockk<CryptoManagerPort>(relaxed = true)
    private val userIdProvider = { "user123" }

    private lateinit var worker: MediaCloudDownloadWorker

    @BeforeEach
    fun setup() {
        mockkStatic("kotlinx.coroutines.tasks.TasksKt")
        every { cryptoManager.hasCachedKey() } returns true
        worker =
            MediaCloudDownloadWorker(
                context,
                workerParams,
                studioRepository,
                storage,
                loggingPort,
                cryptoManager,
                userIdProvider,
            )
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic("kotlinx.coroutines.tasks.TasksKt")
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
            coEvery { studioRepository.getMediaPendingDownload() } returns listOf(pendingAsset(mediaId))

            val storageRef = mockk<StorageReference>(relaxed = true)
            every { storage.reference.child("users/user123/media/$mediaId") } returns storageRef
            every { storageRef.getBytes(any()) } returns Tasks.forResult(encrypted)
            every { cryptoManager.decryptBytes(encrypted, null) } returns Result.success(decrypted)

            val result = worker.doWork()

            assertEquals(ListenableWorker.Result.success(), result)
            coVerify(exactly = 1) { studioRepository.materializeDownloadedMedia(mediaId, decrypted) }
        }

    @Test
    fun `doWork returns success without touching storage when nothing pending`() =
        runBlocking {
            coEvery { studioRepository.getMediaPendingDownload() } returns emptyList()

            val result = worker.doWork()

            assertEquals(ListenableWorker.Result.success(), result)
            coVerify(exactly = 0) { storage.reference }
        }

    @Test
    fun `doWork retries when vault is locked`() =
        runBlocking {
            every { cryptoManager.hasCachedKey() } returns false

            val result = worker.doWork()

            assertEquals(ListenableWorker.Result.retry(), result)
            coVerify(exactly = 0) { studioRepository.getMediaPendingDownload() }
        }

    @Test
    fun `doWork retries when decryption fails`() =
        runBlocking {
            val mediaId = "media2"
            val encrypted = byteArrayOf(4, 5, 6)
            coEvery { studioRepository.getMediaPendingDownload() } returns listOf(pendingAsset(mediaId))

            val storageRef = mockk<StorageReference>(relaxed = true)
            every { storage.reference.child("users/user123/media/$mediaId") } returns storageRef
            every { storageRef.getBytes(any()) } returns Tasks.forResult(encrypted)
            every { cryptoManager.decryptBytes(encrypted, null) } returns Result.failure(RuntimeException("bad key"))

            val result = worker.doWork()

            assertEquals(ListenableWorker.Result.retry(), result)
            coVerify(exactly = 0) { studioRepository.materializeDownloadedMedia(any(), any()) }
        }
}
