@file:Suppress("ForbiddenMockKInTests")
// MockK is used only for Firebase SDK classes (FirebaseFirestore / FirebaseFunctions) as inert constructor dummies inside hand-written fakes of owned collaborators (ENC-28).

package com.browntowndev.enclave.core.data.remote

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.browntowndev.enclave.core.data.FakeApiCredentialsDao
import com.browntowndev.enclave.core.data.FakeApiKeyManager
import com.browntowndev.enclave.core.data.FakeApiModelConfigurationsDao
import com.browntowndev.enclave.core.data.FakeChatDao
import com.browntowndev.enclave.core.data.FakeChatSummaryDao
import com.browntowndev.enclave.core.data.FakeCloudSyncTrigger
import com.browntowndev.enclave.core.data.FakeDefaultModelsDao
import com.browntowndev.enclave.core.data.FakeDocumentDao
import com.browntowndev.enclave.core.data.FakeEmbeddingDao
import com.browntowndev.enclave.core.data.FakeEnclaveDatabase
import com.browntowndev.enclave.core.data.FakeFirestoreSyncPlaceholderDao
import com.browntowndev.enclave.core.data.FakeLiveVoiceProviderDao
import com.browntowndev.enclave.core.data.FakeLocalModelConfigurationsDao
import com.browntowndev.enclave.core.data.FakeLocalModelsDao
import com.browntowndev.enclave.core.data.FakeMediaProviderDao
import com.browntowndev.enclave.core.data.FakeMemoriesDao
import com.browntowndev.enclave.core.data.FakeMessageDaos
import com.browntowndev.enclave.core.data.FakeMessageFileDao
import com.browntowndev.enclave.core.data.FakeMessageInferenceTelemetryDao
import com.browntowndev.enclave.core.data.FakeMessageTranscriptSegmentDao
import com.browntowndev.enclave.core.data.FakeMessageVisionAnalysisDao
import com.browntowndev.enclave.core.data.FakeStudioAlbumDao
import com.browntowndev.enclave.core.data.FakeStudioMediaDao
import com.browntowndev.enclave.core.data.FakeTtsProviderDao
import com.browntowndev.enclave.core.data.FakeUtilityModelsDao
import com.browntowndev.enclave.core.data.FakeWebSearchSourceDao
import com.browntowndev.enclave.core.data.local.ApiCredentialsDao
import com.browntowndev.enclave.core.data.local.ApiModelConfigurationsDao
import com.browntowndev.enclave.core.data.local.ChatDao
import com.browntowndev.enclave.core.data.local.ChatSummaryDao
import com.browntowndev.enclave.core.data.local.DefaultModelsDao
import com.browntowndev.enclave.core.data.local.DocumentDao
import com.browntowndev.enclave.core.data.local.EmbeddingDao
import com.browntowndev.enclave.core.data.local.FirestoreSyncPlaceholderDao
import com.browntowndev.enclave.core.data.local.LiveVoiceProviderDao
import com.browntowndev.enclave.core.data.local.LocalModelConfigurationsDao
import com.browntowndev.enclave.core.data.local.LocalModelsDao
import com.browntowndev.enclave.core.data.local.MediaProviderDao
import com.browntowndev.enclave.core.data.local.MemoriesDao
import com.browntowndev.enclave.core.data.local.MessageDao
import com.browntowndev.enclave.core.data.local.MessageFileDao
import com.browntowndev.enclave.core.data.local.MessageInferenceTelemetryDao
import com.browntowndev.enclave.core.data.local.MessageTranscriptSegmentDao
import com.browntowndev.enclave.core.data.local.MessageVisionAnalysisDao
import com.browntowndev.enclave.core.data.local.StudioAlbumDao
import com.browntowndev.enclave.core.data.local.StudioMediaDao
import com.browntowndev.enclave.core.data.local.TtsProviderDao
import com.browntowndev.enclave.core.data.local.UtilityModelsDao
import com.browntowndev.enclave.core.data.local.WebSearchSourceDao
import com.browntowndev.enclave.core.data.remote.firestore.BackfillSyncFeedResult
import com.browntowndev.enclave.core.data.remote.firestore.ChatSyncDaos
import com.browntowndev.enclave.core.data.remote.firestore.ConfigSyncDaos
import com.browntowndev.enclave.core.data.remote.firestore.FirestoreSyncContext
import com.browntowndev.enclave.core.data.remote.firestore.FirestoreSyncDaos
import com.browntowndev.enclave.core.data.remote.firestore.FirestoreSyncDocumentCache
import com.browntowndev.enclave.core.data.remote.firestore.FirestoreSyncEntityReconciler
import com.browntowndev.enclave.core.data.remote.firestore.FirestoreSyncEventApplier
import com.browntowndev.enclave.core.data.remote.firestore.FirestoreSyncEventDispatcher
import com.browntowndev.enclave.core.data.remote.firestore.FirestoreSyncEventFeedClient
import com.browntowndev.enclave.core.data.remote.firestore.FirestoreSyncFeedCallableClient
import com.browntowndev.enclave.core.data.remote.firestore.FirestoreSyncFeedIntegrityRecovery
import com.browntowndev.enclave.core.data.remote.firestore.FirestoreSyncFeedMetaClient
import com.browntowndev.enclave.core.data.remote.firestore.FirestoreSyncFeedStatus
import com.browntowndev.enclave.core.data.remote.firestore.FirestoreSyncLocalStore
import com.browntowndev.enclave.core.data.remote.firestore.FirestoreSyncPendingSyncLookup
import com.browntowndev.enclave.core.data.remote.firestore.FirestoreSyncStateStore
import com.browntowndev.enclave.core.data.remote.firestore.StudioSyncDaos
import com.browntowndev.enclave.core.data.remote.firestore.testEmbeddingRegenerationCoordinator
import com.browntowndev.enclave.core.data.repository.SettingsRepositoryImpl
import com.browntowndev.enclave.core.data.security.ApiKeyManager
import com.browntowndev.enclave.core.data.security.LocalDataEncryptionService
import com.browntowndev.enclave.core.data.security.SensitivePersistenceSelfHealer
import com.browntowndev.enclave.core.data.security.StaticSensitiveFieldCatalog
import com.browntowndev.enclave.core.data.sync.CloudSyncTrigger
import com.browntowndev.enclave.core.data.sync.EmbeddingRegenerationCoordinator
import com.browntowndev.enclave.core.data.sync.SearchToolSettingsSyncQueue
import com.browntowndev.enclave.core.testing.FakeCryptoManagerPort
import com.browntowndev.enclave.core.testing.FakeLoggingPort
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import com.browntowndev.enclave.domain.port.security.CryptoManagerPort
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.util.concurrent.atomic.AtomicBoolean

/** No-op feed meta client: the feed is never initialized and no event ever arrives. */
internal class FakeFirestoreSyncFeedMetaClient(
    firestore: FirebaseFirestore,
) : FirestoreSyncFeedMetaClient(firestore) {
    var feedStatus: FirestoreSyncFeedStatus? = null
    var awaitSyncEventResult: Boolean = false

    override suspend fun readFeedStatus(userId: String): FirestoreSyncFeedStatus? = feedStatus

    override suspend fun awaitSyncEvent(
        userId: String,
        sequence: Long,
        attempts: Int,
        delayMillis: Long,
    ): Boolean = awaitSyncEventResult
}

/** No-op feed callable client: backfill calls report "nothing to do". */
internal class FakeFirestoreSyncFeedCallableClient :
    FirestoreSyncFeedCallableClient(mockk<FirebaseFunctions>(relaxed = true), FakeLoggingPort()) {
    var backfillResult: BackfillSyncFeedResult? = null
    var markInitializedResult: BackfillSyncFeedResult? = null

    override suspend fun backfillIfNeeded(userId: String): BackfillSyncFeedResult? = backfillResult

    override suspend fun markFeedInitializedIfNeeded(userId: String): BackfillSyncFeedResult? = markInitializedResult
}

/** No-op entity reconciler: reports zero upserts/tombstones applied. */
internal class FakeFirestoreSyncEntityReconciler(
    firestore: FirebaseFirestore,
    ctx: FirestoreSyncContext,
    loggingPort: LoggingPort,
) : FirestoreSyncEntityReconciler(
        firestore = firestore,
        ctx = ctx,
        adapterLookup = emptyMap(),
        localStore = NoOpFirestoreSyncLocalStore,
        pendingSyncLookup = FirestoreSyncPendingSyncLookup(FakeEnclaveDatabase()),
        loggingPort = loggingPort,
    ) {
    var reconcileResult: ReconcileResult = ReconcileResult(upsertsApplied = 0, tombstonesApplied = 0)
    val reconcileCalls = mutableListOf<String>()

    override suspend fun reconcileFromCloudEntitySources(userId: String): ReconcileResult {
        reconcileCalls += userId
        return reconcileResult
    }
}

private object NoOpFirestoreSyncLocalStore : FirestoreSyncLocalStore {
    override suspend fun upsert(
        collectionPath: String,
        entityId: String,
        data: Map<String, Any?>,
        eventDeviceId: String?,
    ) = Unit

    override suspend fun delete(
        collectionPath: String,
        entityId: String,
    ) = Unit

    override suspend fun withTransaction(block: suspend () -> Unit) = block()
}

/**
 * Document cache that never touches Firestore: prefetch is a no-op and every
 * document lookup misses. This preserves the previous relaxed-mock default,
 * which existed so cache prefetch in applyLiveEvents cannot trigger live
 * Firestore calls (and dangling Tasks.await coroutines) during tests.
 */
internal class FakeFirestoreSyncDocumentCache(
    firestore: FirebaseFirestore,
) : FirestoreSyncDocumentCache(firestore) {
    override suspend fun prefetch(
        userId: String,
        events: List<com.browntowndev.enclave.core.data.remote.firestore.FirestoreSyncEvent>,
    ) = Unit

    override suspend fun getDocument(path: String): Map<String, Any?>? = null

    override fun clear() = Unit
}

private fun defaultSettingsDataStore(): androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences> =
    androidx.datastore.preferences.core.PreferenceDataStoreFactory.create(
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
    ) { java.io.File.createTempFile("settings-", ".preferences_pb") }

@Suppress("LongParameterList")
internal fun createTestFirestoreDataSource(
    context: Context = ApplicationProvider.getApplicationContext(),
    firestore: FirebaseFirestore,
    chatDao: ChatDao = FakeChatDao(),
    messageDao: MessageDao = FakeMessageDaos().dao,
    messageTranscriptSegmentDao: MessageTranscriptSegmentDao = FakeMessageTranscriptSegmentDao(),
    messageInferenceTelemetryDao: MessageInferenceTelemetryDao = FakeMessageInferenceTelemetryDao(),
    memoriesDao: MemoriesDao = FakeMemoriesDao(),
    webSearchSourceDao: WebSearchSourceDao = FakeWebSearchSourceDao(),
    ttsProviderDao: TtsProviderDao = FakeTtsProviderDao(),
    liveVoiceProviderDao: LiveVoiceProviderDao = FakeLiveVoiceProviderDao(),
    mediaProviderDao: MediaProviderDao = FakeMediaProviderDao(),
    apiModelConfigurationsDao: ApiModelConfigurationsDao = FakeApiModelConfigurationsDao(),
    localModelsDao: LocalModelsDao = FakeLocalModelsDao(),
    apiCredentialsDao: ApiCredentialsDao = FakeApiCredentialsDao(),
    localModelConfigurationsDao: LocalModelConfigurationsDao = FakeLocalModelConfigurationsDao(),
    defaultModelsDao: DefaultModelsDao = FakeDefaultModelsDao(),
    utilityModelsDao: UtilityModelsDao = FakeUtilityModelsDao(),
    studioMediaDao: StudioMediaDao = FakeStudioMediaDao(),
    studioAlbumDao: StudioAlbumDao = FakeStudioAlbumDao(),
    messageFileDao: MessageFileDao = FakeMessageFileDao(),
    messageVisionAnalysisDao: MessageVisionAnalysisDao = FakeMessageVisionAnalysisDao(),
    chatSummaryDao: ChatSummaryDao = FakeChatSummaryDao(),
    documentDao: DocumentDao = FakeDocumentDao(),
    embeddingDao: EmbeddingDao = FakeEmbeddingDao(),
    firestoreSyncPlaceholderDao: FirestoreSyncPlaceholderDao = FakeFirestoreSyncPlaceholderDao(),
    apiKeyManager: ApiKeyManager = FakeApiKeyManager(),
    cryptoManager: CryptoManagerPort = FakeCryptoManagerPort(),
    loggingPort: LoggingPort = FakeLoggingPort(),
    externalScope: CoroutineScope,
    userIdProvider: () -> String? = { "test-user-id" },
    embeddingCoordinator: EmbeddingRegenerationCoordinator =
        testEmbeddingRegenerationCoordinator(context),
    localEncryption: LocalDataEncryptionService = LenientFakeLocalDataEncryptionService(),
    sensitiveSelfHealer: SensitivePersistenceSelfHealer? = null,
    purgeInProgress: AtomicBoolean = AtomicBoolean(false),
    syncEventFeedClient: FirestoreSyncEventFeedClient = FirestoreSyncEventFeedClient(),
    syncEventApplier: FirestoreSyncEventDispatcher = FirestoreSyncEventApplier(),
    syncStateStore: FirestoreSyncStateStore = DeterministicTestSyncStateStore(),
    documentCache: FirestoreSyncDocumentCache = FakeFirestoreSyncDocumentCache(firestore),
    searchToolSettingsSyncQueue: SearchToolSettingsSyncQueue =
        SearchToolSettingsSyncQueue(
            dataStore =
                androidx.datastore.preferences.core.PreferenceDataStoreFactory.create(
                    scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO + kotlinx.coroutines.SupervisorJob()),
                ) { java.io.File.createTempFile("search-settings-", ".preferences_pb") },
        ),
    settingsRepository: SettingsRepositoryImpl? = null,
    syncTrigger: CloudSyncTrigger = FakeCloudSyncTrigger(),
    feedIntegrityRecovery: FirestoreSyncFeedIntegrityRecovery? = null,
): FirestoreDataSource {
    val healer = sensitiveSelfHealer ?: SensitivePersistenceSelfHealer(localEncryption, loggingPort)
    val resolvedSettingsRepository =
        settingsRepository
            ?: SettingsRepositoryImpl(
                dataStore = defaultSettingsDataStore(),
                apiKeyManager = apiKeyManager,
                localEncryption = localEncryption,
                loggingPort = loggingPort,
                searchToolSettingsSyncQueue = searchToolSettingsSyncQueue,
            )
    val resolvedFeedIntegrityRecovery =
        feedIntegrityRecovery
            ?: FirestoreSyncFeedIntegrityRecovery(
                metaClient = FakeFirestoreSyncFeedMetaClient(firestore),
                callableClient = FakeFirestoreSyncFeedCallableClient(),
                entityReconciler =
                    FakeFirestoreSyncEntityReconciler(
                        firestore = firestore,
                        ctx =
                            FirestoreSyncContext(
                                firestore = firestore,
                                daos =
                                    testFirestoreSyncDaos(
                                        chatDao = chatDao,
                                        messageDao = messageDao,
                                        messageTranscriptSegmentDao = messageTranscriptSegmentDao,
                                        messageInferenceTelemetryDao = messageInferenceTelemetryDao,
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
                                        documentDao = documentDao,
                                        embeddingDao = embeddingDao,
                                        firestoreSyncPlaceholderDao = firestoreSyncPlaceholderDao,
                                        utilityModelsDao = utilityModelsDao,
                                    ),
                                localEncryption = localEncryption,
                                sensitiveSelfHealer = healer,
                                firestoreSensitiveFieldRepairer =
                                    FirestoreSensitiveFieldRepairer(StaticSensitiveFieldCatalog(), healer),
                                cryptoManager = cryptoManager,
                                loggingPort = loggingPort,
                                apiKeyManager = apiKeyManager,
                                embeddingCoordinator = embeddingCoordinator,
                                userIdProvider = userIdProvider,
                                purgeInProgress = purgeInProgress,
                                searchToolSettingsSyncQueue = searchToolSettingsSyncQueue,
                                settingsRepository = resolvedSettingsRepository,
                            ),
                        loggingPort = loggingPort,
                    ),
                syncStateStore = syncStateStore,
                loggingPort = loggingPort,
            )
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
                    messageInferenceTelemetryDao = messageInferenceTelemetryDao,
                    webSearchSourceDao = webSearchSourceDao,
                    documentDao = documentDao,
                    embeddingDao = embeddingDao,
                    firestoreSyncPlaceholderDao = firestoreSyncPlaceholderDao,
                ),
            configSyncDaos =
                ConfigSyncDaos(
                    memoriesDao = memoriesDao,
                    ttsProviderDao = ttsProviderDao,
                    liveVoiceProviderDao = liveVoiceProviderDao,
                    mediaProviderDao = mediaProviderDao,
                    apiModelConfigurationsDao = apiModelConfigurationsDao,
                    localModelsDao = localModelsDao,
                    apiCredentialsDao = apiCredentialsDao,
                    localModelConfigurationsDao = localModelConfigurationsDao,
                    defaultModelsDao = defaultModelsDao,
                    utilityModelsDao = utilityModelsDao,
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
            settingsRepository = resolvedSettingsRepository,
            feedIntegrityRecovery = resolvedFeedIntegrityRecovery,
        )
    return FirestoreDataSource(facade, externalScope, syncTrigger)
}

/** Avoids real Firestore meta reads during replay tests (prevents dangling Tasks.await coroutines). */
internal fun testFeedIntegrityRecovery(
    syncStateStore: FirestoreSyncStateStore,
    loggingPort: LoggingPort,
    context: Context = ApplicationProvider.getApplicationContext(),
): FirestoreSyncFeedIntegrityRecovery {
    val dummyFirestore = mockk<FirebaseFirestore>(relaxed = true)
    val localEncryption = LenientFakeLocalDataEncryptionService()
    val healer = SensitivePersistenceSelfHealer(localEncryption, loggingPort)
    val ctx =
        FirestoreSyncContext(
            firestore = dummyFirestore,
            daos = testFirestoreSyncDaos(),
            localEncryption = localEncryption,
            sensitiveSelfHealer = healer,
            firestoreSensitiveFieldRepairer = FirestoreSensitiveFieldRepairer(StaticSensitiveFieldCatalog(), healer),
            cryptoManager = FakeCryptoManagerPort(),
            loggingPort = loggingPort,
            apiKeyManager = FakeApiKeyManager(),
            embeddingCoordinator =
                testEmbeddingRegenerationCoordinator(context),
            userIdProvider = { "test-user-id" },
            purgeInProgress = AtomicBoolean(false),
        )
    return FirestoreSyncFeedIntegrityRecovery(
        metaClient = FakeFirestoreSyncFeedMetaClient(dummyFirestore),
        callableClient = FakeFirestoreSyncFeedCallableClient(),
        entityReconciler = FakeFirestoreSyncEntityReconciler(dummyFirestore, ctx, loggingPort),
        syncStateStore = syncStateStore,
        loggingPort = loggingPort,
    )
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
    chatDao: ChatDao = FakeChatDao(),
    messageDao: MessageDao = FakeMessageDaos().dao,
    messageTranscriptSegmentDao: MessageTranscriptSegmentDao = FakeMessageTranscriptSegmentDao(),
    messageInferenceTelemetryDao: MessageInferenceTelemetryDao = FakeMessageInferenceTelemetryDao(),
    memoriesDao: MemoriesDao = FakeMemoriesDao(),
    webSearchSourceDao: WebSearchSourceDao = FakeWebSearchSourceDao(),
    ttsProviderDao: TtsProviderDao = FakeTtsProviderDao(),
    mediaProviderDao: MediaProviderDao = FakeMediaProviderDao(),
    apiModelConfigurationsDao: ApiModelConfigurationsDao = FakeApiModelConfigurationsDao(),
    localModelsDao: LocalModelsDao = FakeLocalModelsDao(),
    apiCredentialsDao: ApiCredentialsDao = FakeApiCredentialsDao(),
    localModelConfigurationsDao: LocalModelConfigurationsDao = FakeLocalModelConfigurationsDao(),
    defaultModelsDao: DefaultModelsDao = FakeDefaultModelsDao(),
    studioMediaDao: StudioMediaDao = FakeStudioMediaDao(),
    studioAlbumDao: StudioAlbumDao = FakeStudioAlbumDao(),
    messageFileDao: MessageFileDao = FakeMessageFileDao(),
    messageVisionAnalysisDao: MessageVisionAnalysisDao = FakeMessageVisionAnalysisDao(),
    chatSummaryDao: ChatSummaryDao = FakeChatSummaryDao(),
    documentDao: DocumentDao = FakeDocumentDao(),
    embeddingDao: EmbeddingDao = FakeEmbeddingDao(),
    firestoreSyncPlaceholderDao: FirestoreSyncPlaceholderDao = FakeFirestoreSyncPlaceholderDao(),
    utilityModelsDao: UtilityModelsDao = FakeUtilityModelsDao(),
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
                messageInferenceTelemetryDao = messageInferenceTelemetryDao,
                webSearchSourceDao = webSearchSourceDao,
                documentDao = documentDao,
                embeddingDao = embeddingDao,
                firestoreSyncPlaceholderDao = firestoreSyncPlaceholderDao,
            ),
        configSyncDaos =
            ConfigSyncDaos(
                memoriesDao = memoriesDao,
                ttsProviderDao = ttsProviderDao,
                liveVoiceProviderDao = FakeLiveVoiceProviderDao(),
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
