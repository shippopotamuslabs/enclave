package com.browntowndev.pocketcrew.core.data.sync

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import com.browntowndev.pocketcrew.core.data.local.MessageFileDao
import com.browntowndev.pocketcrew.core.data.local.MessageFileEntity
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.security.CryptoManagerPort
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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MessageFileCloudSyncWorkerTest {

    private val context = mockk<Context>(relaxed = true)
    private val workerParams = mockk<WorkerParameters>(relaxed = true)
    private val messageFileDao = mockk<MessageFileDao>(relaxed = true)
    private val storage = mockk<FirebaseStorage>(relaxed = true)
    private val loggingPort = mockk<LoggingPort>(relaxed = true)
    private val cryptoManager = mockk<CryptoManagerPort>(relaxed = true)
    private val syncTrigger = mockk<CloudSyncTrigger>(relaxed = true)
    private val userIdProvider = { "user123" }

    private lateinit var worker: MessageFileCloudSyncWorker

    @Before
    fun setup() {
        mockkStatic("kotlinx.coroutines.tasks.TasksKt")
        worker = MessageFileCloudSyncWorker(
            context,
            workerParams,
            messageFileDao,
            storage,
            loggingPort,
            cryptoManager,
            userIdProvider,
            syncTrigger
        )
    }

    @After
    fun tearDown() {
        unmockkStatic("kotlinx.coroutines.tasks.TasksKt")
    }

    @Test
    fun `doWork recovers from stagingRemoteUrl if present`() = runBlocking {
        // Arrange
        val fileId = "file1"
        val stagingUrl = "https://firebasestorage.googleapis.com/v0/b/bucket/o/users%2Fuser123%2Ffiles%2Ffile1?alt=media"
        val file = MessageFileEntity(
            id = fileId,
            messageId = "msg1",
            name = "test.txt",
            fileType = "TEXT",
            content = "test content",
            stagingRemoteUrl = stagingUrl
        )

        coEvery { messageFileDao.getPendingUploads() } returns listOf(file)
        coEvery { messageFileDao.getPendingDeletions() } returns emptyList()

        val storageRef = mockk<StorageReference>(relaxed = true)
        val metadata = mockk<StorageMetadata>(relaxed = true)
        
        every { storage.reference.child("users/user123/files/$fileId") } returns storageRef
        every { storageRef.metadata } returns Tasks.forResult(metadata)
        every { storageRef.downloadUrl } returns Tasks.forResult(android.net.Uri.parse(stagingUrl))

        // Act
        val result = worker.doWork()

        // Assert
        assertEquals(ListenableWorker.Result.success(), result)
        coVerify(exactly = 0) { cryptoManager.encryptBytes(any()) }
        coVerify(exactly = 0) { storageRef.putBytes(any()) }
        coVerify { messageFileDao.markUploaded(fileId, stagingUrl, any()) }
    }
}
