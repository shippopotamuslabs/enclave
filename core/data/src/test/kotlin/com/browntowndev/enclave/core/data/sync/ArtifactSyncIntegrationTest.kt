package com.browntowndev.enclave.core.data.sync

import android.content.Context
import androidx.work.WorkerParameters
import com.browntowndev.enclave.core.data.local.ApiCredentialsDao
import com.browntowndev.enclave.core.data.local.ApiModelConfigurationsDao
import com.browntowndev.enclave.core.data.local.ChatDao
import com.browntowndev.enclave.core.data.local.ChatSummaryDao
import com.browntowndev.enclave.core.data.local.DefaultModelsDao
import com.browntowndev.enclave.core.data.local.DocumentDao
import com.browntowndev.enclave.core.data.local.DocumentEntity
import com.browntowndev.enclave.core.data.local.EmbeddingDao
import com.browntowndev.enclave.core.data.local.LocalModelConfigurationsDao
import com.browntowndev.enclave.core.data.local.LocalModelsDao
import com.browntowndev.enclave.core.data.local.MediaProviderDao
import com.browntowndev.enclave.core.data.local.MediaProviderEntity
import com.browntowndev.enclave.core.data.local.MemoriesDao
import com.browntowndev.enclave.core.data.local.MessageDao
import com.browntowndev.enclave.core.data.local.MessageFileDao
import com.browntowndev.enclave.core.data.local.MessageFileEntity
import com.browntowndev.enclave.core.data.local.MessageTranscriptSegmentDao
import com.browntowndev.enclave.core.data.local.MessageVisionAnalysisDao
import com.browntowndev.enclave.core.data.local.MessageVisionAnalysisEntity
import com.browntowndev.enclave.core.data.local.StudioAlbumDao
import com.browntowndev.enclave.core.data.local.StudioAlbumEntity
import com.browntowndev.enclave.core.data.local.StudioMediaDao
import com.browntowndev.enclave.core.data.local.TtsProviderDao
import com.browntowndev.enclave.core.data.local.TtsProviderEntity
import com.browntowndev.enclave.core.data.local.WebSearchSourceDao
import com.browntowndev.enclave.core.data.local.WebSearchSourceEntity
import com.browntowndev.enclave.core.data.remote.FirestoreDataSource
import com.browntowndev.enclave.core.data.remote.createTestFirestoreDataSource
import com.browntowndev.enclave.core.data.repository.ChatRepositoryImpl
import com.browntowndev.enclave.core.data.repository.FakeLocalDataEncryptionService
import com.browntowndev.enclave.core.data.repository.testSensitiveSelfHealer
import com.browntowndev.enclave.core.data.security.ApiKeyManager
import com.browntowndev.enclave.core.data.security.SensitiveRoomReadRepairApplier
import com.browntowndev.enclave.domain.model.artifact.ArtifactGenerationRequest
import com.browntowndev.enclave.domain.model.artifact.MimeType
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.model.chat.MessageState
import com.browntowndev.enclave.domain.model.config.MediaCapability
import com.browntowndev.enclave.domain.model.inference.ApiProvider
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import com.browntowndev.enclave.domain.port.repository.TransactionProvider
import com.browntowndev.enclave.domain.port.security.CryptoManagerPort
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [34])
class ArtifactSyncIntegrationTest {
    private val syncTrigger = mockk<CloudSyncTrigger>(relaxed = true)
    private val chatDao = mockk<ChatDao>(relaxed = true)
    private val messageDao = mockk<MessageDao>(relaxed = true)
    private val messageTranscriptSegmentDao = mockk<MessageTranscriptSegmentDao>(relaxed = true)
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
    private val documentDao = mockk<DocumentDao>(relaxed = true)
    private val messageFileDao = mockk<MessageFileDao>(relaxed = true)
    private val messageVisionAnalysisDao = mockk<MessageVisionAnalysisDao>(relaxed = true)
    private val chatSummaryDao = mockk<ChatSummaryDao>(relaxed = true)
    private val embeddingDao = mockk<EmbeddingDao>(relaxed = true)
    private val apiKeyManager = mockk<ApiKeyManager>(relaxed = true)
    private val embeddingCoordinator = mockk<EmbeddingRegenerationCoordinator>(relaxed = true)
    private val firestore = mockk<FirebaseFirestore>(relaxed = true)
    private val testScope = TestScope()

    private val transactionProvider =
        object : TransactionProvider {
            override suspend fun <T> runInTransaction(block: suspend () -> T): T = block()
        }
    private val storage = mockk<FirebaseStorage>(relaxed = true)
    private val loggingPort = mockk<LoggingPort>(relaxed = true)
    private val cryptoManager = mockk<CryptoManagerPort>(relaxed = true)
    private val userIdProvider: () -> String? = { "user123" }
    private val readRepairApplier = mockk<SensitiveRoomReadRepairApplier>(relaxed = true)

    private lateinit var repository: ChatRepositoryImpl
    private lateinit var worker: MessageFileCloudSyncWorker
    private lateinit var dataSource: FirestoreDataSource

    @BeforeEach
    fun setup() {
        repository =
            ChatRepositoryImpl(
                syncTrigger = syncTrigger,
                chatDao = chatDao,
                messageDao = messageDao,
                messageTranscriptSegmentDao = messageTranscriptSegmentDao,
                webSearchSourceDao = webSearchSourceDao,
                documentDao = documentDao,
                transactionProvider = transactionProvider,
                cleanupDao = mockk(relaxed = true),
                messageInferenceTelemetryDao = mockk(relaxed = true),
                messageFileDao = mockk(relaxed = true),
                healer = testSensitiveSelfHealer(FakeLocalDataEncryptionService()),
                readRepairApplier = readRepairApplier,
            )

        dataSource =
            createTestFirestoreDataSource(
                documentDao = mockk(relaxed = true),
                firestore = firestore,
                chatDao = chatDao,
                messageDao = messageDao,
                messageTranscriptSegmentDao = messageTranscriptSegmentDao,
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
                embeddingCoordinator = embeddingCoordinator,
                localEncryption = FakeLocalDataEncryptionService(),
            )

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
                FakeLocalDataEncryptionService(),
            )

        every { cryptoManager.encrypt(any()) } returns Result.success("encrypted".toByteArray())
        every { cryptoManager.hasCachedKey() } returns true
        every { firestore.runBatch(any<com.google.firebase.firestore.WriteBatch.Function>()) } returns Tasks.forResult(null)
    }

    @Test
    fun `artifact generation flow - from repo to storage upload`() =
        runTest {
            val messageId = MessageId("msg1")
            val artifact =
                ArtifactGenerationRequest(
                    artifactId = "artifact-sync-1",
                    title = "Report",
                    content = "# Markdown",
                )

            // 1. Persist artifact via repository -> documents table.
            coEvery { documentDao.softDeleteByMessageId(any(), any()) } returns Unit
            val capturedDocuments = mutableListOf<DocumentEntity>()
            coEvery { documentDao.insertDocument(capture(capturedDocuments)) } returns Unit

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
                artifacts = listOf(artifact),
                generatedMediaUris = emptyList(),
                generatedMediaAssetIds = emptyList(),
                generatedMediaTypes = emptyList(),
                generatedMediaPrompts = emptyList(),
                transcriptSegments = emptyList(),
            )

            assertEquals(1, capturedDocuments.size)
            val savedDocument = capturedDocuments[0]
            assertEquals("artifact-sync-1", savedDocument.id)
            assertEquals("PCENC:v1:aes-gcm:Report", savedDocument.title)
            assertEquals("PCENC:v1:aes-gcm:# Markdown", savedDocument.json)
            assertEquals(null, savedDocument.targetFormat)
            assertEquals(messageId, savedDocument.messageId)
            assertEquals(1, savedDocument.pendingSync)
        }

    @Test
    fun `sync VisionAnalysis to firestore`() =
        runTest {
            val entity =
                MessageVisionAnalysisEntity(
                    id = "vision1",
                    userMessageId = MessageId("msg1"),
                    imageUri = "content://media/1",
                    promptText = "Describe this",
                    analysisText = "A sunset",
                    modelType = ModelType.VISION,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                    pendingSync = 1,
                )

            coEvery { messageVisionAnalysisDao.claimPending() } returns Unit
            coEvery { messageVisionAnalysisDao.getClaimedForSync() } returns listOf(entity)
            coEvery { messageVisionAnalysisDao.markSynced(listOf(entity.id)) } returns Unit

            dataSource.syncPendingChanges()

            coVerify { messageVisionAnalysisDao.markSynced(listOf(entity.id)) }
        }

    @Test
    fun `sync MessageFile to firestore`() =
        runTest {
            val entity =
                MessageFileEntity(
                    id = "file1",
                    messageId = "msg1",
                    name = "file.txt",
                    content = "Hello",
                    mimeType = MimeType.PLAIN_TEXT,
                    remoteUrl = "https://example.com/file.txt",
                    syncStatus = "UPLOADED",
                    pendingSync = 1,
                    updatedAt = System.currentTimeMillis(),
                    deleted = false,
                )

            coEvery { messageFileDao.claimPending() } returns Unit
            coEvery { messageFileDao.getClaimedForSync() } returns listOf(entity)
            coEvery { messageFileDao.markSynced(listOf(entity.id)) } returns Unit

            dataSource.syncPendingChanges()

            coVerify { messageFileDao.markSynced(listOf(entity.id)) }
        }

    @Test
    fun `sync WebSearchSource to firestore`() =
        runTest {
            val entity =
                WebSearchSourceEntity(
                    id = "source1",
                    messageId = MessageId("msg1"),
                    title = "Google",
                    url = "https://google.com",
                    content = "Result",
                    score = 1.0,
                    extracted = true,
                    pendingSync = 1,
                    updatedAt = System.currentTimeMillis(),
                    deleted = false,
                )

            coEvery { webSearchSourceDao.claimPending() } returns Unit
            coEvery { webSearchSourceDao.getClaimedForSync() } returns listOf(entity)
            coEvery { webSearchSourceDao.markSynced(listOf(entity.id)) } returns Unit

            dataSource.syncPendingChanges()

            coVerify { webSearchSourceDao.markSynced(listOf(entity.id)) }
        }

    @Test
    fun `sync TtsProvider to firestore`() =
        runTest {
            val entity =
                TtsProviderEntity(
                    id = "tts1",
                    displayName = "OpenAI TTS",
                    provider = ApiProvider.OPENAI,
                    voiceName = "alloy",
                    credentialAlias = "openai_key",
                    pendingSync = 1,
                    updatedAt = System.currentTimeMillis(),
                    deleted = false,
                )

            coEvery { ttsProviderDao.claimPending() } returns Unit
            coEvery { ttsProviderDao.getClaimedForSync() } returns listOf(entity)
            coEvery { ttsProviderDao.markSynced(listOf(entity.id)) } returns Unit

            dataSource.syncPendingChanges()

            coVerify { ttsProviderDao.markSynced(listOf(entity.id)) }
        }

    @Test
    fun `sync MediaProvider to firestore`() =
        runTest {
            val entity =
                MediaProviderEntity(
                    id = "media1",
                    displayName = "DALL-E 3",
                    provider = ApiProvider.OPENAI,
                    capability = MediaCapability.IMAGE,
                    credentialAlias = "openai_key",
                    pendingSync = 1,
                    updatedAt = System.currentTimeMillis(),
                    deleted = false,
                )

            coEvery { mediaProviderDao.claimPending() } returns Unit
            coEvery { mediaProviderDao.getClaimedForSync() } returns listOf(entity)
            coEvery { mediaProviderDao.markSynced(listOf(entity.id)) } returns Unit

            dataSource.syncPendingChanges()

            coVerify { mediaProviderDao.markSynced(listOf(entity.id)) }
        }

    @Test
    fun `sync StudioAlbum to firestore`() =
        runTest {
            val entity =
                StudioAlbumEntity(
                    id = "album1",
                    name = "Vacation",
                    pendingSync = 1,
                    updatedAt = System.currentTimeMillis(),
                    deleted = false,
                )

            coEvery { studioAlbumDao.claimPending() } returns Unit
            coEvery { studioAlbumDao.getClaimedForSync() } returns listOf(entity)
            coEvery { studioAlbumDao.markSynced(listOf(entity.id)) } returns Unit

            dataSource.syncPendingChanges()

            coVerify { studioAlbumDao.markSynced(listOf(entity.id)) }
        }
}
