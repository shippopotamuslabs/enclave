@file:Suppress("ForbiddenMockKInTests")

// MockK retained only for the Firebase Firestore SDK surface, which has no JVM fake.

package com.browntowndev.enclave.core.data.remote.firestore

import android.content.Context
import android.content.SharedPreferences
import androidx.room.InvalidationTracker
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.work.WorkManager
import androidx.test.core.app.ApplicationProvider
import com.browntowndev.enclave.core.data.local.ApiCredentialsDao
import com.browntowndev.enclave.core.data.testWorkManager
import com.browntowndev.enclave.core.data.local.ApiModelConfigurationsDao
import com.browntowndev.enclave.core.data.local.ChatDao
import com.browntowndev.enclave.core.data.local.ChatSummaryDao
import com.browntowndev.enclave.core.data.local.CleanupDao
import com.browntowndev.enclave.core.data.local.DefaultModelsDao
import com.browntowndev.enclave.core.data.local.DocumentDao
import com.browntowndev.enclave.core.data.local.EmbeddingDao
import com.browntowndev.enclave.core.data.local.EnclaveDatabase
import com.browntowndev.enclave.core.data.local.FirestoreSyncPlaceholderDao
import com.browntowndev.enclave.core.data.local.LiveVoiceProviderDao
import com.browntowndev.enclave.core.data.local.LocalModelConfigurationsDao
import com.browntowndev.enclave.core.data.local.LocalModelsDao
import com.browntowndev.enclave.core.data.local.MediaProviderDao
import com.browntowndev.enclave.core.data.local.MemoriesDao
import com.browntowndev.enclave.core.data.local.MessageFileDao
import com.browntowndev.enclave.core.data.local.MessageInferenceTelemetryDao
import com.browntowndev.enclave.core.data.local.MessageTranscriptSegmentDao
import com.browntowndev.enclave.core.data.local.MessageVisionAnalysisDao
import com.browntowndev.enclave.core.data.local.ProfileAnalysisRunDao
import com.browntowndev.enclave.core.data.local.ProfileCorpusDao
import com.browntowndev.enclave.core.data.local.ProfileFindingsDao
import com.browntowndev.enclave.core.data.local.StudioAlbumDao
import com.browntowndev.enclave.core.data.local.StudioMediaDao
import com.browntowndev.enclave.core.data.local.TelemetryEventDao
import com.browntowndev.enclave.core.data.local.TtsProviderDao
import com.browntowndev.enclave.core.data.local.UtilityModelsDao
import com.browntowndev.enclave.core.data.local.WebSearchSourceDao
import com.browntowndev.enclave.core.data.local.MessageCommittedInferencePromptDao
import com.browntowndev.enclave.core.data.local.dao.message.MessageCommandDao
import com.browntowndev.enclave.core.data.local.dao.message.MessageQueryDao
import com.browntowndev.enclave.core.data.local.dao.message.MessageSyncCommandDao
import com.browntowndev.enclave.core.data.remote.EntitySyncAdapter
import com.browntowndev.enclave.core.data.remote.FirestoreSensitiveFieldRepairer
import com.browntowndev.enclave.core.data.remote.testFirestoreSyncDaos
import com.browntowndev.enclave.core.data.repository.FakeLocalDataEncryptionService
import com.browntowndev.enclave.core.data.repository.FakeSharedPreferences
import com.browntowndev.enclave.core.data.repository.SettingsRepositoryImpl
import com.browntowndev.enclave.core.data.security.ApiKeyManager
import com.browntowndev.enclave.core.data.security.LocalDataEncryptionService
import com.browntowndev.enclave.core.data.security.SensitivePersistenceSelfHealer
import com.browntowndev.enclave.core.data.security.StaticSensitiveFieldCatalog
import com.browntowndev.enclave.core.data.sync.EmbeddingRegenerationCoordinator
import com.browntowndev.enclave.core.data.sync.SearchToolSettingsSyncQueue
import com.browntowndev.enclave.core.testing.FakeCryptoManagerPort
import com.browntowndev.enclave.core.testing.FakeLoggingPort
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import com.browntowndev.enclave.domain.port.security.CryptoManagerPort
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import io.mockk.mockk
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.CoroutineContext

/**
 * Hand-written doubles for the Firestore sync test suites (ENC-28): a shared
 * [FirestoreSyncContext] factory wired entirely over fakes, recording fakes for
 * the owned sync collaborators, and the one remaining SDK-boundary stub
 * ([fakeFirestore]) — [FirebaseFirestore] cannot be constructed on the JVM.
 */

/** Relaxed Firebase SDK stub used as inert context filler. The ONLY MockK usage in this package's doubles. */
internal fun fakeFirestore(): FirebaseFirestore = mockk(relaxed = true)

// ---------------------------------------------------------------------------
// WorkManager: no fake exists in work-runtime 2.11 (the WorkManager
// constructor is internal). Filler collaborators get the real test
// WorkManager via testWorkManager(); their debounce scopes never dispatch,
// so no work is ever enqueued from filler contexts.
// ---------------------------------------------------------------------------

// ---------------------------------------------------------------------------
// Preferences DataStore + ApiKeyManager + embedding coordinator factories
// ---------------------------------------------------------------------------

/** In-memory [androidx.datastore.core.DataStore] for preferences, backed by a StateFlow. */
internal class FakePreferencesDataStore(
    initial: androidx.datastore.preferences.core.Preferences =
        androidx.datastore.preferences.core.emptyPreferences(),
) : androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences> {
    private val state = kotlinx.coroutines.flow.MutableStateFlow(initial)

    override val data: Flow<androidx.datastore.preferences.core.Preferences> = state

    override suspend fun updateData(
        transform: suspend (t: androidx.datastore.preferences.core.Preferences) ->
            androidx.datastore.preferences.core.Preferences,
    ): androidx.datastore.preferences.core.Preferences {
        val updated = transform(state.value)
        state.value = updated
        return updated
    }
}

/** Real [ApiKeyManager] over an in-memory [SharedPreferences] and [FakePreferencesDataStore]. */
internal fun testApiKeyManager(
    prefs: SharedPreferences = FakeSharedPreferences(),
    dataStore: androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences> =
        FakePreferencesDataStore(),
    scope: CoroutineScope = CoroutineScope(SupervisorJob()),
): ApiKeyManager = ApiKeyManager({ prefs }, dataStore, scope)

/** Dispatcher that drops every block: filler collaborators' scopes never execute work. */
private object NeverDispatchingDispatcher : CoroutineDispatcher() {
    override fun dispatch(
        context: CoroutineContext,
        block: Runnable,
    ) = Unit
}

/**
 * Real [EmbeddingRegenerationCoordinator] over a real test [WorkManager]. Its
 * debounce scope never dispatches, so no WorkManager work is ever enqueued
 * from filler contexts — matching the relaxed mock's no-op behavior in these
 * suites.
 */
internal fun testEmbeddingRegenerationCoordinator(
    context: Context = ApplicationProvider.getApplicationContext(),
    workManager: WorkManager = testWorkManager(context),
): EmbeddingRegenerationCoordinator =
    EmbeddingRegenerationCoordinator(
        workManager,
        CoroutineScope(SupervisorJob() + NeverDispatchingDispatcher),
    )

// ---------------------------------------------------------------------------
// Repairer fake (subclass of the production open seam)
// ---------------------------------------------------------------------------

/**
 * Scripted [FirestoreSensitiveFieldRepairer]: records every call and returns
 * the input unchanged unless a handler is installed — the identity behavior the
 * MockK `answers { thirdArg() }` stubs provided.
 */
internal class FakeFirestoreSensitiveFieldRepairer :
    FirestoreSensitiveFieldRepairer(
        StaticSensitiveFieldCatalog(),
        SensitivePersistenceSelfHealer(FakeLocalDataEncryptionService(), FakeLoggingPort()),
    ) {
    data class RepairCall(
        val collectionPath: String,
        val entityId: String,
        val data: Map<String, Any?>,
    )

    val repairInboundCalls = mutableListOf<RepairCall>()
    val repairOutboundCalls = mutableListOf<RepairCall>()

    var repairInboundHandler: (String, String, Map<String, Any?>) -> Map<String, Any?> =
        { _, _, data -> data }
    var repairOutboundHandler: (String, String, Map<String, Any?>) -> Map<String, Any?> =
        { _, _, data -> data }

    override fun repairInbound(
        collectionPath: String,
        entityId: String,
        data: Map<String, Any?>,
    ): Map<String, Any?> {
        repairInboundCalls += RepairCall(collectionPath, entityId, data)
        return repairInboundHandler(collectionPath, entityId, data)
    }

    override fun repairOutbound(
        collectionPath: String,
        entityId: String,
        data: Map<String, Any?>,
    ): Map<String, Any?> {
        repairOutboundCalls += RepairCall(collectionPath, entityId, data)
        return repairOutboundHandler(collectionPath, entityId, data)
    }
}

// ---------------------------------------------------------------------------
// State store fake (subclass of the production open store)
// ---------------------------------------------------------------------------

/**
 * In-memory [FirestoreSyncStateStore] with recorded writes. Defaults mirror the
 * deterministic stores the suites previously defined inline (device-a /
 * mutation-a / fixed clock).
 */
internal class FakeSyncStateStore : FirestoreSyncStateStore() {
    var sequence: Long = 0
    var feedSchemaVersionValue: Long = 0
    val setLastAppliedCalls = mutableListOf<Pair<String, Long>>()
    val setFeedSchemaVersionCalls = mutableListOf<Pair<String, Long>>()

    /** Optional shared ordering log for cross-collaborator sequence assertions. */
    var events: MutableList<String>? = null

    override suspend fun lastAppliedSequence(userId: String): Long {
        events?.add("lastAppliedSequence")
        return sequence
    }

    override suspend fun setLastAppliedSequence(
        userId: String,
        sequence: Long,
    ) {
        events?.add("setLastAppliedSequence")
        setLastAppliedCalls += userId to sequence
        this.sequence = sequence
    }

    override suspend fun feedSchemaVersion(userId: String): Long = feedSchemaVersionValue

    override suspend fun setFeedSchemaVersion(
        userId: String,
        version: Long,
    ) {
        setFeedSchemaVersionCalls += userId to version
        feedSchemaVersionValue = version
    }

    override suspend fun deviceId(): String = "device-a"

    override fun newClientMutationId(): String = "mutation-a"

    override fun currentTimeMillis(): Long = 1700000001000L
}

// ---------------------------------------------------------------------------
// Event-feed gateway / dispatcher / local store recording fakes
// ---------------------------------------------------------------------------

internal class FakeListenerRegistration : FirestoreSyncEventListenerRegistration {
    var removeCount = 0
        private set

    override fun remove() {
        removeCount++
    }
}

/** Recording [FirestoreSyncEventQueryGateway] for driving the real feed client. */
internal class FakeFeedQueryGateway : FirestoreSyncEventQueryGateway {
    val fetchCalls = mutableListOf<FirestoreSyncEventFeedClient.QueryShape>()
    val listenCalls = mutableListOf<FirestoreSyncEventFeedClient.QueryShape>()
    val listenerCallbacks = mutableListOf<suspend (List<FirestoreSyncEvent>) -> Unit>()
    val registrations = mutableListOf<FakeListenerRegistration>()

    var fetchResult: List<FirestoreSyncEvent> = emptyList()
    var fetchHandler: ((FirestoreSyncEventFeedClient.QueryShape) -> List<FirestoreSyncEvent>)? = null

    override suspend fun fetchEvents(
        queryShape: FirestoreSyncEventFeedClient.QueryShape,
    ): List<FirestoreSyncEvent> {
        fetchCalls += queryShape
        return fetchHandler?.invoke(queryShape) ?: fetchResult
    }

    override fun listen(
        queryShape: FirestoreSyncEventFeedClient.QueryShape,
        onEvents: suspend (List<FirestoreSyncEvent>) -> Unit,
    ): FirestoreSyncEventListenerRegistration {
        listenCalls += queryShape
        listenerCallbacks += onEvents
        val registration = FakeListenerRegistration()
        registrations += registration
        return registration
    }
}

/** Recording [FirestoreSyncEventDispatcher] with a scriptable result / failure. */
internal class RecordingSyncEventDispatcher : FirestoreSyncEventDispatcher {
    val applyCalls = mutableListOf<Pair<String, FirestoreSyncEvent>>()

    var applyHandler: (FirestoreSyncEvent) -> FirestoreSyncEventApplier.ApplyResult =
        { event -> FirestoreSyncEventApplier.ApplyResult(appliedCount = 1, lastAppliedSequence = event.sequence) }
    var applyThrowable: Throwable? = null

    override suspend fun apply(
        userId: String,
        event: FirestoreSyncEvent,
    ): FirestoreSyncEventApplier.ApplyResult {
        applyCalls += userId to event
        applyThrowable?.let { throw it }
        return applyHandler(event)
    }
}

/** Recording [FirestoreSyncLocalStore]; transactions run the block inline. */
internal class RecordingSyncLocalStore : FirestoreSyncLocalStore {
    data class UpsertCall(
        val collectionPath: String,
        val entityId: String,
        val data: Map<String, Any?>,
        val eventDeviceId: String?,
    )

    val upsertCalls = mutableListOf<UpsertCall>()
    val deleteCalls = mutableListOf<Pair<String, String>>()

    override suspend fun upsert(
        collectionPath: String,
        entityId: String,
        data: Map<String, Any?>,
        eventDeviceId: String?,
    ) {
        upsertCalls += UpsertCall(collectionPath, entityId, data, eventDeviceId)
    }

    override suspend fun delete(
        collectionPath: String,
        entityId: String,
    ) {
        deleteCalls += collectionPath to entityId
    }

    override suspend fun withTransaction(block: suspend () -> Unit) {
        block()
    }
}

/** Recording [FirestoreSyncDocumentCache] subclass: records without touching Firestore. */
internal class RecordingDocumentCache(
    firestore: FirebaseFirestore,
) : FirestoreSyncDocumentCache(firestore) {
    val prefetchCalls = mutableListOf<Pair<String, List<FirestoreSyncEvent>>>()
    var clearCalls = 0
        private set

    override suspend fun prefetch(
        userId: String,
        events: List<FirestoreSyncEvent>,
    ) {
        prefetchCalls += userId to events
    }

    override fun clear() {
        clearCalls++
    }
}

// ---------------------------------------------------------------------------
// Feed-integrity client fakes (subclasses of the production open seams)
// ---------------------------------------------------------------------------

internal class FakeFirestoreSyncFeedMetaClient(
    firestore: FirebaseFirestore,
) : FirestoreSyncFeedMetaClient(firestore) {
    val awaitSyncEventCalls = mutableListOf<Pair<String, Long>>()
    val readFeedStatusCalls = mutableListOf<String>()

    var awaitSyncEventResult: Boolean = true
    var feedStatus: FirestoreSyncFeedStatus? = null

    override suspend fun awaitSyncEvent(
        userId: String,
        sequence: Long,
        attempts: Int,
        delayMillis: Long,
    ): Boolean {
        awaitSyncEventCalls += userId to sequence
        return awaitSyncEventResult
    }

    override suspend fun readFeedStatus(userId: String): FirestoreSyncFeedStatus? {
        readFeedStatusCalls += userId
        return feedStatus
    }
}

internal class FakeFirestoreSyncFeedCallableClient(
    functions: FirebaseFunctions,
    loggingPort: LoggingPort = FakeLoggingPort(),
) : FirestoreSyncFeedCallableClient(functions, loggingPort) {
    val backfillCalls = mutableListOf<String>()
    val markInitializedCalls = mutableListOf<String>()

    var backfillResult: BackfillSyncFeedResult? = null
    var markInitializedResult: BackfillSyncFeedResult? = null

    override suspend fun backfillIfNeeded(userId: String): BackfillSyncFeedResult? {
        backfillCalls += userId
        return backfillResult
    }

    override suspend fun markFeedInitializedIfNeeded(userId: String): BackfillSyncFeedResult? {
        markInitializedCalls += userId
        return markInitializedResult
    }
}

internal class FakeFirestoreSyncEntityReconciler(
    firestore: FirebaseFirestore = fakeFirestore(),
    ctx: FirestoreSyncContext = testSyncContext(firestore = firestore),
    localStore: FirestoreSyncLocalStore = RecordingSyncLocalStore(),
    pendingSyncLookup: FirestoreSyncPendingSyncLookup = FirestoreSyncPendingSyncLookup(FakeEnclaveDatabase()),
    loggingPort: LoggingPort = FakeLoggingPort(),
) : FirestoreSyncEntityReconciler(firestore, ctx, emptyMap(), localStore, pendingSyncLookup, loggingPort) {
    val reconcileCalls = mutableListOf<String>()

    var reconcileResult: ReconcileResult = ReconcileResult(upsertsApplied = 0, tombstonesApplied = 0)

    override suspend fun reconcileFromCloudEntitySources(userId: String): ReconcileResult {
        reconcileCalls += userId
        return reconcileResult
    }
}

// ---------------------------------------------------------------------------
// Entity adapter fake
// ---------------------------------------------------------------------------

/** Scriptable [EntitySyncAdapter] fake with recorded local writes. */
internal class FakeSyncAdapter<T>(
    override val collectionPath: String,
    var fromFirestoreResult: T? = null,
) : EntitySyncAdapter<T> {
    data class UpsertLocalCall<T>(
        val entity: T,
        val overwrite: Boolean,
    )

    val upsertLocalCalls = mutableListOf<UpsertLocalCall<T>>()
    val deleteLocalCalls = mutableListOf<String>()
    val markSyncedCalls = mutableListOf<List<T>>()
    var claimPendingCalls = 0
        private set

    override suspend fun claimPending() {
        claimPendingCalls++
    }

    override suspend fun getClaimedForSync(): List<T> = emptyList()

    override suspend fun markSynced(entities: List<T>) {
        markSyncedCalls += entities
    }

    override suspend fun toFirestore(local: T): Map<String, Any?> = emptyMap()

    override fun fromFirestore(doc: DocumentSnapshot): T? = fromFirestoreResult

    override suspend fun upsertLocal(
        entity: T,
        doc: DocumentSnapshot,
        overwrite: Boolean,
    ) {
        upsertLocalCalls += UpsertLocalCall(entity, overwrite)
    }

    override suspend fun deleteLocal(id: String) {
        deleteLocalCalls += id
    }

    override fun getEntityId(entity: T): String = entity.toString()
}

// ---------------------------------------------------------------------------
// Fake EnclaveDatabase — never opened; DAO getters return recording fakes
// ---------------------------------------------------------------------------

/**
 * Hand-written [EnclaveDatabase] subclass whose DAO getters return the
 * recording fakes from this package (and [FakeChatDao] for the abstract
 * ChatDao). The database is never opened: [createOpenHelper] throws if any
 * code path tries, matching the filler role the relaxed mock played.
 */
internal class FakeEnclaveDatabase : EnclaveDatabase() {
    val chatDaoFake = FakeChatDao()
    val messageQueryDaoHandle = recordingDao<MessageQueryDao>()
    val messageCommandDaoHandle = recordingDao<MessageCommandDao>()
    val messageSyncCommandDaoHandle = recordingDao<MessageSyncCommandDao>()

    override fun chatDao(): ChatDao = chatDaoFake

    override fun messageQueryDao(): MessageQueryDao = messageQueryDaoHandle.dao

    override fun messageCommandDao(): MessageCommandDao = messageCommandDaoHandle.dao

    override fun messageSyncCommandDao(): MessageSyncCommandDao = messageSyncCommandDaoHandle.dao

    override fun chatSummaryDao(): ChatSummaryDao = FakeChatSummaryDao()

    override fun localModelsDao(): LocalModelsDao = recordingDao<LocalModelsDao>().dao

    override fun localModelConfigurationsDao(): LocalModelConfigurationsDao =
        recordingDao<LocalModelConfigurationsDao>().dao

    override fun apiModelConfigurationsDao(): ApiModelConfigurationsDao =
        recordingDao<ApiModelConfigurationsDao>().dao

    override fun apiCredentialsDao(): ApiCredentialsDao = recordingDao<ApiCredentialsDao>().dao

    override fun defaultModelsDao(): DefaultModelsDao = recordingDao<DefaultModelsDao>().dao

    override fun webSearchSourceDao(): WebSearchSourceDao = recordingDao<WebSearchSourceDao>().dao

    override fun messageVisionAnalysisDao(): MessageVisionAnalysisDao =
        recordingDao<MessageVisionAnalysisDao>().dao

    override fun embeddingDao(): EmbeddingDao = recordingDao<EmbeddingDao>().dao

    override fun ttsProviderDao(): TtsProviderDao = recordingDao<TtsProviderDao>().dao

    override fun liveVoiceProviderDao(): LiveVoiceProviderDao =
        recordingDao<LiveVoiceProviderDao>().dao

    override fun memoriesDao(): MemoriesDao = recordingDao<MemoriesDao>().dao

    override fun mediaProviderDao(): MediaProviderDao = recordingDao<MediaProviderDao>().dao

    override fun studioMediaDao(): StudioMediaDao = recordingDao<StudioMediaDao>().dao

    override fun studioAlbumDao(): StudioAlbumDao = recordingDao<StudioAlbumDao>().dao

    override fun messageFileDao(): MessageFileDao = recordingDao<MessageFileDao>().dao

    override fun messageTranscriptSegmentDao(): MessageTranscriptSegmentDao =
        FakeMessageTranscriptSegmentDao()

    override fun cleanupDao(): CleanupDao = recordingDao<CleanupDao>().dao

    override fun documentDao(): DocumentDao = recordingDao<DocumentDao>().dao

    override fun utilityModelsDao(): UtilityModelsDao = recordingDao<UtilityModelsDao>().dao

    override fun messageInferenceTelemetryDao(): MessageInferenceTelemetryDao =
        FakeMessageInferenceTelemetryDao()

    override fun firestoreSyncPlaceholderDao(): FirestoreSyncPlaceholderDao =
        FakeFirestoreSyncPlaceholderDao()

    override fun messageCommittedInferencePromptDao(): MessageCommittedInferencePromptDao =
        recordingDao<MessageCommittedInferencePromptDao>().dao

    override fun telemetryEventDao(): TelemetryEventDao = FakeTelemetryEventDao()

    override fun profileCorpusDao(): ProfileCorpusDao = recordingDao<ProfileCorpusDao>().dao

    override fun profileFindingsDao(): ProfileFindingsDao = recordingDao<ProfileFindingsDao>().dao

    override fun profileAnalysisRunDao(): ProfileAnalysisRunDao =
        recordingDao<ProfileAnalysisRunDao>().dao

    protected override fun createOpenHelper(
        config: androidx.room.DatabaseConfiguration,
    ): SupportSQLiteOpenHelper =
        throw UnsupportedOperationException("FakeEnclaveDatabase is never opened")

    protected override fun createInvalidationTracker(): InvalidationTracker = InvalidationTracker(this)

    override fun clearAllTables() = Unit
}

// ---------------------------------------------------------------------------
// Shared FirestoreSyncContext factory
// ---------------------------------------------------------------------------

/**
 * A real [FirestoreSyncContext] wired over fakes — the shared replacement for
 * the per-test `createContext()` MockK filler blocks in this package.
 */
internal fun testSyncContext(
    context: Context = ApplicationProvider.getApplicationContext(),
    firestore: FirebaseFirestore = fakeFirestore(),
    daos: FirestoreSyncDaos = testFirestoreSyncDaos(),
    localEncryption: LocalDataEncryptionService = FakeLocalDataEncryptionService(),
    loggingPort: LoggingPort = FakeLoggingPort(),
    cryptoManager: CryptoManagerPort = FakeCryptoManagerPort(),
    repairer: FirestoreSensitiveFieldRepairer = FakeFirestoreSensitiveFieldRepairer(),
    apiKeyManager: ApiKeyManager = testApiKeyManager(),
    embeddingCoordinator: EmbeddingRegenerationCoordinator = testEmbeddingRegenerationCoordinator(context),
    userId: String? = "user-1",
    purgeInProgress: AtomicBoolean = AtomicBoolean(false),
    searchToolSettingsSyncQueue: SearchToolSettingsSyncQueue? = null,
    settingsRepository: SettingsRepositoryImpl? = null,
): FirestoreSyncContext =
    FirestoreSyncContext(
        firestore = firestore,
        daos = daos,
        localEncryption = localEncryption,
        sensitiveSelfHealer = SensitivePersistenceSelfHealer(localEncryption, loggingPort),
        firestoreSensitiveFieldRepairer = repairer,
        cryptoManager = cryptoManager,
        loggingPort = loggingPort,
        apiKeyManager = apiKeyManager,
        embeddingCoordinator = embeddingCoordinator,
        userIdProvider = { userId },
        purgeInProgress = purgeInProgress,
        searchToolSettingsSyncQueue = searchToolSettingsSyncQueue,
        settingsRepository = settingsRepository,
    )
