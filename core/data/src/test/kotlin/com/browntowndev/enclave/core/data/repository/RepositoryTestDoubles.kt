package com.browntowndev.enclave.core.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.browntowndev.enclave.core.data.inertWorkManager
import com.browntowndev.enclave.core.data.local.EnclaveDatabase
import com.browntowndev.enclave.core.data.local.messageDao
import com.browntowndev.enclave.core.data.security.ApiKeyManager
import com.browntowndev.enclave.core.data.security.SensitiveRoomReadRepairApplier
import com.browntowndev.enclave.core.data.sync.CloudSyncTrigger
import com.browntowndev.enclave.core.testing.FakeLoggingPort
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Shared hand-written test doubles for the repository test suite (ENC-28).
 *
 * Replaces the MockK mocks removed from this package: a recording
 * [CloudSyncTrigger] subclass (over the behavior-preserving `open` seam),
 * an in-memory preferences [DataStore], and factories for the real
 * collaborators wired over a real in-memory Room database. The recording
 * trigger overrides every enqueue entry point, so it defaults to an inert
 * [WorkManager] placeholder (see inertWorkManager, ENC-253) and never
 * resolves a real WorkManager — work-runtime 2.11 made its constructor
 * internal, so no subclass fake can exist.
 */

/** Records every trigger call instead of enqueueing WorkManager work. */
class RecordingCloudSyncTrigger(
    workManager: WorkManager = inertWorkManager(),
    loggingPort: FakeLoggingPort = FakeLoggingPort(),
) : CloudSyncTrigger(workManager, loggingPort) {
    var triggerCalls = 0
        private set
    var triggerMetadataCalls = 0
        private set
    var triggerMediaCalls = 0
        private set
    var triggerMediaDownloadCalls = 0
        private set
    var triggerFilesCalls = 0
        private set
    var triggerCloudChatsPurgeCalls = 0
        private set
    var triggerCloudMediaPurgeCalls = 0
        private set
    var triggerCloudMemoriesPurgeCalls = 0
        private set
    var triggerCloudArtifactsPurgeCalls = 0
        private set

    override fun trigger() {
        triggerCalls++
    }

    override fun triggerMetadata() {
        triggerMetadataCalls++
    }

    override fun triggerMedia() {
        triggerMediaCalls++
    }

    override fun triggerMediaDownload() {
        triggerMediaDownloadCalls++
    }

    override fun triggerFiles() {
        triggerFilesCalls++
    }

    override fun triggerCloudChatsPurge() {
        triggerCloudChatsPurgeCalls++
    }

    override fun triggerCloudMediaPurge() {
        triggerCloudMediaPurgeCalls++
    }

    override fun triggerCloudMemoriesPurge() {
        triggerCloudMemoriesPurgeCalls++
    }

    override fun triggerCloudArtifactsPurge() {
        triggerCloudArtifactsPurgeCalls++
    }
}

/** Builds a real [WorkInfo] through its public constructor. */
fun testWorkInfo(
    id: java.util.UUID,
    state: WorkInfo.State,
    outputData: androidx.work.Data = androidx.work.Data.EMPTY,
    tags: Set<String> = emptySet(),
    progress: androidx.work.Data = androidx.work.Data.EMPTY,
): WorkInfo =
    WorkInfo(
        id,
        state,
        tags,
        outputData,
        progress,
        0,
        0,
        androidx.work.Constraints.Builder().build(),
        0L,
        null,
        0L,
        WorkInfo.STOP_REASON_NOT_STOPPED,
    )

/** In-memory [DataStore] over a [MutableStateFlow]; updateData applies the transform atomically. */
class InMemoryPreferencesDataStore(
    initial: Preferences = emptyPreferences(),
) : DataStore<Preferences> {
    private val state = MutableStateFlow(initial)

    override val data: Flow<Preferences> = state

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
        val updated = transform(state.value)
        state.value = updated
        return updated
    }
}

/**
 * Real [ApiKeyManager] over Robolectric SharedPreferences and the in-memory
 * preferences DataStore, mirroring the construction in ApiKeyManagerTest
 * without a file-backed DataStore.
 */
fun testApiKeyManager(
    context: Context,
    scope: CoroutineScope,
    prefsName: String,
): ApiKeyManager {
    val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
    prefs.edit().clear().commit()
    return ApiKeyManager({ prefs }, InMemoryPreferencesDataStore(), scope)
}

/**
 * Real [ChatRepositoryImpl] wired over the database's real DAOs, the real
 * read-repair applier, and the fake-backed sensitive self-healer.
 */
fun EnclaveDatabase.chatRepositoryImpl(
    syncTrigger: CloudSyncTrigger,
    transactionProvider: com.browntowndev.enclave.core.testing.FakeTransactionProvider =
        com.browntowndev.enclave.core.testing.FakeTransactionProvider(),
): ChatRepositoryImpl =
    ChatRepositoryImpl(
        syncTrigger = syncTrigger,
        chatDao = chatDao(),
        messageDao = messageDao(),
        messageTranscriptSegmentDao = messageTranscriptSegmentDao(),
        webSearchSourceDao = webSearchSourceDao(),
        documentDao = documentDao(),
        transactionProvider = transactionProvider,
        cleanupDao = cleanupDao(),
        messageInferenceTelemetryDao = messageInferenceTelemetryDao(),
        messageFileDao = messageFileDao(),
        healer = testSensitiveSelfHealer(FakeLocalDataEncryptionService()),
        readRepairApplier = realReadRepairApplier(),
    )

/** Real [SensitiveRoomReadRepairApplier] wired over the database's real DAOs. */
fun EnclaveDatabase.realReadRepairApplier(): SensitiveRoomReadRepairApplier =
    SensitiveRoomReadRepairApplier(
        chatDao = chatDao(),
        messageDao = messageDao(),
        messageFileDao = messageFileDao(),
        chatSummaryDao = chatSummaryDao(),
        memoriesDao = memoriesDao(),
        messageVisionAnalysisDao = messageVisionAnalysisDao(),
        webSearchSourceDao = webSearchSourceDao(),
        messageTranscriptSegmentDao = messageTranscriptSegmentDao(),
        documentDao = documentDao(),
        apiCredentialsDao = apiCredentialsDao(),
        apiModelConfigurationsDao = apiModelConfigurationsDao(),
        localModelConfigurationsDao = localModelConfigurationsDao(),
        ttsProviderDao = ttsProviderDao(),
        mediaProviderDao = mediaProviderDao(),
        studioAlbumDao = studioAlbumDao(),
        studioMediaDao = studioMediaDao(),
    )
