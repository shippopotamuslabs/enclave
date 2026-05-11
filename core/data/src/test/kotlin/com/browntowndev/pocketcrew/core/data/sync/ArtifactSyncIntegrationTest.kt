package com.browntowndev.pocketcrew.core.data.sync

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import com.browntowndev.pocketcrew.core.data.local.MessageDao
import com.browntowndev.pocketcrew.core.data.local.MessageFileDao
import com.browntowndev.pocketcrew.core.data.local.MessageFileEntity
import com.browntowndev.pocketcrew.core.data.local.ChatDao
import com.browntowndev.pocketcrew.core.data.local.MemoriesDao
import com.browntowndev.pocketcrew.core.data.local.WebSearchSourceDao
import com.browntowndev.pocketcrew.core.data.local.TtsProviderDao
import com.browntowndev.pocketcrew.core.data.local.MediaProviderDao
import com.browntowndev.pocketcrew.core.data.local.ApiModelConfigurationsDao
import com.browntowndev.pocketcrew.core.data.local.LocalModelsDao
import com.browntowndev.pocketcrew.core.data.local.ApiCredentialsDao
import com.browntowndev.pocketcrew.core.data.local.LocalModelConfigurationsDao
import com.browntowndev.pocketcrew.core.data.local.DefaultModelsDao
import com.browntowndev.pocketcrew.core.data.local.StudioMediaDao
import com.browntowndev.pocketcrew.core.data.local.StudioAlbumDao
import com.browntowndev.pocketcrew.core.data.local.MessageVisionAnalysisDao
import com.browntowndev.pocketcrew.core.data.local.MessageVisionAnalysisEntity
import com.browntowndev.pocketcrew.core.data.local.ChatSummaryDao
import com.browntowndev.pocketcrew.core.data.local.EmbeddingDao
import com.browntowndev.pocketcrew.core.data.local.StudioAlbumEntity
import com.browntowndev.pocketcrew.core.data.local.WebSearchSourceEntity
import com.browntowndev.pocketcrew.core.data.local.TtsProviderEntity
import com.browntowndev.pocketcrew.core.data.local.MediaProviderEntity
import com.browntowndev.pocketcrew.core.data.security.ApiKeyManager
import com.browntowndev.pocketcrew.core.data.remote.FirestoreDataSource
import com.browntowndev.pocketcrew.core.data.repository.ChatRepositoryImpl
import com.browntowndev.pocketcrew.domain.model.artifact.ArtifactGenerationRequest
import com.browntowndev.pocketcrew.domain.model.artifact.MimeType
import com.browntowndev.pocketcrew.domain.model.artifact.FileSource
import com.browntowndev.pocketcrew.domain.model.chat.ChatId
import com.browntowndev.pocketcrew.domain.model.chat.MessageId
import com.browntowndev.pocketcrew.domain.model.chat.MessageState
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.model.inference.ApiProvider
import com.browntowndev.pocketcrew.domain.model.config.MediaCapability
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.repository.TransactionProvider
import com.browntowndev.pocketcrew.domain.port.security.CryptoManagerPort
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import com.google.firebase.storage.UploadTask
import io.mockk.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ArtifactSyncIntegrationTest {

    private val syncTrigger = mockk<CloudSyncTrigger>(relaxed = true)
    private val chatDao = mockk<ChatDao>(relaxed = true)
    private val messageDao = mockk<MessageDao>(relaxed = true)
    private val memoriesDao = mockk<MemoriesDao>(relaxed = true)
    private val webSearchSourceDao = mockk<WebSearchSourceDao>(relaxed = true)
    private val ttsProviderDao = mockk<TtsProviderDao>(relaxed = true)
    private val mediaProviderDao = mockk<MediaProviderDao>(relaxed = true)
    private val apiModelConfigurationsDao = mockk<ApiModelConfigurationsDao>(relaxed = true)
    private val localModelsDao = mockk<LocalModelsDao>(relaxed = true)
    private val apiCredentialsDao = mockk<ApiCredentialsDao>(relaxed = true)
    private val localModelConfigurationsDao = mockk<LocalModelConfigurationsDao>(relaxed = true)
    private val defaultModelsDao = mockk<DefaultModelsDao>(relaxed = true)
    private val studioMediaDao = mockk<StudioMediaDao>(relaxed = true)
    private val studioAlbumDao = mockk<StudioAlbumDao>(relaxed = true)
    private val messageFileDao = mockk<MessageFileDao>(relaxed = true)
    private val messageVisionAnalysisDao = mockk<MessageVisionAnalysisDao>(relaxed = true)
    private val chatSummaryDao = mockk<ChatSummaryDao>(relaxed = true)
    private val embeddingDao = mockk<EmbeddingDao>(relaxed = true)
    private val apiKeyManager = mockk<ApiKeyManager>(relaxed = true)
    private val embeddingCoordinator = mockk<EmbeddingRegenerationCoordinator>(relaxed = true)
    private val firestore = mockk<FirebaseFirestore>(relaxed = true)
    private val testScope = TestScope()

    private val transactionProvider = object : TransactionProvider {
        override suspend fun <T> runInTransaction(block: suspend () -> T): T = block()
    }
    private val storage = mockk<FirebaseStorage>(relaxed = true)
    private val loggingPort = mockk<LoggingPort>(relaxed = true)
    private val cryptoManager = mockk<CryptoManagerPort>(relaxed = true)
    private val userIdProvider: () -> String? = { "user123" }

    private lateinit var repository: ChatRepositoryImpl
    private lateinit var worker: MessageFileCloudSyncWorker
    private lateinit var dataSource: FirestoreDataSource

    @Before
    fun setup() {
        repository = ChatRepositoryImpl(
            syncTrigger = syncTrigger,
            chatDao = chatDao,
            messageDao = messageDao,
            webSearchSourceDao = webSearchSourceDao,
            messageFileDao = messageFileDao,
            transactionProvider = transactionProvider
        )

        dataSource = FirestoreDataSource(documentDao = mockk(relaxed = true), 
            firestore = firestore,
            chatDao = chatDao,
            messageDao = messageDao,
            memoriesDao = memoriesDao,
            webSearchSourceDao = webSearchSourceDao,
            ttsProviderDao = ttsProviderDao,
            mediaProviderDao = mediaProviderDao,
            apiModelConfigurationsDao = apiModelConfigurationsDao,
            localModelsDao = localModelsDao,
            apiCredentialsDao = apiCredentialsDao,
            localModelConfigurationsDao = localModelConfigurationsDao,
            defaultModelsDao = defaultModelsDao,
            studioMediaDao = studioMediaDao,
            studioAlbumDao = studioAlbumDao,
            messageFileDao = messageFileDao,
            messageVisionAnalysisDao = messageVisionAnalysisDao,
            chatSummaryDao = chatSummaryDao,
            embeddingDao = embeddingDao,
            apiKeyManager = apiKeyManager,
            cryptoManager = cryptoManager,
            loggingPort = loggingPort,
            externalScope = testScope,
            userIdProvider = userIdProvider,
            embeddingCoordinator = embeddingCoordinator
        )

        val context = mockk<Context>(relaxed = true)
        val workerParams = mockk<WorkerParameters>(relaxed = true)
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

        every { cryptoManager.encrypt(any()) } returns Result.success("encrypted".toByteArray())
        every { cryptoManager.hasCachedKey() } returns true
        every { firestore.runBatch(any<com.google.firebase.firestore.WriteBatch.Function>()) } returns Tasks.forResult(null)
    }

    @Test
    fun `artifact generation flow - from repo to storage upload`() = runTest {
        val messageId = MessageId("msg1")
        val artifact = ArtifactGenerationRequest(title = "Report", content = "# Markdown")
        
        // 1. Persist artifact via repository
        coEvery { messageFileDao.getByIdIncludingDeleted(any()) } returns null
        coEvery { messageFileDao.getByMessageId(any()) } returns emptyList()
        val capturedFiles = mutableListOf<MessageFileEntity>()
        coEvery { messageFileDao.insert(capture(capturedFiles)) } returns Unit
        
        repository.persistAllMessageData(
            messageId = messageId,
            modelType = ModelType.FAST,
            thinkingStartTime = 0L,
            thinkingEndTime = 0L,
            thinkingDuration = 0,
            thinkingRaw = null,
            content = "Here is the report",
            messageState = MessageState.COMPLETE,
            pipelineStep = null,
            webSearchSources = emptyList(),
            artifacts = listOf(artifact)
        )

        assertEquals(1, capturedFiles.size)
        val savedFile = capturedFiles[0]
        assertEquals("Report", savedFile.name)
        assertEquals("# Markdown", savedFile.content)
        assertEquals(MimeType.MARKDOWN, savedFile.mimeType)
        assertEquals(FileSource.GENERATED, savedFile.fileSource)
        assertEquals(MimeType.PDF, savedFile.targetFormat)
        assertEquals("PENDING_UPLOAD", savedFile.syncStatus)

        // 2. Run worker to upload to Storage
        coEvery { messageFileDao.getPendingUploads() } returns listOf(savedFile)
        coEvery { messageFileDao.getPendingDeletions() } returns emptyList()
        
        val storageRef = mockk<StorageReference>(relaxed = true)
        val fileRef = mockk<StorageReference>(relaxed = true)
        val uploadTask = mockk<UploadTask>(relaxed = true)
        val downloadUrlTask = mockk<com.google.android.gms.tasks.Task<android.net.Uri>>(relaxed = true)

        every { storage.reference } returns storageRef
        every { storageRef.child(any()) } returns fileRef
        every { fileRef.putBytes(any()) } returns uploadTask
        
        mockkStatic("kotlinx.coroutines.tasks.TasksKt")
        mockkStatic("android.net.Uri")
        val mockUri = mockk<android.net.Uri>()
        every { mockUri.toString() } returns "https://firebase.storage/report.pdf"
        every { android.net.Uri.parse(any()) } returns mockUri
        
        coEvery { uploadTask.await() } returns mockk()
        every { fileRef.downloadUrl } returns downloadUrlTask
        coEvery { downloadUrlTask.await() } returns mockUri
        
        coEvery { cryptoManager.encryptBytes(any()) } returns Result.success("encrypted".toByteArray())

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        
        // Verify storage upload
        verify { fileRef.putBytes("encrypted".toByteArray()) }
        
        // Verify DB update with remote URL and pendingSync=1
        coVerify { 
            messageFileDao.markUploaded(savedFile.id, "https://firebase.storage/report.pdf", any())
        }
    }

    @Test
    fun `sync VisionAnalysis to firestore`() = runTest {
        val entity = MessageVisionAnalysisEntity(
            id = "vision1",
            userMessageId = MessageId("msg1"),
            imageUri = "content://media/1",
            promptText = "Describe this",
            analysisText = "A sunset",
            modelType = ModelType.VISION,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            pendingSync = 1
        )

        coEvery { messageVisionAnalysisDao.claimPending() } returns Unit
        coEvery { messageVisionAnalysisDao.getClaimedForSync() } returns listOf(entity)
        coEvery { messageVisionAnalysisDao.markSynced(listOf(entity.id)) } returns Unit

        dataSource.syncPendingChanges()

        coVerify { messageVisionAnalysisDao.markSynced(listOf(entity.id)) }
    }

    @Test
    fun `sync MessageFile to firestore`() = runTest {
        val entity = MessageFileEntity(
            id = "file1",
            messageId = "msg1",
            name = "file.txt",
            content = "Hello",
            mimeType = MimeType.PLAIN_TEXT,
            fileSource = FileSource.ATTACHMENT,
            remoteUrl = "https://example.com/file.txt",
            syncStatus = "UPLOADED",
            pendingSync = 1,
            updatedAt = System.currentTimeMillis(),
            deleted = false
        )

        coEvery { messageFileDao.claimPending() } returns Unit
        coEvery { messageFileDao.getClaimedForSync() } returns listOf(entity)
        coEvery { messageFileDao.markSynced(listOf(entity.id)) } returns Unit

        dataSource.syncPendingChanges()

        coVerify { messageFileDao.markSynced(listOf(entity.id)) }
    }

    @Test
    fun `sync WebSearchSource to firestore`() = runTest {
        val entity = WebSearchSourceEntity(
            id = "source1",
            messageId = MessageId("msg1"),
            title = "Google",
            url = "https://google.com",
            content = "Result",
            score = 1.0,
            extracted = true,
            pendingSync = 1,
            updatedAt = System.currentTimeMillis(),
            deleted = false
        )

        coEvery { webSearchSourceDao.claimPending() } returns Unit
        coEvery { webSearchSourceDao.getClaimedForSync() } returns listOf(entity)
        coEvery { webSearchSourceDao.markSynced(listOf(entity.id)) } returns Unit

        dataSource.syncPendingChanges()

        coVerify { webSearchSourceDao.markSynced(listOf(entity.id)) }
    }

    @Test
    fun `sync TtsProvider to firestore`() = runTest {
        val entity = TtsProviderEntity(
            id = "tts1",
            displayName = "OpenAI TTS",
            provider = ApiProvider.OPENAI,
            voiceName = "alloy",
            credentialAlias = "openai_key",
            pendingSync = 1,
            updatedAt = System.currentTimeMillis(),
            deleted = false
        )

        coEvery { ttsProviderDao.claimPending() } returns Unit
        coEvery { ttsProviderDao.getClaimedForSync() } returns listOf(entity)
        coEvery { ttsProviderDao.markSynced(listOf(entity.id)) } returns Unit

        dataSource.syncPendingChanges()

        coVerify { ttsProviderDao.markSynced(listOf(entity.id)) }
    }

    @Test
    fun `sync MediaProvider to firestore`() = runTest {
        val entity = MediaProviderEntity(
            id = "media1",
            displayName = "DALL-E 3",
            provider = ApiProvider.OPENAI,
            capability = MediaCapability.IMAGE,
            credentialAlias = "openai_key",
            pendingSync = 1,
            updatedAt = System.currentTimeMillis(),
            deleted = false
        )

        coEvery { mediaProviderDao.claimPending() } returns Unit
        coEvery { mediaProviderDao.getClaimedForSync() } returns listOf(entity)
        coEvery { mediaProviderDao.markSynced(listOf(entity.id)) } returns Unit

        dataSource.syncPendingChanges()

        coVerify { mediaProviderDao.markSynced(listOf(entity.id)) }
    }

    @Test
    fun `sync StudioAlbum to firestore`() = runTest {
        val entity = StudioAlbumEntity(
            id = "album1",
            name = "Vacation",
            pendingSync = 1,
            updatedAt = System.currentTimeMillis(),
            deleted = false
        )

        coEvery { studioAlbumDao.claimPending() } returns Unit
        coEvery { studioAlbumDao.getClaimedForSync() } returns listOf(entity)
        coEvery { studioAlbumDao.markSynced(listOf(entity.id)) } returns Unit

        dataSource.syncPendingChanges()

        coVerify { studioAlbumDao.markSynced(listOf(entity.id)) }
    }
}
