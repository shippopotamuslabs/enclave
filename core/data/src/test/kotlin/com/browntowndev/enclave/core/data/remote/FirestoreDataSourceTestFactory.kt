package com.browntowndev.enclave.core.data.remote

import com.browntowndev.enclave.core.data.local.ApiCredentialsDao
import com.browntowndev.enclave.core.data.local.ApiModelConfigurationsDao
import com.browntowndev.enclave.core.data.local.ChatDao
import com.browntowndev.enclave.core.data.local.ChatSummaryDao
import com.browntowndev.enclave.core.data.local.DefaultModelsDao
import com.browntowndev.enclave.core.data.local.DocumentDao
import com.browntowndev.enclave.core.data.local.EmbeddingDao
import com.browntowndev.enclave.core.data.local.FirestoreSyncPlaceholderDao
import com.browntowndev.enclave.core.data.local.LocalModelConfigurationsDao
import com.browntowndev.enclave.core.data.local.LocalModelsDao
import com.browntowndev.enclave.core.data.local.MediaProviderDao
import com.browntowndev.enclave.core.data.local.MemoriesDao
import com.browntowndev.enclave.core.data.local.MessageDao
import com.browntowndev.enclave.core.data.local.MessageFileDao
import com.browntowndev.enclave.core.data.local.MessageTranscriptSegmentDao
import com.browntowndev.enclave.core.data.local.MessageVisionAnalysisDao
import com.browntowndev.enclave.core.data.local.StudioAlbumDao
import com.browntowndev.enclave.core.data.local.StudioMediaDao
import com.browntowndev.enclave.core.data.local.TtsProviderDao
import com.browntowndev.enclave.core.data.local.UtilityModelsDao
import com.browntowndev.enclave.core.data.local.WebSearchSourceDao
import com.browntowndev.enclave.core.data.remote.firestore.ChatSyncDaos
import com.browntowndev.enclave.core.data.remote.firestore.ConfigSyncDaos
import com.browntowndev.enclave.core.data.remote.firestore.FirestoreSyncDaos
import com.browntowndev.enclave.core.data.remote.firestore.FirestoreSyncDocumentCache
import com.browntowndev.enclave.core.data.remote.firestore.FirestoreSyncEventApplier
import com.browntowndev.enclave.core.data.remote.firestore.FirestoreSyncEventDispatcher
import com.browntowndev.enclave.core.data.remote.firestore.FirestoreSyncEventFeedClient
import com.browntowndev.enclave.core.data.remote.firestore.FirestoreSyncStateStore
import com.browntowndev.enclave.core.data.remote.firestore.StudioSyncDaos
import com.browntowndev.enclave.core.data.repository.SettingsRepositoryImpl
import com.browntowndev.enclave.core.data.security.ApiKeyManager
import com.browntowndev.enclave.core.data.security.LocalDataEncryptionService
import com.browntowndev.enclave.core.data.security.SensitivePersistenceSelfHealer
import com.browntowndev.enclave.core.data.sync.EmbeddingRegenerationCoordinator
import com.browntowndev.enclave.core.data.sync.SearchToolSettingsSyncQueue
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import com.browntowndev.enclave.domain.port.security.CryptoManagerPort
import com.google.firebase.firestore.FirebaseFirestore
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import java.util.concurrent.atomic.AtomicBoolean

@Suppress("LongParameterList")
internal fun createTestFirestoreDataSource(
    firestore: FirebaseFirestore,
    chatDao: ChatDao = mockk(relaxed = true),
    messageDao: MessageDao = mockk(relaxed = true),
    messageTranscriptSegmentDao: MessageTranscriptSegmentDao = mockk(relaxed = true),
    memoriesDao: MemoriesDao = mockk(relaxed = true),
    webSearchSourceDao: WebSearchSourceDao = mockk(relaxed = true),
    ttsProviderDao: TtsProviderDao = mockk(relaxed = true),
    mediaProviderDao: MediaProviderDao = mockk(relaxed = true),
    apiModelConfigurationsDao: ApiModelConfigurationsDao = mockk(relaxed = true),
    localModelsDao: LocalModelsDao = mockk(relaxed = true),
    apiCredentialsDao: ApiCredentialsDao = mockk(relaxed = true),
    localModelConfigurationsDao: LocalModelConfigurationsDao = mockk(relaxed = true),
    defaultModelsDao: DefaultModelsDao = mockk(relaxed = true),
    studioMediaDao: StudioMediaDao = mockk(relaxed = true),
    studioAlbumDao: StudioAlbumDao = mockk(relaxed = true),
    messageFileDao: MessageFileDao = mockk(relaxed = true),
    messageVisionAnalysisDao: MessageVisionAnalysisDao = mockk(relaxed = true),
    chatSummaryDao: ChatSummaryDao = mockk(relaxed = true),
    documentDao: DocumentDao = mockk(relaxed = true),
    embeddingDao: EmbeddingDao = mockk(relaxed = true),
    firestoreSyncPlaceholderDao: FirestoreSyncPlaceholderDao = mockk(relaxed = true),
    apiKeyManager: ApiKeyManager = mockk(relaxed = true),
    cryptoManager: CryptoManagerPort = mockk(relaxed = true),
    loggingPort: LoggingPort = mockk(relaxed = true),
    externalScope: CoroutineScope,
    userIdProvider: () -> String? = { "test-user-id" },
    embeddingCoordinator: EmbeddingRegenerationCoordinator = mockk(relaxed = true),
    localEncryption: LocalDataEncryptionService = LenientFakeLocalDataEncryptionService(),
    sensitiveSelfHealer: SensitivePersistenceSelfHealer? = null,
    purgeInProgress: AtomicBoolean = AtomicBoolean(false),
    syncEventFeedClient: FirestoreSyncEventFeedClient = FirestoreSyncEventFeedClient(),
    syncEventApplier: FirestoreSyncEventDispatcher = FirestoreSyncEventApplier(),
    syncStateStore: FirestoreSyncStateStore = DeterministicTestSyncStateStore(),
    // Default to a relaxed no-op mock so existing tests don't trigger live Firestore calls during
    // cache prefetch in applyLiveEvents, which would cause UncompletedCoroutinesError.
    documentCache: FirestoreSyncDocumentCache = mockk(relaxed = true),
    searchToolSettingsSyncQueue: SearchToolSettingsSyncQueue =
        SearchToolSettingsSyncQueue(
            dataStore =
                androidx.datastore.preferences.core.PreferenceDataStoreFactory.create(
                    scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO + kotlinx.coroutines.SupervisorJob()),
                ) { java.io.File.createTempFile("search-settings-", ".preferences_pb") },
        ),
    settingsRepository: SettingsRepositoryImpl = mockk(relaxed = true),
): FirestoreDataSource {
    val healer = sensitiveSelfHealer ?: SensitivePersistenceSelfHealer(localEncryption, loggingPort)
    val facade =
        FirestoreSyncDependenciesFacade.forTests(
            firestore = firestore,
            chatSyncDaos =
                ChatSyncDaos(
                    chatDao = chatDao,
                    messageDao = messageDao,
                    chatSummaryDao = chatSummaryDao,
                    messageFileDao = messageFileDao,
                    messageVisionAnalysisDao = messageVisionAnalysisDao,
                    messageTranscriptSegmentDao = messageTranscriptSegmentDao,
                    messageInferenceTelemetryDao = mockk(relaxed = true),
                    webSearchSourceDao = webSearchSourceDao,
                    documentDao = documentDao,
                    embeddingDao = embeddingDao,
                    firestoreSyncPlaceholderDao = firestoreSyncPlaceholderDao,
                ),
            configSyncDaos =
                ConfigSyncDaos(
                    memoriesDao = memoriesDao,
                    ttsProviderDao = ttsProviderDao,
                    mediaProviderDao = mediaProviderDao,
                    apiModelConfigurationsDao = apiModelConfigurationsDao,
                    localModelsDao = localModelsDao,
                    apiCredentialsDao = apiCredentialsDao,
                    localModelConfigurationsDao = localModelConfigurationsDao,
                    defaultModelsDao = defaultModelsDao,
                    utilityModelsDao = mockk(relaxed = true),
                ),
            studioSyncDaos =
                StudioSyncDaos(
                    studioMediaDao = studioMediaDao,
                    studioAlbumDao = studioAlbumDao,
                ),
            apiKeyManager = apiKeyManager,
            cryptoManager = cryptoManager,
            loggingPort = loggingPort,
            userIdProvider = userIdProvider,
            embeddingCoordinator = embeddingCoordinator,
            localEncryption = localEncryption,
            sensitiveSelfHealer = healer,
            purgeInProgress = purgeInProgress,
            syncEventFeedClient = syncEventFeedClient,
            syncEventApplier = syncEventApplier,
            syncStateStore = syncStateStore,
            documentCache = documentCache,
            searchToolSettingsSyncQueue = searchToolSettingsSyncQueue,
            settingsRepository = settingsRepository,
        )
    return FirestoreDataSource(facade, externalScope)
}

/**
 * Lenient encryption service used as the default for [createTestFirestoreDataSource]. Any
 * value that already looks like ciphertext (starts with the production envelope prefix or the
 * "cipher-" fixture prefix used in SCN-PENDING-002) is treated as already-encrypted so the
 * pending-sync engine preserves it verbatim. New plaintext values still go through the
 * production envelope so encryption-required call sites see a sensible blob.
 */
internal class LenientFakeLocalDataEncryptionService : LocalDataEncryptionService {
    override fun encryptString(plaintext: String): String = if (isEncrypted(plaintext)) plaintext else "PCENC:v1:aes-gcm:$plaintext"

    override fun decryptString(stored: String): String = stored.removePrefix("PCENC:v1:aes-gcm:")

    override fun isEncrypted(stored: String): Boolean = stored.startsWith("PCENC:v1:aes-gcm:") || stored.startsWith("cipher-")

    override fun encryptString(
        plaintext: String,
        aad: ByteArray,
    ): String = encryptString(plaintext)

    override fun decryptString(
        stored: String,
        aad: ByteArray,
    ): String = decryptString(stored)
}

/**
 * Default sync-state store for tests. Provides deterministic device/mutation/clock values so
 * pending-sync metadata assertions (SCN-PENDING-001/002) are reproducible without requiring
 * every test to thread a custom store.
 */
internal class DeterministicTestSyncStateStore : FirestoreSyncStateStore() {
    private val lastApplied = mutableMapOf<String, Long>()

    override suspend fun lastAppliedSequence(userId: String): Long = lastApplied[userId] ?: 0L

    override suspend fun setLastAppliedSequence(
        userId: String,
        sequence: Long,
    ) {
        lastApplied[userId] = sequence
    }

    override suspend fun deviceId(): String = "device-a"

    override fun newClientMutationId(): String = "mutation-upload-1"

    override fun currentTimeMillis(): Long = 1_700_000_003_000L
}

internal fun testFirestoreSyncDaos(
    chatDao: ChatDao = mockk(relaxed = true),
    messageDao: MessageDao = mockk(relaxed = true),
    messageTranscriptSegmentDao: MessageTranscriptSegmentDao = mockk(relaxed = true),
    memoriesDao: MemoriesDao = mockk(relaxed = true),
    webSearchSourceDao: WebSearchSourceDao = mockk(relaxed = true),
    ttsProviderDao: TtsProviderDao = mockk(relaxed = true),
    mediaProviderDao: MediaProviderDao = mockk(relaxed = true),
    apiModelConfigurationsDao: ApiModelConfigurationsDao = mockk(relaxed = true),
    localModelsDao: LocalModelsDao = mockk(relaxed = true),
    apiCredentialsDao: ApiCredentialsDao = mockk(relaxed = true),
    localModelConfigurationsDao: LocalModelConfigurationsDao = mockk(relaxed = true),
    defaultModelsDao: DefaultModelsDao = mockk(relaxed = true),
    studioMediaDao: StudioMediaDao = mockk(relaxed = true),
    studioAlbumDao: StudioAlbumDao = mockk(relaxed = true),
    messageFileDao: MessageFileDao = mockk(relaxed = true),
    messageVisionAnalysisDao: MessageVisionAnalysisDao = mockk(relaxed = true),
    chatSummaryDao: ChatSummaryDao = mockk(relaxed = true),
    documentDao: DocumentDao = mockk(relaxed = true),
    embeddingDao: EmbeddingDao = mockk(relaxed = true),
    firestoreSyncPlaceholderDao: FirestoreSyncPlaceholderDao = mockk(relaxed = true),
    utilityModelsDao: UtilityModelsDao = mockk(relaxed = true),
): FirestoreSyncDaos =
    FirestoreSyncDaos.from(
        chatSyncDaos =
            ChatSyncDaos(
                chatDao = chatDao,
                messageDao = messageDao,
                chatSummaryDao = chatSummaryDao,
                messageFileDao = messageFileDao,
                messageVisionAnalysisDao = messageVisionAnalysisDao,
                messageTranscriptSegmentDao = messageTranscriptSegmentDao,
                messageInferenceTelemetryDao = mockk(relaxed = true),
                webSearchSourceDao = webSearchSourceDao,
                documentDao = documentDao,
                embeddingDao = embeddingDao,
                firestoreSyncPlaceholderDao = firestoreSyncPlaceholderDao,
            ),
        configSyncDaos =
            ConfigSyncDaos(
                memoriesDao = memoriesDao,
                ttsProviderDao = ttsProviderDao,
                mediaProviderDao = mediaProviderDao,
                apiModelConfigurationsDao = apiModelConfigurationsDao,
                localModelsDao = localModelsDao,
                apiCredentialsDao = apiCredentialsDao,
                localModelConfigurationsDao = localModelConfigurationsDao,
                defaultModelsDao = defaultModelsDao,
                utilityModelsDao = utilityModelsDao,
            ),
        studioSyncDaos = StudioSyncDaos(studioMediaDao = studioMediaDao, studioAlbumDao = studioAlbumDao),
    )
