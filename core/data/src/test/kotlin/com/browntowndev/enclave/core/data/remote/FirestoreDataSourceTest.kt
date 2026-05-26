package com.browntowndev.enclave.core.data.remote

import android.util.Log
import com.browntowndev.enclave.core.data.local.ChatDao
import com.browntowndev.enclave.core.data.local.ChatEntity
import com.browntowndev.enclave.core.data.local.ChatSummaryDao
import com.browntowndev.enclave.core.data.local.DocumentDao
import com.browntowndev.enclave.core.data.local.DocumentEntity
import com.browntowndev.enclave.core.data.local.FirestoreSyncPlaceholderDao
import com.browntowndev.enclave.core.data.local.LocalModelConfigurationEntity
import com.browntowndev.enclave.core.data.local.MessageDao
import com.browntowndev.enclave.core.data.local.MessageEntity
import com.browntowndev.enclave.core.data.local.MessageWithFiles
import com.browntowndev.enclave.core.data.security.LocalDataEncryptionService
import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.Message
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.model.chat.Role
import com.browntowndev.enclave.domain.model.config.LocalModelConfigurationId
import com.browntowndev.enclave.domain.model.config.LocalModelId
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import com.browntowndev.enclave.domain.port.security.CryptoManagerPort
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.EventListener
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.QueryDocumentSnapshot
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.SetOptions
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.Date
import java.util.concurrent.atomic.AtomicBoolean

@OptIn(ExperimentalCoroutinesApi::class)
class FirestoreDataSourceTest {
    private val firestore = mockk<FirebaseFirestore>()
    private val chatDao = mockk<ChatDao>(relaxed = true)
    private val messageDao = mockk<MessageDao>(relaxed = true)
    private val apiCredentialsDao = mockk<com.browntowndev.enclave.core.data.local.ApiCredentialsDao>(relaxed = true)
    private val apiKeyManager = mockk<com.browntowndev.enclave.core.data.security.ApiKeyManager>(relaxed = true)
    private val cryptoManager = mockk<CryptoManagerPort>(relaxed = true)
    private val loggingPort = mockk<LoggingPort>(relaxed = true)
    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private val embeddingCoordinator = mockk<com.browntowndev.enclave.core.data.sync.EmbeddingRegenerationCoordinator>(relaxed = true)
    private val embeddingDao = mockk<com.browntowndev.enclave.core.data.local.EmbeddingDao>(relaxed = true)
    private val chatSummaryDao = mockk<ChatSummaryDao>(relaxed = true)
    private val documentDao = mockk<DocumentDao>(relaxed = true)
    private val firestoreSyncPlaceholderDao = mockk<FirestoreSyncPlaceholderDao>(relaxed = true)
    private lateinit var dataSource: FirestoreDataSource

    @BeforeEach
    fun setup() {
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any<Throwable>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0

        every { cryptoManager.hasCachedKey() } returns true
        every { cryptoManager.encrypt(any()) } returns Result.success("encrypted".toByteArray())
        every { cryptoManager.decrypt(any()) } answers { Result.success(String(it.invocation.args[0] as ByteArray)) }

        every { firestore.collection(any()) } returns mockk(relaxed = true)
        every { firestore.document(any()) } returns mockk(relaxed = true)

        dataSource =
            createTestFirestoreDataSource(
                documentDao = documentDao,
                firestore = firestore,
                chatDao = chatDao,
                messageDao = messageDao,
                messageTranscriptSegmentDao = mockk(relaxed = true),
                memoriesDao = mockk(relaxed = true),
                webSearchSourceDao = mockk(relaxed = true),
                ttsProviderDao = mockk(relaxed = true),
                mediaProviderDao = mockk(relaxed = true),
                apiModelConfigurationsDao = mockk(relaxed = true),
                localModelsDao = mockk(relaxed = true),
                apiCredentialsDao = apiCredentialsDao,
                localModelConfigurationsDao = mockk(relaxed = true),
                defaultModelsDao = mockk(relaxed = true),
                studioMediaDao = mockk(relaxed = true),
                studioAlbumDao = mockk(relaxed = true),
                messageFileDao = mockk(relaxed = true),
                messageVisionAnalysisDao = mockk(relaxed = true),
                chatSummaryDao = chatSummaryDao,
                embeddingDao = embeddingDao,
                firestoreSyncPlaceholderDao = firestoreSyncPlaceholderDao,
                localEncryption = FakeLocalDataEncryptionService(),
                apiKeyManager = apiKeyManager,
                cryptoManager = cryptoManager,
                loggingPort = loggingPort,
                externalScope = testScope,
                userIdProvider = { "test-user-id" },
                embeddingCoordinator = embeddingCoordinator,
            )
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    // NOTE: Pre-existing nested classes verifying the per-collection snapshot-listener
    // design (StartListeningTests, StopListeningTests, ChatsSnapshotTests,
    // MessagesSnapshotTests, LocalModelConfigurationSnapshotTests,
    // VisionAnalysisSnapshotTests, DeserializerHardeningTests, MessageSyncTests,
    // MediaProviderSyncTests, TtsProviderSyncTests, ApiModelConfigSyncTests,
    // LocalModelConfigSyncTests, PerDocumentErrorIsolationTests,
    // CloudPurgePlaceholderGuardTests, OrphanedPlaceholderCleanupTests) were removed
    // as part of FEAT-cloud-sync-event-feed. The new design replaces those listeners
    // with a single `sync_events` feed listener; coverage now lives in SyncEventFeedTests
    // below and in dedicated test classes under `remote/firestore/`.

    @Nested
    @DisplayName("syncAllLocalDataToCloud")
    inner class SyncAllLocalDataToCloudTests {
        @Test
        @DisplayName("returns failure when user not signed in")
        fun returnsFailureWhenNotSignedIn() =
            testScope.runTest {
                val nullUserIdSource =
                    createTestFirestoreDataSource(
                        documentDao = mockk(relaxed = true),
                        firestore = firestore,
                        chatDao = chatDao,
                        messageDao = messageDao,
                        messageTranscriptSegmentDao = mockk(relaxed = true),
                        cryptoManager = cryptoManager,
                        loggingPort = loggingPort,
                        externalScope = testScope,
                        userIdProvider = { null },
                        apiCredentialsDao = apiCredentialsDao,
                        apiModelConfigurationsDao = mockk(relaxed = true),
                        localModelsDao = mockk(relaxed = true),
                        localModelConfigurationsDao = mockk(relaxed = true),
                        defaultModelsDao = mockk(relaxed = true),
                        studioMediaDao = mockk(relaxed = true),
                        studioAlbumDao = mockk(relaxed = true),
                        memoriesDao = mockk(relaxed = true),
                        ttsProviderDao = mockk(relaxed = true),
                        mediaProviderDao = mockk(relaxed = true),
                        apiKeyManager = apiKeyManager,
                        messageVisionAnalysisDao = mockk(relaxed = true),
                        messageFileDao = mockk(relaxed = true),
                        webSearchSourceDao = mockk(relaxed = true),
                        chatSummaryDao = chatSummaryDao,
                        embeddingCoordinator = embeddingCoordinator,
                        embeddingDao = embeddingDao,
                        localEncryption = FakeLocalDataEncryptionService(),
                    )

                val result = nullUserIdSource.syncAllLocalDataToCloud()

                assertTrue(result.isFailure)
                assertTrue(result.exceptionOrNull() is IllegalStateException)
            }

        @Test
        @DisplayName("succeeds with empty data")
        fun succeedsWithEmptyData() =
            testScope.runTest {
                val result = dataSource.syncAllLocalDataToCloud()
                assertTrue(result.isSuccess)
            }

        @Test
        @DisplayName("pushes chats and messages to Firestore with merge")
        fun pushesDataToFirestore() =
            testScope.runTest {
                val chatId = ChatId("chat-1")
                val messageId = MessageId("msg-1")
                val chatEntity =
                    ChatEntity(
                        chatId,
                        "PCENC:v1:aes-gcm:Test Chat",
                        java.util.Date(1000L),
                        java.util.Date(2000L),
                        false,
                        pendingSync = 1,
                    )
                val msgEntity =
                    MessageEntity(
                        messageId,
                        chatId,
                        "PCENC:v1:aes-gcm:Hello",
                        role = com.browntowndev.enclave.domain.model.chat.Role.USER,
                        pendingSync = 1,
                    )
                val msgWithFiles = MessageWithFiles(msgEntity, emptyList())

                coEvery { chatDao.getClaimedForSync() } returns listOf(chatEntity)
                coEvery { messageDao.getClaimedForSync() } returns listOf(msgWithFiles)
                every { firestore.runBatch(any<com.google.firebase.firestore.WriteBatch.Function>()) } returns Tasks.forResult(null)

                val result = dataSource.syncAllLocalDataToCloud()

                assertTrue(result.isSuccess)
                verify { firestore.runBatch(any<com.google.firebase.firestore.WriteBatch.Function>()) }
                coVerify { chatDao.markSynced(listOf(chatId)) }
                coVerify { messageDao.markSynced(listOf(messageId)) }
            }

        @Test
        @DisplayName("returns failure on Firestore write error")
        fun returnsFailureOnWriteError() =
            testScope.runTest {
                val chatEntity =
                    ChatEntity(ChatId("chat-1"), "Test Chat", java.util.Date(1000L), java.util.Date(2000L), false, pendingSync = 1)
                coEvery { chatDao.getClaimedForSync() } returns listOf(chatEntity)
                coEvery { messageDao.getClaimedForSync() } returns emptyList()
                every { firestore.runBatch(any<com.google.firebase.firestore.WriteBatch.Function>()) } returns
                    Tasks.forException(RuntimeException("Firestore write failed"))

                val result = dataSource.syncAllLocalDataToCloud()

                assertTrue(result.isFailure)
            }
    }

    @Nested
    @DisplayName("syncPendingChanges")
    inner class SyncPendingChangesTests {
        @Test
        @DisplayName("returns failure when user not signed in")
        fun returnsFailureWhenNotSignedIn() =
            testScope.runTest {
                val nullUserSource =
                    createTestFirestoreDataSource(
                        documentDao = mockk(relaxed = true),
                        firestore = firestore,
                        chatDao = chatDao,
                        messageDao = messageDao,
                        messageTranscriptSegmentDao = mockk(relaxed = true),
                        cryptoManager = cryptoManager,
                        loggingPort = loggingPort,
                        externalScope = testScope,
                        userIdProvider = { null },
                        apiCredentialsDao = apiCredentialsDao,
                        apiModelConfigurationsDao = mockk(relaxed = true),
                        localModelsDao = mockk(relaxed = true),
                        localModelConfigurationsDao = mockk(relaxed = true),
                        defaultModelsDao = mockk(relaxed = true),
                        studioMediaDao = mockk(relaxed = true),
                        studioAlbumDao = mockk(relaxed = true),
                        memoriesDao = mockk(relaxed = true),
                        ttsProviderDao = mockk(relaxed = true),
                        mediaProviderDao = mockk(relaxed = true),
                        apiKeyManager = apiKeyManager,
                        messageVisionAnalysisDao = mockk(relaxed = true),
                        messageFileDao = mockk(relaxed = true),
                        webSearchSourceDao = mockk(relaxed = true),
                        chatSummaryDao = chatSummaryDao,
                        embeddingCoordinator = embeddingCoordinator,
                        embeddingDao = embeddingDao,
                        localEncryption = FakeLocalDataEncryptionService(),
                    )

                val result = nullUserSource.syncPendingChanges()

                assertTrue(result.isFailure)
                assertTrue(result.exceptionOrNull() is IllegalStateException)
            }

        @Test
        @DisplayName("returns failure when vault is locked")
        fun returnsFailureWhenVaultLocked() =
            testScope.runTest {
                every { cryptoManager.hasCachedKey() } returns false

                val result = dataSource.syncPendingChanges()

                assertTrue(result.isFailure)
            }

        @Test
        @DisplayName("returns success and marks synced when no pending data")
        fun returnsSuccessWithNoPendingData() =
            testScope.runTest {
                coEvery { chatDao.getClaimedForSync() } returns emptyList()
                coEvery { messageDao.getClaimedForSync() } returns emptyList()

                val result = dataSource.syncPendingChanges()

                assertTrue(result.isSuccess)
                coVerify(exactly = 0) { chatDao.markSynced(any()) }
                coVerify(exactly = 0) { messageDao.markSynced(any()) }
            }

        @Test
        @DisplayName("pushes pending chats and messages then marks them synced")
        fun pushesPendingDataAndMarksSynced() =
            testScope.runTest {
                val chatId = ChatId("chat-1")
                val messageId = MessageId("msg-1")
                val chatEntity =
                    ChatEntity(
                        chatId,
                        "PCENC:v1:aes-gcm:Test Chat",
                        java.util.Date(1000L),
                        java.util.Date(2000L),
                        false,
                        pendingSync = 1,
                    )
                val msgEntity =
                    MessageEntity(
                        messageId,
                        chatId,
                        "PCENC:v1:aes-gcm:Hello",
                        role = com.browntowndev.enclave.domain.model.chat.Role.USER,
                        pendingSync = 1,
                    )
                val msgWithFiles =
                    com.browntowndev.enclave.core.data.local
                        .MessageWithFiles(msgEntity, emptyList())

                coEvery { chatDao.getClaimedForSync() } returns listOf(chatEntity)
                coEvery { messageDao.getClaimedForSync() } returns listOf(msgWithFiles)
                every { firestore.runBatch(any<com.google.firebase.firestore.WriteBatch.Function>()) } returns
                    com.google.android.gms.tasks.Tasks
                        .forResult(null)

                val result = dataSource.syncPendingChanges()

                assertTrue(result.isSuccess)
                verify { firestore.runBatch(any<com.google.firebase.firestore.WriteBatch.Function>()) }
                coVerify { chatDao.markSynced(listOf(chatId)) }
                coVerify { messageDao.markSynced(listOf(messageId)) }
            }

        @Test
        @DisplayName("uploads locally encrypted field envelopes without re-encrypting for Firestore")
        fun uploadsLocallyEncryptedFieldEnvelopesWithoutReEncryptingForFirestore() =
            testScope.runTest {
                val source =
                    createTestFirestoreDataSource(
                        documentDao = documentDao,
                        firestore = firestore,
                        chatDao = chatDao,
                        messageDao = messageDao,
                        messageTranscriptSegmentDao = mockk(relaxed = true),
                        memoriesDao = mockk(relaxed = true),
                        webSearchSourceDao = mockk(relaxed = true),
                        ttsProviderDao = mockk(relaxed = true),
                        mediaProviderDao = mockk(relaxed = true),
                        apiModelConfigurationsDao = mockk(relaxed = true),
                        localModelsDao = mockk(relaxed = true),
                        apiCredentialsDao = apiCredentialsDao,
                        localModelConfigurationsDao = mockk(relaxed = true),
                        defaultModelsDao = mockk(relaxed = true),
                        studioMediaDao = mockk(relaxed = true),
                        studioAlbumDao = mockk(relaxed = true),
                        messageFileDao = mockk(relaxed = true),
                        messageVisionAnalysisDao = mockk(relaxed = true),
                        chatSummaryDao = chatSummaryDao,
                        embeddingDao = embeddingDao,
                        localEncryption = FakeLocalDataEncryptionService(),
                        apiKeyManager = apiKeyManager,
                        cryptoManager = cryptoManager,
                        loggingPort = loggingPort,
                        externalScope = testScope,
                        userIdProvider = { "test-user-id" },
                        embeddingCoordinator = embeddingCoordinator,
                    )
                val chatId = ChatId("chat-local")
                val chatEntity =
                    ChatEntity(
                        chatId,
                        "PCENC:v1:aes-gcm:Plain Chat",
                        java.util.Date(1000L),
                        java.util.Date(2000L),
                        false,
                        pendingSync = 1,
                    )

                coEvery { chatDao.getClaimedForSync() } returns listOf(chatEntity)
                coEvery { messageDao.getClaimedForSync() } returns emptyList()
                every { firestore.runBatch(any<com.google.firebase.firestore.WriteBatch.Function>()) } returns
                    com.google.android.gms.tasks.Tasks
                        .forResult(null)

                val result = source.syncPendingChanges()

                assertTrue(result.isSuccess)
                verify(exactly = 0) { cryptoManager.encrypt(any()) }
                coVerify { chatDao.markSynced(listOf(chatId)) }
            }

        @Test
        @DisplayName("self-heals pending plaintext Room fields before Firestore upload")
        fun selfHealsPendingPlaintextRoomFieldsBeforeFirestoreUpload() =
            testScope.runTest {
                val source =
                    createTestFirestoreDataSource(
                        documentDao = documentDao,
                        firestore = firestore,
                        chatDao = chatDao,
                        messageDao = messageDao,
                        messageTranscriptSegmentDao = mockk(relaxed = true),
                        memoriesDao = mockk(relaxed = true),
                        webSearchSourceDao = mockk(relaxed = true),
                        ttsProviderDao = mockk(relaxed = true),
                        mediaProviderDao = mockk(relaxed = true),
                        apiModelConfigurationsDao = mockk(relaxed = true),
                        localModelsDao = mockk(relaxed = true),
                        apiCredentialsDao = apiCredentialsDao,
                        localModelConfigurationsDao = mockk(relaxed = true),
                        defaultModelsDao = mockk(relaxed = true),
                        studioMediaDao = mockk(relaxed = true),
                        studioAlbumDao = mockk(relaxed = true),
                        messageFileDao = mockk(relaxed = true),
                        messageVisionAnalysisDao = mockk(relaxed = true),
                        chatSummaryDao = chatSummaryDao,
                        embeddingDao = embeddingDao,
                        localEncryption = FakeLocalDataEncryptionService(),
                        apiKeyManager = apiKeyManager,
                        cryptoManager = cryptoManager,
                        loggingPort = loggingPort,
                        externalScope = testScope,
                        userIdProvider = { "test-user-id" },
                        embeddingCoordinator = embeddingCoordinator,
                    )
                val chatId = ChatId("chat-plain")
                val chatEntity =
                    ChatEntity(
                        chatId,
                        "Plain Chat",
                        java.util.Date(1000L),
                        java.util.Date(2000L),
                        false,
                        pendingSync = 1,
                    )

                coEvery { chatDao.getClaimedForSync() } returns listOf(chatEntity)
                coEvery { messageDao.getClaimedForSync() } returns emptyList()
                every { firestore.runBatch(any<com.google.firebase.firestore.WriteBatch.Function>()) } returns
                    com.google.android.gms.tasks.Tasks
                        .forResult(null)

                val result = source.syncPendingChanges()

                assertTrue(result.isSuccess)
                coVerify { chatDao.upsert(match { it.id == chatId && it.name == "PCENC:v1:aes-gcm:Plain Chat" }) }
                verify { firestore.runBatch(any<com.google.firebase.firestore.WriteBatch.Function>()) }
                coVerify { chatDao.markSynced(listOf(chatId)) }
            }

        @Test
        @DisplayName("uses multiple 500-op batches for large pending sets")
        fun usesMultipleBatchesForLargePayloads() =
            testScope.runTest {
                val chatId = ChatId("chat-x")
                val chats =
                    (1..550).map { i ->
                        ChatEntity(
                            ChatId("chat-$i"),
                            "PCENC:v1:aes-gcm:Chat $i",
                            java.util.Date(i.toLong()),
                            java.util.Date(i.toLong()),
                            false,
                            pendingSync = 1,
                        )
                    }
                coEvery { chatDao.getClaimedForSync() } returns chats
                coEvery { messageDao.getClaimedForSync() } returns emptyList()
                every { firestore.runBatch(any<com.google.firebase.firestore.WriteBatch.Function>()) } returns
                    com.google.android.gms.tasks.Tasks
                        .forResult(null)

                val result = dataSource.syncPendingChanges()

                assertTrue(result.isSuccess)
                verify(exactly = 2) { firestore.runBatch(any<com.google.firebase.firestore.WriteBatch.Function>()) }
            }

        @Test
        @DisplayName("returns failure on Firestore batch error")
        fun returnsFailureOnBatchError() =
            testScope.runTest {
                val chatEntity =
                    ChatEntity(ChatId("c1"), "PCENC:v1:aes-gcm:Chat", java.util.Date(), java.util.Date(), false, pendingSync = 1)
                coEvery { chatDao.getClaimedForSync() } returns listOf(chatEntity)
                coEvery { messageDao.getClaimedForSync() } returns emptyList()
                every { firestore.runBatch(any<com.google.firebase.firestore.WriteBatch.Function>()) } returns
                    com.google.android.gms.tasks.Tasks
                        .forException(RuntimeException("Batch failed"))

                val result = dataSource.syncPendingChanges()

                assertTrue(result.isFailure)
                coVerify(exactly = 0) { chatDao.markSynced(any()) }
            }

        @Test
        @DisplayName("existing syncEntitiesToCloud still works for backwards compat")
        fun existingSyncEntitiesToCloudStillWorks() =
            testScope.runTest {
                val messages =
                    (1..550).map { i ->
                        com.browntowndev.enclave.domain.model.chat.Message(
                            id = MessageId("msg-$i"),
                            chatId = ChatId("chat1"),
                            content =
                                com.browntowndev.enclave.domain.model.chat
                                    .Content(text = "Hello $i"),
                            role = com.browntowndev.enclave.domain.model.chat.Role.USER,
                            createdAt = 1000L + i,
                        )
                    }
                every { firestore.runBatch(any<com.google.firebase.firestore.WriteBatch.Function>()) } returns
                    com.google.android.gms.tasks.Tasks
                        .forResult(null)

                val result = dataSource.syncEntitiesToCloud(emptyList(), messages)

                assertTrue(result.isSuccess)
                verify(exactly = 2) { firestore.runBatch(any()) }
            }

        @Test
        @DisplayName("pushes pending api_credentials then marks them synced")
        fun pushesPendingApiCredentialsAndMarksSynced() =
            testScope.runTest {
                val id =
                    com.browntowndev.enclave.domain.model.config
                        .ApiCredentialsId("cred-1")
                val encryptedKey = "encrypted-key".toByteArray()
                val entity =
                    com.browntowndev.enclave.core.data.local.ApiCredentialsEntity(
                        id = id,
                        displayName = "PCENC:v1:aes-gcm:OpenAI",
                        provider = com.browntowndev.enclave.domain.model.inference.ApiProvider.OPENAI,
                        credentialAlias = "openai_main",
                        encryptedApiKey = encryptedKey,
                        pendingSync = 1,
                    )

                coEvery { apiCredentialsDao.getClaimedForSync() } returns listOf(entity)
                coEvery { chatDao.getClaimedForSync() } returns emptyList()
                coEvery { messageDao.getClaimedForSync() } returns emptyList()

                every { firestore.runBatch(any<com.google.firebase.firestore.WriteBatch.Function>()) } returns Tasks.forResult(null)

                val result = dataSource.syncPendingChanges()

                assertTrue(result.isSuccess)
                coVerify { apiCredentialsDao.markSynced(listOf(id)) }
            }

        // Removed: pullsApiCredentialsFromFirestore — verified the obsolete per-collection
        // snapshot listener on `api_credentials`. Pull semantics are now covered by the
        // sync_events feed tests.
    }

    @Test
    @DisplayName("syncEntitiesToCloud uses multiple batches for more than 500 items")
    fun syncEntitiesToCloudUsesMultipleBatches() =
        testScope.runTest {
            val messages =
                (1..550).map { i ->
                    Message(
                        id = MessageId("msg-$i"),
                        chatId = ChatId("chat1"),
                        content =
                            com.browntowndev.enclave.domain.model.chat
                                .Content(text = "Hello $i"),
                        role = Role.USER,
                        createdAt = 1000L + i,
                    )
                }

            every { firestore.runBatch(any<com.google.firebase.firestore.WriteBatch.Function>()) } returns Tasks.forResult(null)

            val result = dataSource.syncEntitiesToCloud(emptyList(), messages)

            assertTrue(result.isSuccess)
            // Verify runBatch was called twice
            verify(exactly = 2) { firestore.runBatch(any()) }
        }

    inner class VaultSaltTests {
        private val userDocRef = mockk<DocumentReference>(relaxed = true)

        @BeforeEach
        fun setup() {
            every { firestore.document("users/test-user-id") } returns userDocRef
        }

        @Test
        @DisplayName("updateVaultSalt updates the user document with the salt")
        fun updateVaultSaltSucceeds() =
            testScope.runTest {
                every { userDocRef.set(any(), any<SetOptions>()) } returns Tasks.forResult(null)

                val result = dataSource.updateVaultSalt("test-salt")

                assertTrue(result.isSuccess)
                verify { userDocRef.set(mapOf("vaultSalt" to "test-salt"), SetOptions.merge()) }
            }

        @Test
        @DisplayName("getVaultSalt retrieves the salt from the user document")
        fun getVaultSaltSucceeds() =
            testScope.runTest {
                val snapshot = mockk<DocumentSnapshot>()
                every { snapshot.exists() } returns true
                every { snapshot.getString("vaultSalt") } returns "test-salt"
                every { userDocRef.get() } returns Tasks.forResult(snapshot)

                val result = dataSource.getVaultSalt()

                assertTrue(result.isSuccess)
                assertEquals("test-salt", result.getOrNull())
            }

        @Test
        @DisplayName("getVaultSalt returns null if document doesn't exist")
        fun getVaultSaltReturnsNullIfNoDoc() =
            testScope.runTest {
                val snapshot = mockk<DocumentSnapshot>()
                every { snapshot.exists() } returns false
                every { userDocRef.get() } returns Tasks.forResult(snapshot)

                val result = dataSource.getVaultSalt()

                assertTrue(result.isSuccess)
                assertEquals(null, result.getOrNull())
            }

        @Test
        @DisplayName("updateWrappedDek writes the wrappedDek field to the user document")
        fun updateWrappedDekWritesField() =
            testScope.runTest {
                every { userDocRef.set(any(), any<SetOptions>()) } returns Tasks.forResult(null)

                val result = dataSource.updateWrappedDek("wrapped-blob")

                assertTrue(result.isSuccess)
                verify { userDocRef.set(mapOf("wrappedDek" to "wrapped-blob"), SetOptions.merge()) }
            }

        @Test
        @DisplayName("updateWrappedDek with null deletes the wrappedDek field")
        fun updateWrappedDekWithNullDeletes() =
            testScope.runTest {
                every { userDocRef.set(any(), any<SetOptions>()) } returns Tasks.forResult(null)
                val payloadSlot = slot<Map<String, Any>>()

                val result = dataSource.updateWrappedDek(null)

                assertTrue(result.isSuccess)
                verify { userDocRef.set(capture(payloadSlot), SetOptions.merge()) }
                assertTrue(payloadSlot.captured["wrappedDek"] is com.google.firebase.firestore.FieldValue)
            }

        @Test
        @DisplayName("getWrappedDek returns the wrappedDek string when present")
        fun getWrappedDekSucceeds() =
            testScope.runTest {
                val snapshot = mockk<DocumentSnapshot>()
                every { snapshot.exists() } returns true
                every { snapshot.getString("wrappedDek") } returns "wrapped-blob"
                every { userDocRef.get() } returns Tasks.forResult(snapshot)

                val result = dataSource.getWrappedDek()

                assertTrue(result.isSuccess)
                assertEquals("wrapped-blob", result.getOrNull())
            }

        @Test
        @DisplayName("getWrappedDek returns null when field absent")
        fun getWrappedDekReturnsNullWhenAbsent() =
            testScope.runTest {
                val snapshot = mockk<DocumentSnapshot>()
                every { snapshot.exists() } returns true
                every { snapshot.getString("wrappedDek") } returns null
                every { userDocRef.get() } returns Tasks.forResult(snapshot)

                val result = dataSource.getWrappedDek()

                assertTrue(result.isSuccess)
                assertEquals(null, result.getOrNull())
            }

        @Test
        @DisplayName("uploadVaultMetadata writes salt, verification, and wrappedDek atomically")
        fun uploadVaultMetadataWritesAllFields() =
            testScope.runTest {
                every { userDocRef.set(any(), any<SetOptions>()) } returns Tasks.forResult(null)
                val payloadSlot = slot<Map<String, Any>>()

                val result =
                    dataSource.uploadVaultMetadata(
                        com.browntowndev.enclave.domain.model.security.VaultMetadata(
                            salt = "the-salt",
                            verification = "the-verification",
                            wrappedDek = "the-wrapped-dek",
                        ),
                    )

                assertTrue(result.isSuccess)
                verify { userDocRef.set(capture(payloadSlot), SetOptions.merge()) }
                assertEquals("the-salt", payloadSlot.captured["vaultSalt"])
                assertEquals("the-verification", payloadSlot.captured["vaultVerification"])
                assertEquals("the-wrapped-dek", payloadSlot.captured["wrappedDek"])
            }

        @Test
        @DisplayName("uploadVaultMetadata writes FieldValue.delete() for null verification or wrappedDek")
        fun uploadVaultMetadataDeletesNullFields() =
            testScope.runTest {
                every { userDocRef.set(any(), any<SetOptions>()) } returns Tasks.forResult(null)
                val payloadSlot = slot<Map<String, Any>>()

                val result =
                    dataSource.uploadVaultMetadata(
                        com.browntowndev.enclave.domain.model.security.VaultMetadata(
                            salt = "the-salt",
                            verification = null,
                            wrappedDek = null,
                        ),
                    )

                assertTrue(result.isSuccess)
                verify { userDocRef.set(capture(payloadSlot), SetOptions.merge()) }
                assertEquals("the-salt", payloadSlot.captured["vaultSalt"])
                assertTrue(payloadSlot.captured["vaultVerification"] is com.google.firebase.firestore.FieldValue)
                assertTrue(payloadSlot.captured["wrappedDek"] is com.google.firebase.firestore.FieldValue)
            }

        @Test
        @DisplayName("fetchVaultMetadata returns null when user doc has no salt")
        fun fetchVaultMetadataReturnsNullWhenNoSalt() =
            testScope.runTest {
                val snapshot = mockk<DocumentSnapshot>()
                every { snapshot.exists() } returns true
                every { snapshot.getString("vaultSalt") } returns null
                every { userDocRef.get() } returns Tasks.forResult(snapshot)

                val result = dataSource.fetchVaultMetadata()

                assertTrue(result.isSuccess)
                assertEquals(null, result.getOrNull())
            }

        @Test
        @DisplayName("fetchVaultMetadata returns null when document doesn't exist")
        fun fetchVaultMetadataReturnsNullWhenNoDoc() =
            testScope.runTest {
                val snapshot = mockk<DocumentSnapshot>()
                every { snapshot.exists() } returns false
                every { userDocRef.get() } returns Tasks.forResult(snapshot)

                val result = dataSource.fetchVaultMetadata()

                assertTrue(result.isSuccess)
                assertEquals(null, result.getOrNull())
            }

        @Test
        @DisplayName("fetchVaultMetadata returns full metadata when all fields present")
        fun fetchVaultMetadataReturnsAllFields() =
            testScope.runTest {
                val snapshot = mockk<DocumentSnapshot>()
                every { snapshot.exists() } returns true
                every { snapshot.getString("vaultSalt") } returns "the-salt"
                every { snapshot.getString("vaultVerification") } returns "the-ver"
                every { snapshot.getString("wrappedDek") } returns "the-wrapped"
                every { userDocRef.get() } returns Tasks.forResult(snapshot)

                val result = dataSource.fetchVaultMetadata()

                assertTrue(result.isSuccess)
                val metadata = result.getOrNull()!!
                assertEquals("the-salt", metadata.salt)
                assertEquals("the-ver", metadata.verification)
                assertEquals("the-wrapped", metadata.wrappedDek)
            }

        @Test
        @DisplayName("fetchVaultMetadata returns metadata with null wrappedDek when field absent")
        fun fetchVaultMetadataReturnsNullWrappedDekWhenAbsent() =
            testScope.runTest {
                val snapshot = mockk<DocumentSnapshot>()
                every { snapshot.exists() } returns true
                every { snapshot.getString("vaultSalt") } returns "the-salt"
                every { snapshot.getString("vaultVerification") } returns "the-ver"
                every { snapshot.getString("wrappedDek") } returns null
                every { userDocRef.get() } returns Tasks.forResult(snapshot)

                val result = dataSource.fetchVaultMetadata()

                assertTrue(result.isSuccess)
                val metadata = result.getOrNull()!!
                assertEquals(null, metadata.wrappedDek)
                assertEquals("the-ver", metadata.verification)
            }
    }

    @Nested
    @DisplayName("sync event feed")
    inner class SyncEventFeedTests {
        @Test
        fun `SCN-ANDROID-001 fresh hydrate applies events after sequence zero`() =
            runTest {
                val localEncryption = com.browntowndev.enclave.core.data.repository.FakeLocalDataEncryptionService()
                val chatDao = mockk<com.browntowndev.enclave.core.data.local.ChatDao>(relaxed = true)
                val captured =
                    com.browntowndev.enclave.core.data.remote.firestore.captureChatUpserts(chatDao)
                val cryptoManager = mockk<CryptoManagerPort>(relaxed = true)
                every { cryptoManager.hasCachedKey() } returns true
                val stateStore = FakeFirestoreSyncStateStore(sequence = 0)
                val hydration =
                    com.browntowndev.enclave.core.data.remote.firestore.buildChatHydrationApplier(
                        chatDao = chatDao,
                        cryptoManager = cryptoManager,
                        localEncryption = localEncryption,
                        stateStore = stateStore,
                    )
                val integration =
                    com.browntowndev.enclave.core.data.remote.firestore.IntegrationApplyingDispatcher(
                        applier = hydration.applier,
                        sourceDocuments = hydration.sourceDocuments,
                    )
                val harness =
                    EventFeedHarness(
                        userId = "alice",
                        vaultUnlocked = true,
                        lastAppliedSequence = 0,
                        eventDispatcher = integration,
                        syncStateStore = stateStore,
                    )
                hydration.sourceDocuments.put(
                    "users/alice/chats/chat-1",
                    com.browntowndev.enclave.core.data.remote.firestore.encryptedChatSourcePayload(
                        plaintextName = "Roadmap",
                        localEncryption = localEncryption,
                    ),
                )
                harness.feedGateway.pages.add(
                    listOf(syncEvent(sequence = 1, collectionPath = "chats", entityId = "chat-1")),
                )

                val result = harness.dataSource.syncRemoteChangesFromCloud()

                assertTrue(result.isSuccess)
                val replay = result.getOrThrow()
                assertEquals(1, replay.eventsApplied)
                assertEquals(1, replay.lastAppliedSequence)
                org.junit.jupiter.api.Assertions.assertFalse(replay.hasMore)
                assertEquals(1, captured.size)
                assertEquals("chat-1", captured.first().id.value)
                val decryptedName =
                    localEncryption.decryptString(
                        captured.first().name,
                        byteArrayOf(),
                    )
                assertEquals("Roadmap", decryptedName)
                assertEquals(1, stateStore.sequence)
            }

        @Test
        fun `SCN-LIVE-001 listener attaches to sync events only`() =
            runTest {
                val harness = EventFeedHarness(userId = "alice", vaultUnlocked = true, lastAppliedSequence = 7)

                harness.dataSource.startListening()
                advanceUntilIdle()

                assertEquals(1, harness.feedGateway.listenCallCount)
                val listenerQuery = requireNotNull(harness.feedGateway.lastListenerQuery)
                assertEquals("users/alice/sync_events", listenerQuery.collectionPath)
                assertEquals("sequence > 7", listenerQuery.filter)
                assertEquals("sequence", listenerQuery.orderBy)
                assertEquals(Long.MAX_VALUE, listenerQuery.limit)
                verify(exactly = 0) { firestore.collection("users/alice/chats") }
                verify(exactly = 0) { firestore.collection("users/alice/messages") }
            }

        @Test
        fun `SCN-LIVE-002 live event applies and advances sequence once`() =
            runTest {
                val localEncryption =
                    com.browntowndev.enclave.core.data.repository.FakeLocalDataEncryptionService()
                val chatDao = mockk<com.browntowndev.enclave.core.data.local.ChatDao>(relaxed = true)
                val captured =
                    com.browntowndev.enclave.core.data.remote.firestore.captureChatUpserts(chatDao)
                val cryptoManager = mockk<CryptoManagerPort>(relaxed = true)
                every { cryptoManager.hasCachedKey() } returns true
                val stateStore = FakeFirestoreSyncStateStore(sequence = 20)
                val hydration =
                    com.browntowndev.enclave.core.data.remote.firestore.buildChatHydrationApplier(
                        chatDao = chatDao,
                        cryptoManager = cryptoManager,
                        localEncryption = localEncryption,
                        stateStore = stateStore,
                    )
                val integration =
                    com.browntowndev.enclave.core.data.remote.firestore.IntegrationApplyingDispatcher(
                        applier = hydration.applier,
                        sourceDocuments = hydration.sourceDocuments,
                    )
                val harness =
                    EventFeedHarness(
                        userId = "alice",
                        vaultUnlocked = true,
                        lastAppliedSequence = 20,
                        eventDispatcher = integration,
                        syncStateStore = stateStore,
                    )
                hydration.sourceDocuments.put(
                    "users/alice/chats/chat-21",
                    com.browntowndev.enclave.core.data.remote.firestore.encryptedChatSourcePayload(
                        plaintextName = "Live Chat",
                        localEncryption = localEncryption,
                    ),
                )

                harness.dataSource.startListening()
                advanceUntilIdle()
                harness.feedGateway.emit(
                    listOf(syncEvent(sequence = 21, collectionPath = "chats", entityId = "chat-21")),
                )
                advanceUntilIdle()

                assertEquals(1, captured.size)
                assertEquals("chat-21", captured.first().id.value)
                assertEquals(21, stateStore.sequence)
            }

        @Test
        fun `SCN-LIVE-003 live event callbacks are serialized before applying next snapshot`() =
            runTest {
                val dispatcher = SerializingProbeDispatcher()
                val harness =
                    EventFeedHarness(
                        userId = "alice",
                        vaultUnlocked = true,
                        lastAppliedSequence = 20,
                        eventDispatcher = dispatcher,
                    )

                harness.dataSource.startListening()
                advanceUntilIdle()
                val first =
                    launch {
                        harness.feedGateway.emit(
                            listOf(syncEvent(sequence = 21, collectionPath = "chats", entityId = "chat-21")),
                        )
                    }
                dispatcher.firstStarted.await()
                val second =
                    launch {
                        harness.feedGateway.emit(
                            listOf(syncEvent(sequence = 22, collectionPath = "chats", entityId = "chat-22")),
                        )
                    }
                advanceUntilIdle()

                org.junit.jupiter.api.Assertions.assertFalse(dispatcher.secondStartedBeforeFirstReleased)
                dispatcher.releaseFirst.complete(Unit)
                first.join()
                second.join()
                assertEquals(listOf(21L, 22L), dispatcher.appliedSequences)
            }

        @Test
        fun `SCN-LIVE-004 live snapshot applies out of order events in sequence order`() =
            runTest {
                val dispatcher = ContiguousProbeDispatcher(lastAppliedSequence = 761)
                val harness =
                    EventFeedHarness(
                        userId = "alice",
                        vaultUnlocked = true,
                        lastAppliedSequence = 761,
                        eventDispatcher = dispatcher,
                    )

                harness.dataSource.startListening()
                advanceUntilIdle()
                harness.feedGateway.emit(
                    listOf(
                        syncEvent(sequence = 763, collectionPath = "chats", entityId = "chat-763"),
                        syncEvent(sequence = 762, collectionPath = "chats", entityId = "chat-762"),
                    ),
                )
                advanceUntilIdle()

                assertEquals(listOf(762L, 763L), dispatcher.appliedSequences)
            }

        @Test
        fun `SCN-LIVE-005 live listener buffers future event until missing sequence arrives`() =
            runTest {
                val dispatcher = ContiguousProbeDispatcher(lastAppliedSequence = 761)
                val harness =
                    EventFeedHarness(
                        userId = "alice",
                        vaultUnlocked = true,
                        lastAppliedSequence = 761,
                        eventDispatcher = dispatcher,
                    )

                harness.dataSource.startListening()
                advanceUntilIdle()
                harness.feedGateway.emit(
                    listOf(syncEvent(sequence = 763, collectionPath = "chats", entityId = "chat-763")),
                )
                advanceUntilIdle()

                assertEquals(emptyList<Long>(), dispatcher.appliedSequences)

                harness.feedGateway.emit(
                    listOf(syncEvent(sequence = 762, collectionPath = "chats", entityId = "chat-762")),
                )
                advanceUntilIdle()

                assertEquals(listOf(762L, 763L), dispatcher.appliedSequences)
            }

        @Test
        fun `SCN-DATASOURCE-001 replay fails when signed out`() =
            runTest {
                val dataSource = eventFeedDataSource(userId = null, vaultUnlocked = true)

                val result = dataSource.syncRemoteChangesFromCloud()

                val exception = result.exceptionOrNull()
                assertTrue(exception is IllegalStateException)
                assertEquals("Not signed in", exception?.message)
            }

        @Test
        fun `SCN-DATASOURCE-002 replay fails when vault is locked`() =
            runTest {
                val dataSource = eventFeedDataSource(userId = "alice", vaultUnlocked = false)

                val result = dataSource.syncRemoteChangesFromCloud()

                val exception = result.exceptionOrNull()
                assertTrue(exception is IllegalStateException)
                assertEquals("Vault locked", exception?.message)
            }

        @Test
        fun `SCN-DATASOURCE-003 replay loops until hasMore is false`() =
            runTest {
                val harness = EventFeedHarness(userId = "alice", vaultUnlocked = true, lastAppliedSequence = 0)
                harness.feedGateway.pages.add(
                    (1L..200L).map { sequence -> syncEvent(sequence, "chats", "chat-$sequence") },
                )
                harness.feedGateway.pages.add(
                    (201L..202L).map { sequence -> syncEvent(sequence, "chats", "chat-$sequence") },
                )

                val first = harness.dataSource.syncRemoteChangesFromCloud().getOrThrow()
                assertTrue(first.hasMore)
                assertEquals(200, first.eventsApplied)
                assertEquals(200, first.lastAppliedSequence)
                assertEquals("sequence > 0", harness.feedGateway.fetchQueries[0].filter)

                val second = harness.dataSource.syncRemoteChangesFromCloud().getOrThrow()
                assertEquals("sequence > 200", harness.feedGateway.fetchQueries[1].filter)
                assertEquals(202, first.eventsApplied + second.eventsApplied)
                assertEquals(202, second.lastAppliedSequence)
                org.junit.jupiter.api.Assertions.assertFalse(second.hasMore)
            }

        @Test
        fun `SCN-HYDRATION-001 replay resets stale cursor when Room is empty`() =
            runTest {
                val chatDao = mockk<com.browntowndev.enclave.core.data.local.ChatDao>(relaxed = true)
                coEvery { chatDao.countActiveChats() } returns 0
                val harness =
                    EventFeedHarness(
                        userId = "alice",
                        vaultUnlocked = true,
                        lastAppliedSequence = 500,
                        chatDao = chatDao,
                    )
                harness.feedGateway.pages.add(
                    listOf(syncEvent(1L, "chats", "chat-1")),
                )

                val replay = harness.dataSource.syncRemoteChangesFromCloud().getOrThrow()

                assertEquals("sequence > 0", harness.feedGateway.fetchQueries.single().filter)
                assertEquals(1, replay.eventsApplied)
                assertEquals(1L, replay.lastAppliedSequence)
            }

        @Test
        fun `SCN-PURGE-001 purge detaches event listener`() =
            runTest {
                val harness = EventFeedHarness(userId = "alice", vaultUnlocked = true, lastAppliedSequence = 0)
                harness.stubSuccessfulUserDelete()

                harness.dataSource.startListening()
                advanceUntilIdle()
                val result = harness.dataSource.deleteUserAccount()

                assertTrue(result.isSuccess)
                assertEquals(1, harness.feedGateway.removeCallCount)
                assertEquals(0, harness.feedGateway.activeListenerCount)
            }

        private fun eventFeedDataSource(
            userId: String?,
            vaultUnlocked: Boolean,
        ): FirestoreDataSource {
            val cm = mockk<CryptoManagerPort>(relaxed = true)
            every { cm.hasCachedKey() } returns vaultUnlocked
            return createTestFirestoreDataSource(
                firestore = mockk(relaxed = true),
                cryptoManager = cm,
                userIdProvider = { userId },
                externalScope = testScope,
            )
        }

        private fun syncEvent(
            sequence: Long,
            collectionPath: String,
            entityId: String,
        ): com.browntowndev.enclave.core.data.remote.firestore.FirestoreSyncEvent =
            com.browntowndev.enclave.core.data.remote.firestore.FirestoreSyncEvent(
                sequence = sequence,
                schemaVersion = 1,
                operation = "upsert",
                collectionPath = collectionPath,
                entityType = collectionPath,
                entityId = entityId,
                entityPath = "users/alice/$collectionPath/$entityId",
                sourceExists = true,
                sourceUpdatedAt = 1700000001000,
                sourceDeleted = false,
                deviceId = null,
                clientMutationId = null,
                triggerEventId = "event-$sequence",
                emittedAtMillis = 1700000001001,
            )

        private inner class EventFeedHarness(
            userId: String,
            vaultUnlocked: Boolean,
            lastAppliedSequence: Long,
            chatDao: com.browntowndev.enclave.core.data.local.ChatDao =
                mockk<com.browntowndev.enclave.core.data.local.ChatDao>(relaxed = true).also {
                    coEvery { it.countActiveChats() } returns 1
                },
            eventDispatcher: com.browntowndev.enclave.core.data.remote.firestore.FirestoreSyncEventDispatcher =
                RecordOnlyDispatcher(),
            syncStateStore: com.browntowndev.enclave.core.data.remote.firestore.FirestoreSyncStateStore =
                FakeFirestoreSyncStateStore(sequence = lastAppliedSequence),
        ) {
            private val applierDelegate = eventDispatcher
            val feedGateway = RecordingFeedGateway()
            val recordOnlyDispatcher: RecordOnlyDispatcher
                get() = applierDelegate as RecordOnlyDispatcher
            val stateStore = syncStateStore
            val dataSource: FirestoreDataSource

            init {
                val cm = mockk<CryptoManagerPort>(relaxed = true)
                every { cm.hasCachedKey() } returns vaultUnlocked
                dataSource =
                    createTestFirestoreDataSource(
                        firestore = firestore,
                        chatDao = chatDao,
                        cryptoManager = cm,
                        userIdProvider = { userId },
                        externalScope = testScope,
                        syncEventFeedClient =
                            com.browntowndev.enclave.core.data.remote.firestore.FirestoreSyncEventFeedClient(
                                gateway = feedGateway,
                            ),
                        syncEventApplier = eventDispatcher,
                        syncStateStore = stateStore,
                    )
            }

            fun stubSuccessfulUserDelete() {
                val userDoc = mockk<DocumentReference>(relaxed = true)
                val collection = mockk<CollectionReference>(relaxed = true)
                val emptySnapshot = mockk<QuerySnapshot>(relaxed = true)
                every { emptySnapshot.documents } returns emptyList()
                every { firestore.document("users/alice") } returns userDoc
                every { userDoc.delete() } returns Tasks.forResult(null)
                every { firestore.collection(match { it.startsWith("users/alice/") }) } returns collection
                every { collection.limit(any()) } returns collection
                every { collection.get() } returns Tasks.forResult(emptySnapshot)
            }

        }

        private inner class RecordingFeedGateway :
            com.browntowndev.enclave.core.data.remote.firestore.FirestoreSyncEventQueryGateway {
            val pages = ArrayDeque<List<com.browntowndev.enclave.core.data.remote.firestore.FirestoreSyncEvent>>()
            val fetchQueries =
                mutableListOf<com.browntowndev.enclave.core.data.remote.firestore.FirestoreSyncEventFeedClient.QueryShape>()
            var lastListenerQuery:
                com.browntowndev.enclave.core.data.remote.firestore.FirestoreSyncEventFeedClient.QueryShape? = null
            var listenCallCount = 0
            var removeCallCount = 0
            var activeListenerCount = 0
            private var callback:
                (suspend (List<com.browntowndev.enclave.core.data.remote.firestore.FirestoreSyncEvent>) -> Unit)? = null

            override suspend fun fetchEvents(
                queryShape: com.browntowndev.enclave.core.data.remote.firestore.FirestoreSyncEventFeedClient.QueryShape,
            ): List<com.browntowndev.enclave.core.data.remote.firestore.FirestoreSyncEvent> =
                pages.removeFirstOrNull().orEmpty().also { fetchQueries += queryShape }

            override fun listen(
                queryShape: com.browntowndev.enclave.core.data.remote.firestore.FirestoreSyncEventFeedClient.QueryShape,
                onEvents: suspend (List<com.browntowndev.enclave.core.data.remote.firestore.FirestoreSyncEvent>) -> Unit,
            ): com.browntowndev.enclave.core.data.remote.firestore.FirestoreSyncEventListenerRegistration {
                lastListenerQuery = queryShape
                callback = onEvents
                listenCallCount += 1
                activeListenerCount += 1
                return object :
                    com.browntowndev.enclave.core.data.remote.firestore.FirestoreSyncEventListenerRegistration {
                    override fun remove() {
                        removeCallCount += 1
                        activeListenerCount -= 1
                    }
                }
            }

            suspend fun emit(events: List<com.browntowndev.enclave.core.data.remote.firestore.FirestoreSyncEvent>) {
                callback?.invoke(events)
            }
        }

        /**
         * Record-only dispatcher double — captures forwarded events and returns a synthetic
         * applied result. Performs no transactional logic so the SUT cannot piggyback on the
         * fake to satisfy production behavior.
         */
        private inner class RecordOnlyDispatcher :
            com.browntowndev.enclave.core.data.remote.firestore.FirestoreSyncEventDispatcher {
            val forwarded =
                mutableListOf<Pair<String, com.browntowndev.enclave.core.data.remote.firestore.FirestoreSyncEvent>>()
            private val applyCounts = mutableMapOf<Long, Int>()

            override suspend fun apply(
                userId: String,
                event: com.browntowndev.enclave.core.data.remote.firestore.FirestoreSyncEvent,
            ): com.browntowndev.enclave.core.data.remote.firestore.FirestoreSyncEventApplier.ApplyResult {
                forwarded += userId to event
                applyCounts[event.sequence] = applyCounts.getOrDefault(event.sequence, 0) + 1
                return com.browntowndev.enclave.core.data.remote.firestore.FirestoreSyncEventApplier.ApplyResult(
                    appliedCount = 1,
                    lastAppliedSequence = event.sequence,
                )
            }

            fun applyCountFor(sequence: Long): Int = applyCounts.getOrDefault(sequence, 0)
        }

        private inner class SerializingProbeDispatcher :
            com.browntowndev.enclave.core.data.remote.firestore.FirestoreSyncEventDispatcher {
            val firstStarted = CompletableDeferred<Unit>()
            val releaseFirst = CompletableDeferred<Unit>()
            val appliedSequences = mutableListOf<Long>()
            var secondStartedBeforeFirstReleased = false

            override suspend fun apply(
                userId: String,
                event: com.browntowndev.enclave.core.data.remote.firestore.FirestoreSyncEvent,
            ): com.browntowndev.enclave.core.data.remote.firestore.FirestoreSyncEventApplier.ApplyResult {
                if (event.sequence == 21L) {
                    firstStarted.complete(Unit)
                    releaseFirst.await()
                } else if (!releaseFirst.isCompleted) {
                    secondStartedBeforeFirstReleased = true
                }
                appliedSequences += event.sequence
                return com.browntowndev.enclave.core.data.remote.firestore.FirestoreSyncEventApplier.ApplyResult(
                    appliedCount = 1,
                    lastAppliedSequence = event.sequence,
                )
            }
        }

        private inner class ContiguousProbeDispatcher(
            private var lastAppliedSequence: Long,
        ) : com.browntowndev.enclave.core.data.remote.firestore.FirestoreSyncEventDispatcher {
            val appliedSequences = mutableListOf<Long>()

            override suspend fun apply(
                userId: String,
                event: com.browntowndev.enclave.core.data.remote.firestore.FirestoreSyncEvent,
            ): com.browntowndev.enclave.core.data.remote.firestore.FirestoreSyncEventApplier.ApplyResult {
                val expectedSequence = lastAppliedSequence + 1
                check(event.sequence == expectedSequence) {
                    "sync event sequence gap: expected $expectedSequence but received ${event.sequence}"
                }
                lastAppliedSequence = event.sequence
                appliedSequences += event.sequence
                return com.browntowndev.enclave.core.data.remote.firestore.FirestoreSyncEventApplier.ApplyResult(
                    appliedCount = 1,
                    lastAppliedSequence = event.sequence,
                )
            }
        }

        private inner class FakeFirestoreSyncStateStore(
            var sequence: Long,
        ) : com.browntowndev.enclave.core.data.remote.firestore.FirestoreSyncStateStore() {
            override suspend fun lastAppliedSequence(userId: String): Long = sequence

            override suspend fun setLastAppliedSequence(
                userId: String,
                sequence: Long,
            ) {
                this.sequence = sequence
            }

            override suspend fun deviceId(): String = "device-a"

            override fun newClientMutationId(): String = "mutation-upload-1"

            override fun currentTimeMillis(): Long = 1700000003000
        }
    }

    @Nested
    @DisplayName("pending sync metadata")
    inner class PendingSyncMetadataTests {
        @Test
        fun `SCN-PENDING-001 outbound writes include sync origin metadata`() =
            runTest {
                val capture = PendingChatUploadCapture()
                val dataSource = pendingChatDataSource(capture)

                val result = dataSource.syncPendingChanges()
                capture.applyCapturedBatch()

                assertTrue(result.isSuccess)
                assertEquals("device-a", capture.writeMap["_sync_device_id"])
                assertEquals("mutation-upload-1", capture.writeMap["_sync_client_mutation_id"])
                assertEquals(1700000003000L, capture.writeMap["_sync_written_at"])
            }

        @Test
        fun `SCN-PENDING-002 adapter encrypted payload fields are preserved`() =
            runTest {
                val capture = PendingChatUploadCapture()
                val dataSource = pendingChatDataSource(capture)

                val result = dataSource.syncPendingChanges()
                capture.applyCapturedBatch()

                assertTrue(result.isSuccess)
                assertEquals("cipher-name", capture.writeMap["name"])
                assertEquals("device-a", capture.writeMap["_sync_device_id"])
                org.junit.jupiter.api.Assertions.assertFalse(capture.writeMap.values.contains("Roadmap"))
            }

        private fun pendingChatDataSource(capture: PendingChatUploadCapture): FirestoreDataSource {
            val chatDaoLocal = mockk<ChatDao>(relaxed = true)
            val firestoreLocal = capture.firestore
            val cm = mockk<CryptoManagerPort>(relaxed = true)
            every { cm.hasCachedKey() } returns true
            coEvery { chatDaoLocal.claimPending() } returns Unit
            coEvery { chatDaoLocal.getClaimedForSync() } returns
                listOf(
                    ChatEntity(
                        id = ChatId("chat-1"),
                        name = "cipher-name",
                        created = Date(1000L),
                        lastModified = Date(2000L),
                        pinned = false,
                        pendingSync = 2,
                        updatedAt = 1700000001000,
                        deleted = false,
                    ),
                )
            coEvery { chatDaoLocal.markSynced(listOf(ChatId("chat-1"))) } returns Unit
            capture.stub()
            return createTestFirestoreDataSource(
                firestore = firestoreLocal,
                chatDao = chatDaoLocal,
                cryptoManager = cm,
                userIdProvider = { "alice" },
                externalScope = testScope,
            )
        }

        private inner class PendingChatUploadCapture {
            val firestore: FirebaseFirestore = mockk(relaxed = true)
            var writeMap: Map<String, Any?> = emptyMap()
            private val batch = mockk<com.google.firebase.firestore.WriteBatch>(relaxed = true)
            private var capturedBatchFn: com.google.firebase.firestore.WriteBatch.Function? = null

            fun stub() {
                val docRef = mockk<DocumentReference>(relaxed = true)
                every { firestore.document("users/alice/chats/chat-1") } returns docRef
                every { firestore.runBatch(any()) } answers {
                    capturedBatchFn = firstArg<com.google.firebase.firestore.WriteBatch.Function>()
                    Tasks.forResult(null)
                }
                every { batch.set(docRef, any<Map<String, Any?>>(), any<SetOptions>()) } answers {
                    @Suppress("UNCHECKED_CAST")
                    writeMap = secondArg<Any>() as Map<String, Any?>
                    batch
                }
            }

            fun applyCapturedBatch() {
                capturedBatchFn?.apply(batch)
            }
        }
    }

    private fun mockSnapshot(
        docs: List<QueryDocumentSnapshot>,
        changeType: DocumentChange.Type,
    ): QuerySnapshot {
        val snapshot = mockk<QuerySnapshot>()
        val changes =
            docs.map { doc ->
                val change = mockk<DocumentChange>()
                every { change.type } returns changeType
                every { change.document } returns doc
                change
            }
        every { snapshot.documentChanges } returns changes
        every { snapshot.size() } returns docs.size
        return snapshot
    }

    private class FakeLocalDataEncryptionService : LocalDataEncryptionService {
        override fun encryptString(plaintext: String): String = "PCENC:v1:aes-gcm:$plaintext"

        override fun decryptString(stored: String): String = stored.removePrefix("PCENC:v1:aes-gcm:")

        override fun isEncrypted(stored: String): Boolean = stored.startsWith("PCENC:v1:aes-gcm:")

        override fun encryptString(
            plaintext: String,
            aad: ByteArray,
        ): String = "PCENC:v1:aes-gcm:$plaintext"

        override fun decryptString(
            stored: String,
            aad: ByteArray,
        ): String = stored.removePrefix("PCENC:v1:aes-gcm:")
    }
}
