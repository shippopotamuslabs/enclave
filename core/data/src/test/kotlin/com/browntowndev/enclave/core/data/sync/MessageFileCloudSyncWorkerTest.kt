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
import com.browntowndev.enclave.core.data.local.ChatEntity
import com.browntowndev.enclave.core.data.local.EnclaveDatabase
import com.browntowndev.enclave.core.data.local.MessageEntity
import com.browntowndev.enclave.core.data.local.MessageFileEntity
import com.browntowndev.enclave.core.data.repository.FakeLocalDataEncryptionService
import com.browntowndev.enclave.core.testing.FakeCryptoManagerPort
import com.browntowndev.enclave.core.testing.FakeLoggingPort
import com.browntowndev.enclave.domain.model.artifact.MimeType
import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.model.chat.Role
import com.google.android.gms.tasks.Tasks
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import com.google.firebase.storage.StorageReference
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension
import java.util.Date
import java.util.concurrent.Executor

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [34])
class MessageFileCloudSyncWorkerTest {
    private lateinit var context: Context
    private lateinit var database: EnclaveDatabase
    private val storage = mockk<FirebaseStorage>(relaxed = true)
    private val loggingPort = FakeLoggingPort()
    private val cryptoManager = FakeCryptoManagerPort()
    private val userIdProvider: () -> String? = { "user123" }
    private val localEncryption = FakeLocalDataEncryptionService()
    private lateinit var syncTrigger: CloudSyncTrigger

    private lateinit var worker: MessageFileCloudSyncWorker

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
            TestListenableWorkerBuilder<MessageFileCloudSyncWorker>(context)
                .setWorkerFactory(MessageFileCloudSyncWorkerFactory())
                .build()
    }

    @AfterEach
    fun tearDown() {
        database.close()
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
                    stagingRemoteUrl = "PCENC:v1:aes-gcm:$stagingUrl",
                )
            // FK parents first: message files reference message.id ("msg1"),
            // which itself references chat.id ("chat1").
            database.chatDao().insertIfAbsent(
                ChatEntity(id = ChatId("chat1"), name = "Chat", created = Date(0), lastModified = Date(0), pinned = false),
            )
            database.messageCommandDao().insertIfAbsent(
                MessageEntity(id = MessageId("msg1"), chatId = ChatId("chat1"), content = "seed", role = Role.USER),
            )
            database.messageFileDao().insert(file)

            val storageRef = mockk<StorageReference>(relaxed = true)
            val metadata = StorageMetadata.Builder().build()

            every { storage.reference.child(any()) } returns storageRef
            every { storageRef.metadata } returns Tasks.forResult(metadata)

            // Act
            val result = worker.doWork()

            // Assert
            assertEquals(ListenableWorker.Result.success(), result)
            // It was marked uploaded with the recovered URL and the staging URL cleared
            val stored = database.messageFileDao().getById(fileId)
            assertEquals("PCENC:v1:aes-gcm:$stagingUrl", stored?.remoteUrl)
            assertEquals("UPLOADED", stored?.syncStatus)
            assertNull(stored?.stagingRemoteUrl)
        }

    private inner class MessageFileCloudSyncWorkerFactory : androidx.work.WorkerFactory() {
        override fun createWorker(
            appContext: Context,
            workerClassName: String,
            workerParameters: WorkerParameters,
        ): ListenableWorker =
            MessageFileCloudSyncWorker(
                appContext,
                workerParameters,
                database.messageFileDao(),
                storage,
                loggingPort,
                cryptoManager,
                userIdProvider,
                syncTrigger,
                localEncryption,
            )
    }
}
