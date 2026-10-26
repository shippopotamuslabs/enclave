@file:Suppress("ForbiddenMockKInTests") // FirebaseFirestore/FirebaseStorage are Firebase SDK types kept as mocks; all Enclave-owned collaborators are real Room DAOs and :core:testing fakes.

package com.browntowndev.enclave.core.data.sync

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
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
import com.browntowndev.enclave.core.data.local.MediaProviderEntity
import com.browntowndev.enclave.core.data.local.MessageDao
import com.browntowndev.enclave.core.data.local.dao.message.MessageDaoPersist
import com.browntowndev.enclave.core.data.local.MessageEntity
import com.browntowndev.enclave.core.data.local.MessageFileEntity
import com.browntowndev.enclave.core.data.local.MessageVisionAnalysisEntity
import com.browntowndev.enclave.core.data.local.StudioAlbumEntity
import com.browntowndev.enclave.core.data.local.TtsProviderEntity
import com.browntowndev.enclave.core.data.local.WebSearchSourceEntity
import com.browntowndev.enclave.core.data.remote.FirestoreDataSource
import com.browntowndev.enclave.core.data.remote.createTestFirestoreDataSource
import com.browntowndev.enclave.core.data.repository.ChatRepositoryImpl
import com.browntowndev.enclave.core.data.repository.FakeLocalDataEncryptionService
import com.browntowndev.enclave.core.data.repository.testSensitiveSelfHealer
import com.browntowndev.enclave.core.data.security.ApiKeyManager
import com.browntowndev.enclave.core.data.security.SensitiveRoomReadRepairApplier
import com.browntowndev.enclave.core.testing.FakeCryptoManagerPort
import com.browntowndev.enclave.core.testing.FakeEntitlementPort
import com.browntowndev.enclave.core.testing.FakeLoggingPort
import com.browntowndev.enclave.domain.model.artifact.ArtifactGenerationRequest
import com.browntowndev.enclave.domain.model.artifact.MimeType
import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.model.chat.MessageState
import com.browntowndev.enclave.domain.model.chat.Role
import com.browntowndev.enclave.domain.model.config.MediaCapability
import com.browntowndev.enclave.domain.model.inference.ApiProvider
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.port.repository.TransactionProvider
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension
import java.io.File
import java.util.Date
import java.util.concurrent.Executor

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [34])
class ArtifactSyncIntegrationTest {
    private lateinit var context: Context
    private lateinit var database: EnclaveDatabase
    private lateinit var syncTrigger: CloudSyncTrigger
    private lateinit var embeddingCoordinator: EmbeddingRegenerationCoordinator

    private val firestore = mockk<FirebaseFirestore>(relaxed = true)
    private val storage = mockk<FirebaseStorage>(relaxed = true)
    private val testScope = TestScope()

    private val transactionProvider =
        object : TransactionProvider {
            override suspend fun <T> runInTransaction(block: suspend () -> T): T = block()
        }
    private val loggingPort = FakeLoggingPort()
    private val cryptoManager = FakeCryptoManagerPort()
    private val userIdProvider: () -> String? = { "user123" }

    private lateinit var repository: ChatRepositoryImpl
    private lateinit var worker: MessageFileCloudSyncWorker
    private lateinit var dataSource: FirestoreDataSource

    private fun messageDao(): MessageDao =
        MessageDao(
            database.messageQueryDao(),
            database.messageCommandDao(),
            database.messageSyncCommandDao(),
            MessageDaoPersist(database, database.messageCommandDao(), database.messageSyncCommandDao()),
        )

    /** Seeds the FK parents of the "msg1"-keyed child rows (chat "chat1" -> message "msg1"). */
    private suspend fun seedChatAndMessage() {
        database.chatDao().insertIfAbsent(
            ChatEntity(id = ChatId("chat1"), name = "Chat", created = Date(0), lastModified = Date(0), pinned = false),
        )
        database.messageCommandDao().insertIfAbsent(
            MessageEntity(id = MessageId("msg1"), chatId = ChatId("chat1"), content = "seed", role = Role.ASSISTANT),
        )
    }

    private fun readRepairApplier(): SensitiveRoomReadRepairApplier =
        SensitiveRoomReadRepairApplier(
            chatDao = database.chatDao(),
            messageDao = messageDao(),
            messageFileDao = database.messageFileDao(),
            chatSummaryDao = database.chatSummaryDao(),
            memoriesDao = database.memoriesDao(),
            messageVisionAnalysisDao = database.messageVisionAnalysisDao(),
            webSearchSourceDao = database.webSearchSourceDao(),
            messageTranscriptSegmentDao = database.messageTranscriptSegmentDao(),
            documentDao = database.documentDao(),
            apiCredentialsDao = database.apiCredentialsDao(),
            apiModelConfigurationsDao = database.apiModelConfigurationsDao(),
            localModelConfigurationsDao = database.localModelConfigurationsDao(),
            ttsProviderDao = database.ttsProviderDao(),
            mediaProviderDao = database.mediaProviderDao(),
            studioAlbumDao = database.studioAlbumDao(),
            studioMediaDao = database.studioMediaDao(),
        )

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
        val workManager = WorkManager.getInstance(context)
        database =
            Room
                .inMemoryDatabaseBuilder(context, EnclaveDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        syncTrigger = CloudSyncTrigger(workManager, loggingPort, FakeEntitlementPort())
        embeddingCoordinator = EmbeddingRegenerationCoordinator(workManager, testScope)

        val apiKeyManager =
            ApiKeyManager(
                prefsProvider = { context.getSharedPreferences("byok_test_keys", Context.MODE_PRIVATE) },
                dataStore =
                    // The DataStore MUST live on a scope that actually runs: its internal
                    // actor is launched on this scope, and the standalone TestScope's
                    // scheduler is never advanced outside runTest — with scope = testScope
                    // every DataStore read/edit suspended forever. That deadlocked
                    // syncPendingChanges(): the sync engine always traverses the ApiKey
                    // adapter, whose claimPending/getClaimedForSync call
                    // ApiKeyManager.claimPending()/getClaimedSyncAliases() ->
                    // dataStore.edit/data.first(), producing the 60s runTest timeouts
                    // (UncompletedCoroutinesError) in the Tts/Media/StudioAlbum tests.
                    PreferenceDataStoreFactory.create(scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)) {
                        File(context.filesDir, "artifact_sync_api_keys.preferences_pb")
                    },
                syncScope = testScope,
            )

        repository =
            ChatRepositoryImpl(
                syncTrigger = syncTrigger,
                chatDao = database.chatDao(),
                messageDao = messageDao(),
                messageTranscriptSegmentDao = database.messageTranscriptSegmentDao(),
                webSearchSourceDao = database.webSearchSourceDao(),
                documentDao = database.documentDao(),
                transactionProvider = transactionProvider,
                cleanupDao = database.cleanupDao(),
                messageInferenceTelemetryDao = database.messageInferenceTelemetryDao(),
                messageFileDao = database.messageFileDao(),
                healer = testSensitiveSelfHealer(FakeLocalDataEncryptionService(), FakeLoggingPort()),
                readRepairApplier = readRepairApplier(),
            )

        dataSource =
            createTestFirestoreDataSource(
                documentDao = database.documentDao(),
                firestore = firestore,
                chatDao = database.chatDao(),
                messageDao = messageDao(),
                messageTranscriptSegmentDao = database.messageTranscriptSegmentDao(),
                memoriesDao = database.memoriesDao(),
                webSearchSourceDao = database.webSearchSourceDao(),
                ttsProviderDao = database.ttsProviderDao(),
                mediaProviderDao = database.mediaProviderDao(),
                apiModelConfigurationsDao = database.apiModelConfigurationsDao(),
                localModelsDao = database.localModelsDao(),
                apiCredentialsDao = database.apiCredentialsDao(),
                localModelConfigurationsDao = database.localModelConfigurationsDao(),
                defaultModelsDao = database.defaultModelsDao(),
                studioMediaDao = database.studioMediaDao(),
                studioAlbumDao = database.studioAlbumDao(),
                messageFileDao = database.messageFileDao(),
                messageVisionAnalysisDao = database.messageVisionAnalysisDao(),
                chatSummaryDao = database.chatSummaryDao(),
                embeddingDao = database.embeddingDao(),
                apiKeyManager = apiKeyManager,
                cryptoManager = cryptoManager,
                loggingPort = loggingPort,
                externalScope = testScope,
                userIdProvider = userIdProvider,
                embeddingCoordinator = embeddingCoordinator,
                localEncryption = FakeLocalDataEncryptionService(),
            )

        worker =
            TestListenableWorkerBuilder<MessageFileCloudSyncWorker>(context)
                .setWorkerFactory(MessageFileWorkerFactory())
                .build()

        cryptoManager.encryptResult = Result.success("encrypted".toByteArray())
        cryptoManager.hasCachedKeyValue = true
        every { firestore.runBatch(any<com.google.firebase.firestore.WriteBatch.Function>()) } returns Tasks.forResult(null)
    }

    @AfterEach
    fun tearDown() {
        database.close()
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

            // Seed the owning chat + message rows (documents carry an FK to message).
            database.chatDao().insertIfAbsent(
                ChatEntity(id = ChatId("chat1"), name = "Chat", created = Date(0), lastModified = Date(0), pinned = false),
            )
            database.messageCommandDao().insertIfAbsent(
                MessageEntity(id = messageId, chatId = ChatId("chat1"), content = "seed", role = Role.ASSISTANT),
            )

            // 1. Persist artifact via repository -> documents table.
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

            val savedDocument = database.documentDao().getDocumentById("artifact-sync-1")
            assertEquals("artifact-sync-1", savedDocument?.id)
            assertEquals("PCENC:v1:aes-gcm:Report", savedDocument?.title)
            assertEquals("PCENC:v1:aes-gcm:# Markdown", savedDocument?.json)
            assertEquals(null, savedDocument?.targetFormat)
            assertEquals(messageId, savedDocument?.messageId)
            assertEquals(1, savedDocument?.pendingSync)
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
            // FK parents first: vision analysis references message.id ("msg1"),
            // which itself references chat.id ("chat1").
            seedChatAndMessage()
            database.messageVisionAnalysisDao().upsert(entity)

            dataSource.syncPendingChanges()

            assertEquals(0, database.messageVisionAnalysisDao().getById(entity.id)?.pendingSync)
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
            // FK parents first: message files reference message.id ("msg1"),
            // which itself references chat.id ("chat1").
            seedChatAndMessage()
            database.messageFileDao().insert(entity)

            dataSource.syncPendingChanges()

            assertEquals(0, database.messageFileDao().getById(entity.id)?.pendingSync)
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
            // FK parents first: web search sources reference message.id ("msg1"),
            // which itself references chat.id ("chat1").
            seedChatAndMessage()
            database.webSearchSourceDao().upsert(entity)

            dataSource.syncPendingChanges()

            assertEquals(0, database.webSearchSourceDao().getById(entity.id)?.pendingSync)
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
            database.ttsProviderDao().upsertTtsProvider(entity)

            dataSource.syncPendingChanges()

            assertEquals(0, database.ttsProviderDao().getTtsProvider(entity.id)?.pendingSync)
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
            database.mediaProviderDao().upsert(entity)

            dataSource.syncPendingChanges()

            assertEquals(0, database.mediaProviderDao().getMediaProvider(entity.id)?.pendingSync)
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
            database.studioAlbumDao().insertAlbum(entity)

            dataSource.syncPendingChanges()

            assertEquals(0, database.studioAlbumDao().getAlbumById(entity.id)?.pendingSync)
        }

    private inner class MessageFileWorkerFactory : androidx.work.WorkerFactory() {
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
                FakeLocalDataEncryptionService(),
            )
    }
}
