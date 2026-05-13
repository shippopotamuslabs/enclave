package com.browntowndev.pocketcrew.core.data.sync

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import com.browntowndev.pocketcrew.core.data.local.MessageFileDao
import com.browntowndev.pocketcrew.core.data.local.MessageFileEntity
import com.browntowndev.pocketcrew.domain.model.artifact.MimeType
import com.browntowndev.pocketcrew.core.data.repository.FakeLocalDataEncryptionService
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.security.CryptoManagerPort
import com.google.android.gms.tasks.Task
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import com.google.firebase.storage.StorageReference
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MessageFileCloudSyncWorkerTest {
    private val messageFileDao = mockk<MessageFileDao>(relaxed = true)
    private val storage = mockk<FirebaseStorage>(relaxed = true)
    private val loggingPort = mockk<LoggingPort>(relaxed = true)
    private val cryptoManager = mockk<CryptoManagerPort>(relaxed = true)
    private val userIdProvider: () -> String? = { "user123" }
    private val syncTrigger = mockk<CloudSyncTrigger>(relaxed = true)
    private val localEncryption = FakeLocalDataEncryptionService()

    private lateinit var worker: MessageFileCloudSyncWorker

    @Before
    fun setup() {
        mockkStatic("kotlinx.coroutines.tasks.TasksKt")
        val context = mockk<Context>(relaxed = true)
        val workerParams = mockk<WorkerParameters>(relaxed = true)
        worker =
            MessageFileCloudSyncWorker(
                context,
                workerParams,
                messageFileDao,
                storage,
                loggingPort,
                cryptoManager,
                userIdProvider,
                syncTrigger,
                localEncryption,
            )
    }

    @After
    fun tearDown() {
        unmockkStatic("kotlinx.coroutines.tasks.TasksKt")
    }

    @Test
    fun `doWork recovers from stagingRemoteUrl if present`() =
        runTest {
            // Arrange
            val fileId = "file1"
            val stagingUrl = "https://firebasestorage.googleapis.com/v0/b/bucket/o/users%2Fuser123%2Ffiles%2Ffile1?alt=media"
            val file =
                MessageFileEntity(
                    id = fileId,
                    messageId = "msg1",
                    name = "test.txt",
                    mimeType = MimeType.PLAIN_TEXT,
                    content = "test content",
                    stagingRemoteUrl = "ENC:v1:$stagingUrl",
                )

            coEvery { messageFileDao.getPendingUploads() } returns listOf(file)
            coEvery { messageFileDao.getPendingDeletions() } returns emptyList()

            val storageRef = mockk<StorageReference>(relaxed = true)
            val metadataTask = mockk<Task<StorageMetadata>>(relaxed = true)
            val metadata = mockk<StorageMetadata>(relaxed = true)

            every { storage.reference.child(any()) } returns storageRef
            every { storageRef.metadata } returns metadataTask
            coEvery { metadataTask.await() } returns metadata

            // Act
            val result = worker.doWork()

            // Assert
            assertEquals(ListenableWorker.Result.success(), result)
            coVerify { messageFileDao.markUploaded(fileId, "ENC:v1:$stagingUrl", any()) }
        }
}
