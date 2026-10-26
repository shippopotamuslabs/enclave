@file:Suppress("ForbiddenMockKInTests")
// MockK is used only for WorkManager, an AndroidX SDK type whose constructor is internal in work-runtime 2.11, so no hand-writable fake exists (ENC-253).

package com.browntowndev.enclave.core.data

import android.content.Context
import android.content.SharedPreferences
import androidx.room.DatabaseConfiguration
import androidx.room.InvalidationTracker
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.work.Configuration
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.ForegroundUpdater
import androidx.work.ListenableWorker
import androidx.work.ProgressUpdater
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.impl.utils.SerialExecutorImpl
import androidx.work.impl.utils.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import androidx.work.impl.utils.taskexecutor.SerialExecutor
import androidx.work.impl.utils.taskexecutor.TaskExecutor
import com.browntowndev.enclave.core.data.local.ApiCredentialsDao
import com.browntowndev.enclave.core.data.local.ApiModelConfigurationsDao
import com.browntowndev.enclave.core.data.local.ChatDao
import com.browntowndev.enclave.core.data.local.ChatEntity
import com.browntowndev.enclave.core.data.local.ChatSummaryDao
import com.browntowndev.enclave.core.data.local.ChatSummaryEntity
import com.browntowndev.enclave.core.data.local.CleanupDao
import com.browntowndev.enclave.core.data.local.DefaultModelAssignmentView
import com.browntowndev.enclave.core.data.local.DefaultModelEntity
import com.browntowndev.enclave.core.data.local.DefaultModelsDao
import com.browntowndev.enclave.core.data.local.DocumentDao
import com.browntowndev.enclave.core.data.local.DocumentEntity
import com.browntowndev.enclave.core.data.local.EmbeddingDao
import com.browntowndev.enclave.core.data.local.EmbeddingEntity
import com.browntowndev.enclave.core.data.local.EnclaveDatabase
import com.browntowndev.enclave.core.data.local.FirestoreSyncPlaceholderDao
import com.browntowndev.enclave.core.data.local.LiveVoiceProviderDao
import com.browntowndev.enclave.core.data.local.LiveVoiceProviderEntity
import com.browntowndev.enclave.core.data.local.LocalModelConfigurationEntity
import com.browntowndev.enclave.core.data.local.LocalModelConfigurationsDao
import com.browntowndev.enclave.core.data.local.LocalModelEntity
import com.browntowndev.enclave.core.data.local.LocalModelsDao
import com.browntowndev.enclave.core.data.local.MediaProviderDao
import com.browntowndev.enclave.core.data.local.MediaProviderEntity
import com.browntowndev.enclave.core.data.local.MemoriesDao
import com.browntowndev.enclave.core.data.local.MemoriesEntity
import com.browntowndev.enclave.core.data.local.MemoryEmbeddingMatch
import com.browntowndev.enclave.core.data.local.MessageCommittedInferencePromptDao
import com.browntowndev.enclave.core.data.local.MessageCommittedInferencePromptEntity
import com.browntowndev.enclave.core.data.local.MessageEntity
import com.browntowndev.enclave.core.data.local.MessageFileDao
import com.browntowndev.enclave.core.data.local.MessageFileEntity
import com.browntowndev.enclave.core.data.local.MessageInferenceTelemetryDao
import com.browntowndev.enclave.core.data.local.MessageInferenceTelemetryEntity
import com.browntowndev.enclave.core.data.local.MessageTranscriptSegmentDao
import com.browntowndev.enclave.core.data.local.MessageTranscriptSegmentEntity
import com.browntowndev.enclave.core.data.local.MessageVisionAnalysisDao
import com.browntowndev.enclave.core.data.local.MessageVisionAnalysisEntity
import com.browntowndev.enclave.core.data.local.ProfileAnalysisRunDao
import com.browntowndev.enclave.core.data.local.ProfileAnalysisRunEntity
import com.browntowndev.enclave.core.data.local.ProfileCorpusConversationEntity
import com.browntowndev.enclave.core.data.local.ProfileCorpusDao
import com.browntowndev.enclave.core.data.local.ProfileCorpusMessageEntity
import com.browntowndev.enclave.core.data.local.ProfileFindingsDao
import com.browntowndev.enclave.core.data.local.ProfileFindingsEntity
import com.browntowndev.enclave.core.data.local.StudioAlbumDao
import com.browntowndev.enclave.core.data.local.StudioAlbumEntity
import com.browntowndev.enclave.core.data.local.StudioMediaDao
import com.browntowndev.enclave.core.data.local.StudioMediaEntity
import com.browntowndev.enclave.core.data.local.TelemetryEventDao
import com.browntowndev.enclave.core.data.local.TelemetryEventEntity
import com.browntowndev.enclave.core.data.local.TtsProviderDao
import com.browntowndev.enclave.core.data.local.TtsProviderEntity
import com.browntowndev.enclave.core.data.local.UtilityModelEntity
import com.browntowndev.enclave.core.data.local.UtilityModelsDao
import com.browntowndev.enclave.core.data.local.WebSearchSourceDao
import com.browntowndev.enclave.core.data.local.WebSearchSourceEntity
import com.browntowndev.enclave.core.data.local.dao.message.MessageCommandDao
import com.browntowndev.enclave.core.data.local.dao.message.MessageDao
import com.browntowndev.enclave.core.data.local.dao.message.MessageDaoPersist
import com.browntowndev.enclave.core.data.local.dao.message.MessageQueryDao
import com.browntowndev.enclave.core.data.local.dao.message.MessageSyncCommandDao
import com.browntowndev.enclave.core.data.local.dao.message.MessageWithFiles
import com.browntowndev.enclave.core.data.repository.InMemoryPreferencesDataStore
import com.browntowndev.enclave.core.data.security.ApiKeyManager
import com.browntowndev.enclave.core.data.sync.CloudSyncTrigger
import com.browntowndev.enclave.core.testing.FakeEntitlementPort
import com.browntowndev.enclave.core.testing.FakeLoggingPort
import com.browntowndev.enclave.domain.model.artifact.MimeType
import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.model.chat.MessageState
import com.browntowndev.enclave.domain.model.config.ApiCredentialsId
import com.browntowndev.enclave.domain.model.config.ApiModelConfigurationId
import com.browntowndev.enclave.domain.model.config.LocalModelConfigurationId
import com.browntowndev.enclave.domain.model.config.LocalModelId
import com.browntowndev.enclave.domain.model.config.UtilityModelId
import com.browntowndev.enclave.domain.model.config.UtilityType
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.model.inference.PipelineStep
import com.browntowndev.enclave.domain.model.memory.MemoryCategory
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.util.UUID
import java.util.concurrent.Executor

/**
 * Hand-written test doubles for the core/data test source set (ENC-28).
 *
 * These replace MockK relaxed mocks for Enclave-owned DAOs and collaborators:
 * reads return relaxed-style defaults (empty / null / 0) unless a test programs
 * a result, and writes are recorded for state-based assertions.
 */

/** In-memory [SharedPreferences] backed by a plain map. */
class FakeSharedPreferences : SharedPreferences {
    val values = mutableMapOf<String, Any?>()

    override fun getAll(): MutableMap<String, *> = values.toMutableMap()

    override fun getString(
        key: String?,
        defValue: String?,
    ): String? = values[key] as? String ?: defValue

    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(
        key: String?,
        defValues: MutableSet<String>?,
    ): MutableSet<String>? = (values[key] as? Set<String>)?.toMutableSet() ?: defValues

    override fun getInt(
        key: String?,
        defValue: Int,
    ): Int = values[key] as? Int ?: defValue

    override fun getLong(
        key: String?,
        defValue: Long,
    ): Long = values[key] as? Long ?: defValue

    override fun getFloat(
        key: String?,
        defValue: Float,
    ): Float = values[key] as? Float ?: defValue

    override fun getBoolean(
        key: String?,
        defValue: Boolean,
    ): Boolean = values[key] as? Boolean ?: defValue

    override fun contains(key: String?): Boolean = values.containsKey(key)

    override fun edit(): SharedPreferences.Editor = Editor()

    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

    inner class Editor : SharedPreferences.Editor {
        private val pending = mutableMapOf<String, Any?>()
        private val removals = mutableSetOf<String>()
        private var clearAll = false

        override fun putString(
            key: String?,
            value: String?,
        ): SharedPreferences.Editor = apply { pending[key.orEmpty()] = value }

        override fun putStringSet(
            key: String?,
            values: MutableSet<String>?,
        ): SharedPreferences.Editor = apply { pending[key.orEmpty()] = values }

        override fun putInt(
            key: String?,
            value: Int,
        ): SharedPreferences.Editor = apply { pending[key.orEmpty()] = value }

        override fun putLong(
            key: String?,
            value: Long,
        ): SharedPreferences.Editor = apply { pending[key.orEmpty()] = value }

        override fun putFloat(
            key: String?,
            value: Float,
        ): SharedPreferences.Editor = apply { pending[key.orEmpty()] = value }

        override fun putBoolean(
            key: String?,
            value: Boolean,
        ): SharedPreferences.Editor = apply { pending[key.orEmpty()] = value }

        override fun remove(key: String?): SharedPreferences.Editor = apply { removals += key.orEmpty() }

        override fun clear(): SharedPreferences.Editor = apply { clearAll = true }

        override fun commit(): Boolean {
            applyChanges()
            return true
        }

        override fun apply() = applyChanges()

        private fun applyChanges() {
            if (clearAll) values.clear()
            removals.forEach { values.remove(it) }
            values.putAll(pending)
        }
    }
}

/**
 * [ApiKeyManager] fake: the real manager over in-memory stores, with the
 * sync-claim surface overridden by programmable state + recorded clears.
 */
class FakeApiKeyManager :
    ApiKeyManager(
        prefsProvider = { FakeSharedPreferences() },
        dataStore = InMemoryPreferencesDataStore(),
        syncScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    ) {
    var claimedSyncAliases: Set<String> = emptySet()
    var claimedDeleteAliases: Set<String> = emptySet()
    val storedValues = mutableMapOf<String, String?>()
    val clearClaimedSyncCalls = mutableListOf<Collection<String>>()
    val clearClaimedDeleteCalls = mutableListOf<Collection<String>>()

    override fun get(credentialAlias: String): String? = storedValues[credentialAlias]

    override suspend fun getClaimedSyncAliases(): Set<String> = claimedSyncAliases

    override suspend fun getClaimedDeleteAliases(): Set<String> = claimedDeleteAliases

    override suspend fun clearClaimedSync(aliases: Collection<String>) {
        clearClaimedSyncCalls += aliases
        claimedSyncAliases = claimedSyncAliases - aliases.toSet()
    }

    override suspend fun clearClaimedDelete(aliases: Collection<String>) {
        clearClaimedDeleteCalls += aliases
        claimedDeleteAliases = claimedDeleteAliases - aliases.toSet()
    }
}

/**
 * Real test [WorkManager] backed by [WorkManagerTestInitHelper] (ENC-28).
 *
 * WorkManager's constructor is internal in work-runtime 2.11, so the former
 * hand-written subclass fake cannot exist; tests get the real implementation
 * instead. Returns the already-initialized instance when one is present (so a
 * test that installed a custom [Configuration] keeps it); otherwise installs a
 * synchronous-executor configuration. Enqueued work stays ENQUEUED unless the
 * owning test drives it — filler collaborators never trigger execution.
 */
fun testWorkManager(context: Context): WorkManager {
    runCatching { WorkManager.getInstance(context) }.getOrNull()?.let { return it }
    val config =
        Configuration
            .Builder()
            .setExecutor(SynchronousExecutor())
            .setTaskExecutor(SynchronousExecutor())
            .build()
    WorkManagerTestInitHelper.initializeTestWorkManager(context, config)
    return WorkManager.getInstance(context)
}

/** Real [WorkerParameters] for worker tests, backed by inert executors/updaters. */
fun testWorkerParameters(): WorkerParameters {
    val directExecutor = Executor { it.run() }
    return WorkerParameters(
        UUID.randomUUID(),
        Data.EMPTY,
        emptyList(),
        WorkerParameters.RuntimeExtras(),
        0,
        0,
        directExecutor,
        Dispatchers.Default,
        object : TaskExecutor {
            override fun getMainThreadExecutor(): Executor = directExecutor

            override fun getSerialTaskExecutor(): SerialExecutor = SerialExecutorImpl(directExecutor)
        },
        object : WorkerFactory() {
            override fun createWorker(
                appContext: Context,
                workerClassName: String,
                workerParameters: WorkerParameters,
            ): ListenableWorker? = null
        },
        ProgressUpdater { _, _, _ -> Futures.immediateFuture(null) },
        ForegroundUpdater { _, _, _ -> Futures.immediateFuture(null) },
    )
}

/**
 * Inert [WorkManager] placeholder that never opens WorkManager's Room database
 * or loads native SQLite (ENC-253). [CloudSyncTrigger] and
 * [com.browntowndev.enclave.core.data.sync.EmbeddingRegenerationCoordinator]
 * take a [WorkManager] eagerly in their production constructors, so a test
 * double that overrides every enqueue path still needs an instance to pass to
 * `super(...)`. A relaxed MockK mock satisfies that slot without resolving a
 * real WorkManager; it must only be used where every enqueue method is
 * overridden (record-only) or the dispatching scope never runs. Tests that
 * actually enqueue work must pass a real instance from [testWorkManager].
 */
fun inertWorkManager(): WorkManager = mockk(relaxed = true)

/** No-op [CloudSyncTrigger] recording which trigger entry points fired. */
class FakeCloudSyncTrigger(
    workManager: WorkManager = inertWorkManager(),
) : CloudSyncTrigger(workManager, FakeLoggingPort(), FakeEntitlementPort()) {
    var triggerCallCount = 0
    var triggerMetadataCallCount = 0
    var triggerMediaCallCount = 0
    var triggerMediaDownloadCallCount = 0
    var triggerFilesCallCount = 0
    var triggerCloudChatsPurgeCallCount = 0
    var triggerCloudMediaPurgeCallCount = 0
    var triggerCloudMemoriesPurgeCallCount = 0
    var triggerCloudArtifactsPurgeCallCount = 0

    override fun trigger() {
        triggerCallCount++
    }

    override fun triggerMetadata() {
        triggerMetadataCallCount++
    }

    override fun triggerMedia() {
        triggerMediaCallCount++
    }

    override fun triggerMediaDownload() {
        triggerMediaDownloadCallCount++
    }

    override fun triggerFiles() {
        triggerFilesCallCount++
    }

    override fun triggerCloudChatsPurge() {
        triggerCloudChatsPurgeCallCount++
    }

    override fun triggerCloudMediaPurge() {
        triggerCloudMediaPurgeCallCount++
    }

    override fun triggerCloudMemoriesPurge() {
        triggerCloudMemoriesPurgeCallCount++
    }

    override fun triggerCloudArtifactsPurge() {
        triggerCloudArtifactsPurgeCallCount++
    }
}

// ---------------------------------------------------------------------------
// DAO fakes (relaxed-style defaults + recorded writes)
// ---------------------------------------------------------------------------

class FakeChatDao : ChatDao() {
    val upsertCalls = mutableListOf<ChatEntity>()
    val markSyncedCalls = mutableListOf<List<ChatId>>()
    var claimedForSyncResult: List<ChatEntity> = emptyList()
    var countActiveChatsResult: Int = 0
    var chatByIdResult: ChatEntity? = null

    override suspend fun upsert(chatEntity: ChatEntity): Long {
        upsertCalls += chatEntity
        return 1L
    }

    override suspend fun insertIfAbsent(chatEntity: ChatEntity): Long = 1L

    override suspend fun update(chatEntity: ChatEntity): Int = 1

    override suspend fun delete(chatEntity: ChatEntity): Int = 1

    override fun getAllChats(): Flow<List<ChatEntity>> = flowOf(emptyList())

    override suspend fun countActiveChats(): Int = countActiveChatsResult

    override suspend fun getChatById(id: ChatId): ChatEntity? = chatByIdResult

    override suspend fun getChatsByIds(ids: List<ChatId>): List<ChatEntity> = emptyList()

    override suspend fun claimPending() = Unit

    override suspend fun getClaimedForSync(): List<ChatEntity> = claimedForSyncResult

    override suspend fun markSynced(ids: List<ChatId>) {
        markSyncedCalls += ids
    }

    override suspend fun updatePinStatus(
        chatId: ChatId,
        timestamp: Long,
    ): Int = 1

    override suspend fun softDeleteById(
        chatId: ChatId,
        timestamp: Long,
    ): Int = 1

    override suspend fun purgeDeleted(ids: List<ChatId>): Int = 0

    override suspend fun deleteOrphanedPlaceholders(): Int = 0

    override suspend fun updateName(
        chatId: ChatId,
        newName: String,
        timestamp: Long,
    ): Int = 1

    override fun searchChats(
        query: String,
        messageIds: List<MessageId>,
    ): Flow<List<ChatEntity>> = flowOf(emptyList())
}

class FakeChatSummaryDao : ChatSummaryDao() {
    var claimedForSyncResult: List<ChatSummaryEntity> = emptyList()
    val markSyncedCalls = mutableListOf<List<Long>>()

    override fun getSummaryForChat(chatId: ChatId): Flow<ChatSummaryEntity?> = flowOf(null)

    override suspend fun getSummaryForChatSync(chatId: ChatId): ChatSummaryEntity? = null

    override suspend fun insertOrUpdateSummary(summary: ChatSummaryEntity) = Unit

    override suspend fun getByIdSync(id: Long): ChatSummaryEntity? = null

    override suspend fun claimPending() = Unit

    override suspend fun getClaimedForSync(): List<ChatSummaryEntity> = claimedForSyncResult

    override suspend fun markSynced(ids: List<Long>) {
        markSyncedCalls += ids
    }

    override suspend fun softDeleteById(
        id: Long,
        timestamp: Long,
    ): Int = 1

    override suspend fun deleteSummaryForChat(
        chatId: ChatId,
        timestamp: Long,
    ): Int = 1

    override suspend fun getSummaryByChatIdIncludingDeletedSync(chatId: ChatId): ChatSummaryEntity? = null
}

class FakeFirestoreSyncPlaceholderDao : FirestoreSyncPlaceholderDao() {
    override suspend fun insertChatIfAbsent(chatEntity: ChatEntity): Long = 1L

    override suspend fun insertMessageIfAbsent(messageEntity: MessageEntity): Long = 1L
}

class FakeMessageInferenceTelemetryDao : MessageInferenceTelemetryDao() {
    var claimedForSyncResult: List<MessageInferenceTelemetryEntity> = emptyList()
    val markSyncedCalls = mutableListOf<List<String>>()

    override suspend fun insertAll(rows: List<MessageInferenceTelemetryEntity>) = Unit

    override suspend fun upsert(entity: MessageInferenceTelemetryEntity) = Unit

    override suspend fun getById(id: String): MessageInferenceTelemetryEntity? = null

    override fun observeByMessageId(messageId: String): Flow<List<MessageInferenceTelemetryEntity>> = flowOf(emptyList())

    override fun observeByChatId(chatId: String): Flow<List<MessageInferenceTelemetryEntity>> = flowOf(emptyList())

    override fun observeAll(): Flow<List<MessageInferenceTelemetryEntity>> = flowOf(emptyList())

    override suspend fun claimPending() = Unit

    override suspend fun getClaimedForSync(): List<MessageInferenceTelemetryEntity> = claimedForSyncResult

    override suspend fun markSynced(ids: List<String>) {
        markSyncedCalls += ids
    }

    override suspend fun softDeleteById(
        id: String,
        timestamp: Long,
    ) = Unit

    override suspend fun softDeleteAllActiveForMessage(
        messageId: String,
        timestamp: Long,
    ) = Unit
}

class FakeMessageTranscriptSegmentDao : MessageTranscriptSegmentDao() {
    var claimedForSyncResult: List<MessageTranscriptSegmentEntity> = emptyList()
    val markSyncedCalls = mutableListOf<List<String>>()

    override fun observeSegmentsForChat(chatId: ChatId): Flow<List<MessageTranscriptSegmentEntity>> = flowOf(emptyList())

    override suspend fun getById(id: String): MessageTranscriptSegmentEntity? = null

    override suspend fun softDeleteAllActiveForMessage(
        messageId: MessageId,
        timestamp: Long,
    ) = Unit

    override suspend fun insertAll(segments: List<MessageTranscriptSegmentEntity>) = Unit

    override suspend fun upsert(entity: MessageTranscriptSegmentEntity) = Unit

    override suspend fun softDeleteById(
        id: String,
        timestamp: Long,
    ) = Unit

    override suspend fun claimPending() = Unit

    override suspend fun getClaimedForSync(): List<MessageTranscriptSegmentEntity> = claimedForSyncResult

    override suspend fun markSynced(ids: List<String>) {
        markSyncedCalls += ids
    }
}

class FakeDocumentDao : DocumentDao {
    val documentsById = mutableMapOf<String, DocumentEntity?>()
    var allDocuments: List<DocumentEntity> = emptyList()
    val insertedDocuments = mutableListOf<DocumentEntity>()
    val updatedDocuments = mutableListOf<DocumentEntity>()
    val searchSimilarBlocksQueries = mutableListOf<androidx.sqlite.db.SupportSQLiteQuery>()
    var searchSimilarBlocksResult: List<String> = emptyList()

    override fun getAllDocuments(): Flow<List<DocumentEntity>> = flowOf(allDocuments)

    override suspend fun getDocumentById(id: String): DocumentEntity? = documentsById[id]

    override fun getDocumentsByChatIdFlow(chatId: ChatId): Flow<List<DocumentEntity>> = flowOf(emptyList())

    override fun getDocumentsByMessageIdFlow(messageId: MessageId): Flow<List<DocumentEntity>> = flowOf(emptyList())

    override suspend fun insertDocument(document: DocumentEntity) {
        insertedDocuments += document
    }

    override suspend fun updateDocument(document: DocumentEntity) {
        updatedDocuments += document
    }

    override suspend fun deleteDocument(
        id: String,
        timestamp: Long,
    ) = Unit

    override suspend fun updateTargetFormat(
        artifactId: String,
        targetFormat: MimeType,
        timestamp: Long,
    ) = Unit

    override suspend fun softDeleteByMessageId(
        messageId: MessageId,
        timestamp: Long,
    ) = Unit

    override suspend fun softDeleteByChatId(
        chatId: ChatId,
        timestamp: Long,
    ) = Unit

    override suspend fun claimPending() = Unit

    override suspend fun getClaimedForSync(): List<DocumentEntity> = emptyList()

    override suspend fun markSynced(ids: List<String>) = Unit

    override suspend fun getAllIds(): List<String> = emptyList()

    override suspend fun deleteBlockEmbedding(query: androidx.sqlite.db.SupportSQLiteQuery): Int = 0

    override suspend fun insertBlockEmbedding(query: androidx.sqlite.db.SupportSQLiteQuery): Long = 0L

    override suspend fun searchSimilarBlocks(query: androidx.sqlite.db.SupportSQLiteQuery): List<String> {
        searchSimilarBlocksQueries += query
        return searchSimilarBlocksResult
    }
}

class FakeEmbeddingDao : EmbeddingDao {
    override suspend fun searchSimilarMessages(query: androidx.sqlite.db.SupportSQLiteQuery): List<String> = emptyList()

    override suspend fun searchMemoryEmbeddingMatches(query: androidx.sqlite.db.SupportSQLiteQuery): List<MemoryEmbeddingMatch> =
        emptyList()

    override suspend fun insertEmbedding(query: androidx.sqlite.db.SupportSQLiteQuery): Long = 0L

    override suspend fun getEmbeddingMetadata(messageId: String): EmbeddingEntity? = null

    override suspend fun insertEmbeddingMetadata(entity: EmbeddingEntity) = Unit

    override suspend fun deleteEmbeddingMetadata(messageId: String) = Unit
}

class FakeLocalModelConfigurationsDao : LocalModelConfigurationsDao {
    var claimedForSyncResult: List<LocalModelConfigurationEntity> = emptyList()

    override suspend fun getAllForAsset(localModelId: LocalModelId): List<LocalModelConfigurationEntity> = emptyList()

    override suspend fun getById(id: LocalModelConfigurationId): LocalModelConfigurationEntity? = null

    override suspend fun getByModelIdAndName(
        localModelId: LocalModelId,
        displayName: String,
    ): LocalModelConfigurationEntity? = null

    override suspend fun upsert(entity: LocalModelConfigurationEntity) = Unit

    override suspend fun softDeleteById(
        id: LocalModelConfigurationId,
        timestamp: Long,
    ) = Unit

    override suspend fun purgeDeleted(ids: List<LocalModelConfigurationId>) = Unit

    override suspend fun deleteAllForAsset(
        localModelId: LocalModelId,
        timestamp: Long,
    ) = Unit

    override suspend fun claimPending() = Unit

    override suspend fun getClaimedForSync(): List<LocalModelConfigurationEntity> = claimedForSyncResult

    override suspend fun markSynced(ids: List<LocalModelConfigurationId>) = Unit
}

class FakeLocalModelsDao : LocalModelsDao {
    var claimedForSyncResult: List<LocalModelEntity> = emptyList()

    override suspend fun getById(id: LocalModelId): LocalModelEntity? = null

    override suspend fun getBySha256(sha256: String): LocalModelEntity? = null

    override suspend fun getAll(): List<LocalModelEntity> = emptyList()

    override suspend fun getAllActive(): List<LocalModelEntity> = emptyList()

    override fun observeAllActive(): Flow<List<LocalModelEntity>> = flowOf(emptyList())

    override suspend fun getSoftDeletedModels(): List<LocalModelEntity> = emptyList()

    override suspend fun upsert(entity: LocalModelEntity): Long = 1L

    override suspend fun softDeleteById(
        id: LocalModelId,
        timestamp: Long,
    ) = Unit

    override suspend fun purgeDeleted(ids: List<LocalModelId>) = Unit

    override suspend fun claimPending() = Unit

    override suspend fun getClaimedForSync(): List<LocalModelEntity> = claimedForSyncResult

    override suspend fun markSynced(ids: List<LocalModelId>) = Unit
}

class FakeMemoriesDao : MemoriesDao {
    var claimedForSyncResult: List<MemoriesEntity> = emptyList()

    override suspend fun insertMemory(memory: MemoriesEntity) = Unit

    override suspend fun updateMemory(memory: MemoriesEntity) = Unit

    override suspend fun softDeleteById(
        id: String,
        timestamp: Long,
    ) = Unit

    override suspend fun purgeDeleted(ids: List<String>) = Unit

    override suspend fun getMemoryById(id: String): MemoriesEntity? = null

    override suspend fun getMemoriesByCategories(categories: List<MemoryCategory>): List<MemoriesEntity> = emptyList()

    override fun getAllMemoriesFlow(): Flow<List<MemoriesEntity>> = flowOf(emptyList())

    override suspend fun searchMemoriesRaw(query: androidx.sqlite.db.SupportSQLiteQuery): List<MemoriesEntity> = emptyList()

    override suspend fun claimPending() = Unit

    override suspend fun getClaimedForSync(): List<MemoriesEntity> = claimedForSyncResult

    override suspend fun markSynced(ids: List<String>) = Unit

    override suspend fun getAllIds(): List<String> = emptyList()
}

class FakeMessageFileDao : MessageFileDao {
    var claimedForSyncResult: List<MessageFileEntity> = emptyList()

    override suspend fun insert(entity: MessageFileEntity) = Unit

    override suspend fun getById(id: String): MessageFileEntity? = null

    override suspend fun getByIdIncludingDeleted(id: String): MessageFileEntity? = null

    override suspend fun getByMessageId(messageId: MessageId): List<MessageFileEntity> = emptyList()

    override suspend fun softDeleteById(
        id: String,
        timestamp: Long,
    ) = Unit

    override suspend fun getPendingUploads(): List<MessageFileEntity> = emptyList()

    override suspend fun getPendingDeletions(): List<MessageFileEntity> = emptyList()

    override suspend fun markCloudDeleted(
        id: String,
        timestamp: Long,
    ) = Unit

    override suspend fun updateStatus(
        id: String,
        status: String,
        timestamp: Long,
    ) = Unit

    override suspend fun updateStagingUrl(
        id: String,
        url: String?,
    ) = Unit

    override suspend fun markUploaded(
        id: String,
        remoteUrl: String,
        timestamp: Long,
    ) = Unit

    override suspend fun purgeDeleted(ids: List<String>) = Unit

    override suspend fun softDeleteByMessageId(
        messageId: MessageId,
        timestamp: Long,
    ) = Unit

    override suspend fun softDeleteByChatId(
        chatId: ChatId,
        timestamp: Long,
    ) = Unit

    override suspend fun claimPending() = Unit

    override suspend fun getClaimedForSync(): List<MessageFileEntity> = claimedForSyncResult

    override suspend fun markSynced(ids: List<String>) = Unit
}

class FakeMessageVisionAnalysisDao : MessageVisionAnalysisDao {
    var claimedForSyncResult: List<MessageVisionAnalysisEntity> = emptyList()

    override suspend fun upsert(entity: MessageVisionAnalysisEntity) = Unit

    override suspend fun getById(id: String): MessageVisionAnalysisEntity? = null

    override suspend fun softDeleteById(
        id: String,
        timestamp: Long,
    ) = Unit

    override suspend fun purgeDeleted(ids: List<String>) = Unit

    override suspend fun getByUserMessageIds(userMessageIds: List<MessageId>): List<MessageVisionAnalysisEntity> = emptyList()

    override suspend fun claimPending() = Unit

    override suspend fun getClaimedForSync(): List<MessageVisionAnalysisEntity> = claimedForSyncResult

    override suspend fun markSynced(ids: List<String>) = Unit
}

class FakeStudioAlbumDao : StudioAlbumDao {
    var claimedForSyncResult: List<StudioAlbumEntity> = emptyList()

    override fun observeAllAlbums(): Flow<List<StudioAlbumEntity>> = flowOf(emptyList())

    override suspend fun insertAlbum(album: StudioAlbumEntity) = Unit

    override suspend fun updateAlbumName(
        id: String,
        name: String,
        timestamp: Long,
    ) = Unit

    override suspend fun getAlbumById(id: String): StudioAlbumEntity? = null

    override suspend fun softDeleteById(
        id: String,
        timestamp: Long,
    ) = Unit

    override suspend fun purgeDeleted(ids: List<String>) = Unit

    override suspend fun claimPending() = Unit

    override suspend fun getClaimedForSync(): List<StudioAlbumEntity> = claimedForSyncResult

    override suspend fun markSynced(ids: List<String>) = Unit
}

class FakeStudioMediaDao : StudioMediaDao {
    var claimedForSyncResult: List<StudioMediaEntity> = emptyList()
    val insertMediaCalls = mutableListOf<StudioMediaEntity>()

    override fun getAllMedia(): Flow<List<StudioMediaEntity>> = flowOf(emptyList())

    override suspend fun insertMedia(media: StudioMediaEntity) {
        insertMediaCalls += media
    }

    override suspend fun softDeleteById(
        id: String,
        timestamp: Long,
    ) = Unit

    override suspend fun purgeDeleted(ids: List<String>) = Unit

    override suspend fun getMediaById(id: String): StudioMediaEntity? = null

    override suspend fun updateMediaAlbum(
        mediaIds: List<String>,
        albumId: String?,
        timestamp: Long,
    ) = Unit

    override suspend fun reassignMediaToDefault(
        albumId: String,
        timestamp: Long,
    ) = Unit

    override suspend fun claimPending() = Unit

    override suspend fun getClaimedForSync(): List<StudioMediaEntity> = claimedForSyncResult

    override suspend fun markSynced(ids: List<String>) = Unit

    override suspend fun getPendingUploads(): List<StudioMediaEntity> = emptyList()

    override suspend fun getUploaded(): List<StudioMediaEntity> = emptyList()

    override suspend fun getPendingDeletions(): List<StudioMediaEntity> = emptyList()

    override suspend fun markCloudDeleted(id: String) = Unit

    override suspend fun updateStagingUrl(
        id: String,
        url: String?,
    ) = Unit

    override suspend fun markUploaded(
        id: String,
        remoteUrl: String,
        updatedAt: Long,
    ) = Unit

    override suspend fun markUploadFailed(
        id: String,
        updatedAt: Long,
    ) = Unit
}

class FakeTtsProviderDao : TtsProviderDao {
    var claimedForSyncResult: List<TtsProviderEntity> = emptyList()

    override fun getTtsProviders(): Flow<List<TtsProviderEntity>> = flowOf(emptyList())

    override suspend fun getTtsProvidersSync(): List<TtsProviderEntity> = emptyList()

    override suspend fun getTtsProvider(id: String): TtsProviderEntity? = null

    override suspend fun upsertTtsProvider(entity: TtsProviderEntity) = Unit

    override suspend fun softDeleteById(
        id: String,
        timestamp: Long,
    ) = Unit

    override suspend fun purgeDeleted(ids: List<String>) = Unit

    override suspend fun claimPending() = Unit

    override suspend fun getClaimedForSync(): List<TtsProviderEntity> = claimedForSyncResult

    override suspend fun markSynced(ids: List<String>) = Unit
}

class FakeLiveVoiceProviderDao : LiveVoiceProviderDao {
    var claimedForSyncResult: List<LiveVoiceProviderEntity> = emptyList()

    override fun getLiveVoiceProviders(): Flow<List<LiveVoiceProviderEntity>> = flowOf(emptyList())

    override suspend fun getLiveVoiceProvidersSync(): List<LiveVoiceProviderEntity> = emptyList()

    override suspend fun getLiveVoiceProvider(id: String): LiveVoiceProviderEntity? = null

    override suspend fun upsertLiveVoiceProvider(entity: LiveVoiceProviderEntity) = Unit

    override suspend fun softDeleteById(
        id: String,
        timestamp: Long,
    ) = Unit

    override suspend fun purgeDeleted(ids: List<String>) = Unit

    override suspend fun claimPending() = Unit

    override suspend fun getClaimedForSync(): List<LiveVoiceProviderEntity> = claimedForSyncResult

    override suspend fun markSynced(ids: List<String>) = Unit
}

class FakeUtilityModelsDao : UtilityModelsDao {
    var claimedForSyncResult: List<UtilityModelEntity> = emptyList()

    override suspend fun getById(id: UtilityModelId): UtilityModelEntity? = null

    override suspend fun getByUtilityType(utilityType: UtilityType): UtilityModelEntity? = null

    override suspend fun getBySha256(sha256: String): UtilityModelEntity? = null

    override suspend fun getAll(): List<UtilityModelEntity> = emptyList()

    override fun observeAllActive(): Flow<List<UtilityModelEntity>> = flowOf(emptyList())

    override suspend fun upsert(entity: UtilityModelEntity): Long = 1L

    override suspend fun softDeleteById(
        id: UtilityModelId,
        timestamp: Long,
    ) = Unit

    override suspend fun claimPending() = Unit

    override suspend fun getClaimedForSync(): List<UtilityModelEntity> = claimedForSyncResult

    override suspend fun markSynced(ids: List<UtilityModelId>) = Unit
}

class FakeWebSearchSourceDao : WebSearchSourceDao {
    var claimedForSyncResult: List<WebSearchSourceEntity> = emptyList()

    override suspend fun insertAll(sources: List<WebSearchSourceEntity>) = Unit

    override suspend fun upsert(entity: WebSearchSourceEntity) = Unit

    override suspend fun getById(id: String): WebSearchSourceEntity? = null

    override suspend fun softDeleteById(
        id: String,
        timestamp: Long,
    ) = Unit

    override suspend fun purgeDeleted(ids: List<String>) = Unit

    override suspend fun getByMessageId(messageId: MessageId): List<WebSearchSourceEntity> = emptyList()

    override fun getByChatIdFlow(chatId: ChatId): Flow<List<WebSearchSourceEntity>> = flowOf(emptyList())

    override suspend fun markExtracted(
        url: String,
        timestamp: Long,
    ) = Unit

    override suspend fun claimPending() = Unit

    override suspend fun getClaimedForSync(): List<WebSearchSourceEntity> = claimedForSyncResult

    override suspend fun markSynced(ids: List<String>) = Unit
}

class FakeApiCredentialsDao : ApiCredentialsDao {
    var claimedForSyncResult: List<com.browntowndev.enclave.core.data.local.ApiCredentialsEntity> = emptyList()
    val markSyncedCalls = mutableListOf<List<ApiCredentialsId>>()

    override fun observeAll(): Flow<List<com.browntowndev.enclave.core.data.local.ApiCredentialsEntity>> = flowOf(emptyList())

    override suspend fun getAll(): List<com.browntowndev.enclave.core.data.local.ApiCredentialsEntity> = emptyList()

    override suspend fun getById(id: ApiCredentialsId): com.browntowndev.enclave.core.data.local.ApiCredentialsEntity? = null

    override suspend fun getByCredentialAlias(credentialAlias: String): com.browntowndev.enclave.core.data.local.ApiCredentialsEntity? =
        null

    override suspend fun getByApiKeySignature(signature: String): com.browntowndev.enclave.core.data.local.ApiCredentialsEntity? =
        null

    override suspend fun getAllCredentialAliases(): List<String> = emptyList()

    override suspend fun releaseDeletedIdentityLocks(
        credentialAlias: String,
        apiKeySignature: String?,
    ) = Unit

    override suspend fun insert(entity: com.browntowndev.enclave.core.data.local.ApiCredentialsEntity): Long = 1L

    override suspend fun update(entity: com.browntowndev.enclave.core.data.local.ApiCredentialsEntity) = Unit

    override suspend fun upsert(entity: com.browntowndev.enclave.core.data.local.ApiCredentialsEntity): Long = 1L

    override suspend fun softDeleteById(
        id: ApiCredentialsId,
        timestamp: Long,
    ) = Unit

    override suspend fun purgeDeleted(ids: List<ApiCredentialsId>) = Unit

    override suspend fun claimPending() = Unit

    override suspend fun getClaimedForSync(): List<com.browntowndev.enclave.core.data.local.ApiCredentialsEntity> =
        claimedForSyncResult

    override suspend fun markSynced(ids: List<ApiCredentialsId>) {
        markSyncedCalls += ids
    }
}

class FakeApiModelConfigurationsDao : ApiModelConfigurationsDao {
    var claimedForSyncResult: List<com.browntowndev.enclave.core.data.local.ApiModelConfigurationEntity> = emptyList()

    override fun observeAll(): Flow<List<com.browntowndev.enclave.core.data.local.ApiModelConfigurationEntity>> =
        flowOf(emptyList())

    override suspend fun getAllForCredentials(
        credentialsId: ApiCredentialsId,
    ): List<com.browntowndev.enclave.core.data.local.ApiModelConfigurationEntity> = emptyList()

    override suspend fun getById(
        id: ApiModelConfigurationId,
    ): com.browntowndev.enclave.core.data.local.ApiModelConfigurationEntity? = null

    override suspend fun upsert(entity: com.browntowndev.enclave.core.data.local.ApiModelConfigurationEntity) = Unit

    override suspend fun softDeleteById(
        id: ApiModelConfigurationId,
        timestamp: Long,
    ) = Unit

    override suspend fun purgeDeleted(ids: List<ApiModelConfigurationId>) = Unit

    override suspend fun deleteAllForCredentials(
        credentialsId: ApiCredentialsId,
        timestamp: Long,
    ) = Unit

    override suspend fun claimPending() = Unit

    override suspend fun getClaimedForSync(): List<com.browntowndev.enclave.core.data.local.ApiModelConfigurationEntity> =
        claimedForSyncResult

    override suspend fun markSynced(ids: List<ApiModelConfigurationId>) = Unit
}

class FakeDefaultModelsDao : DefaultModelsDao {
    var claimedForSyncResult: List<DefaultModelEntity> = emptyList()

    override suspend fun getDefault(modelType: ModelType): DefaultModelEntity? = null

    override fun observeAll(): Flow<List<DefaultModelEntity>> = flowOf(emptyList())

    override fun observeAllWithDetails(): Flow<List<DefaultModelAssignmentView>> = flowOf(emptyList())

    override suspend fun getAll(): List<DefaultModelEntity> = emptyList()

    override suspend fun upsert(entity: DefaultModelEntity) = Unit

    override suspend fun softDeleteById(
        modelType: ModelType,
        timestamp: Long,
    ) = Unit

    override suspend fun purgeDeleted(modelTypes: List<ModelType>) = Unit

    override suspend fun claimPending() = Unit

    override suspend fun getClaimedForSync(): List<DefaultModelEntity> = claimedForSyncResult

    override suspend fun markSynced(modelTypes: List<ModelType>) = Unit
}

class FakeMediaProviderDao : MediaProviderDao {
    var observeAllResult: Flow<List<MediaProviderEntity>> = flowOf(emptyList())
    val upsertCalls = mutableListOf<MediaProviderEntity>()
    var claimedForSyncResult: List<MediaProviderEntity> = emptyList()

    override suspend fun getMediaProvider(id: String): MediaProviderEntity? = null

    override fun observeAllMediaProviders(): Flow<List<MediaProviderEntity>> = observeAllResult

    override suspend fun upsert(entity: MediaProviderEntity) {
        upsertCalls += entity
    }

    override suspend fun softDeleteById(
        id: String,
        timestamp: Long,
    ) = Unit

    override suspend fun purgeDeleted(ids: List<String>) = Unit

    override suspend fun claimPending() = Unit

    override suspend fun getClaimedForSync(): List<MediaProviderEntity> = claimedForSyncResult

    override suspend fun markSynced(ids: List<String>) = Unit
}

class FakeMessageQueryDao : MessageQueryDao {
    var claimedForSyncResult: List<MessageWithFiles> = emptyList()
    var messageByIdResult: MessageWithFiles? = null

    override suspend fun getMessageChatIdById(id: MessageId): ChatId? = null

    override suspend fun getMessageById(id: MessageId): MessageWithFiles? = messageByIdResult

    override fun getMessages(ids: List<MessageId>): Flow<List<MessageWithFiles>> = flowOf(emptyList())

    override suspend fun getMessagesForChat(
        chatId: ChatId,
        ids: List<MessageId>,
    ): List<MessageWithFiles> = emptyList()

    override fun getAllMessages(): Flow<List<MessageWithFiles>> = flowOf(emptyList())

    override suspend fun getMessagesByChatId(chatId: ChatId): List<MessageWithFiles> = emptyList()

    override suspend fun getMessagesBefore(
        chatId: ChatId,
        timestamp: Long,
        limit: Int,
    ): List<MessageWithFiles> = emptyList()

    override suspend fun getMessagesAfter(
        chatId: ChatId,
        timestamp: Long,
        limit: Int,
    ): List<MessageWithFiles> = emptyList()

    override fun getMessagesByChatIdFlow(chatId: ChatId): Flow<List<MessageWithFiles>> = flowOf(emptyList())

    override suspend fun getMessagesByStates(
        chatId: ChatId,
        states: List<MessageState>,
    ): List<MessageWithFiles> = emptyList()

    override suspend fun getClaimedForSync(): List<MessageWithFiles> = claimedForSyncResult

    override suspend fun getAllIds(): List<MessageId> = emptyList()
}

class FakeMessageCommandDao : MessageCommandDao {
    val upsertCalls = mutableListOf<MessageEntity>()
    val updateCalls = mutableListOf<MessageEntity>()

    override suspend fun updateMessageContent(
        id: MessageId,
        content: String,
        thinkingDuration: Int?,
        thinkingRaw: String?,
        timestamp: Long,
    ) = Unit

    override suspend fun updateMessageState(
        id: MessageId,
        messageState: MessageState,
        timestamp: Long,
    ) = Unit

    override suspend fun updateMessageCreatedAt(
        id: MessageId,
        timestamp: Long,
        syncTimestamp: Long,
    ) = Unit

    override suspend fun updateMessageContentText(
        id: MessageId,
        content: String,
        timestamp: Long,
    ) = Unit

    override suspend fun updateMessageModelType(
        id: MessageId,
        modelType: ModelType,
        timestamp: Long,
    ) = Unit

    override suspend fun updateThinkingDuration(
        id: MessageId,
        thinkingDuration: Int,
        timestamp: Long,
    ) = Unit

    override suspend fun appendMessageContentAtomic(
        id: MessageId,
        suffix: String,
        timestamp: Long,
    ) = Unit

    override suspend fun appendThinkingRawAtomic(
        id: MessageId,
        suffix: String,
        timestamp: Long,
    ) = Unit

    override suspend fun upsert(messageEntity: MessageEntity): Long {
        upsertCalls += messageEntity
        return 1L
    }

    override suspend fun insertIfAbsent(messageEntity: MessageEntity): Long = 1L

    override suspend fun insertFiles(files: List<MessageFileEntity>) = Unit

    override suspend fun update(messageEntity: MessageEntity) {
        updateCalls += messageEntity
    }

    override suspend fun softDeleteById(
        id: MessageId,
        timestamp: Long,
    ) = Unit

    override suspend fun purgeDeleted(ids: List<MessageId>) = Unit

    override suspend fun updateThinkingStartTime(
        messageId: MessageId,
        startTime: Long,
        timestamp: Long,
    ) = Unit

    override suspend fun updateThinkingEndTime(
        messageId: MessageId,
        endTime: Long,
        timestamp: Long,
    ) = Unit

    override suspend fun updateThinkingRaw(
        messageId: MessageId,
        thinkingRaw: String?,
        timestamp: Long,
    ) = Unit

    override suspend fun updateMessagePipelineStep(
        messageId: MessageId,
        pipelineStep: PipelineStep?,
        timestamp: Long,
    ) = Unit

    override suspend fun updateMessageMedia(
        id: MessageId,
        mediaUrisJson: String?,
        mediaAssetIdsJson: String?,
        mediaTypesJson: String?,
        media_prompts_json: String?,
        timestamp: Long,
    ) = Unit
}

class FakeMessageSyncCommandDao : MessageSyncCommandDao {
    val markSyncedCalls = mutableListOf<List<MessageId>>()

    override suspend fun softDeleteAllActiveByChatId(
        chatId: ChatId,
        timestamp: Long,
    ) = Unit

    override suspend fun claimPending() = Unit

    override suspend fun markSynced(ids: List<MessageId>) {
        markSyncedCalls += ids
    }

    override suspend fun insertWebSearchSources(sources: List<WebSearchSourceEntity>) = Unit

    override suspend fun updateMessageArtifacts(
        id: MessageId,
        artifactsJson: String?,
        timestamp: Long,
    ) = Unit
}

/**
 * A real composite [MessageDao] wired over fake sub-DAOs, mirroring
 * `MessageDaoTestFactory` but without a Room database. `persistAllMessageData`
 * runs through a [MessageDaoPersist] over [FakeEnclaveDatabase], whose
 * transaction support throws if actually exercised.
 */
class FakeMessageDaos(
    database: EnclaveDatabase = FakeEnclaveDatabase(),
) {
    val query = FakeMessageQueryDao()
    val command = FakeMessageCommandDao()
    val sync = FakeMessageSyncCommandDao()
    val dao: MessageDao =
        MessageDao(
            query = query,
            command = command,
            syncCommand = sync,
            persist = MessageDaoPersist(database, command, sync),
        )
}

class FakeProfileCorpusDao : ProfileCorpusDao {
    var selectedConversationsResult: List<ProfileCorpusConversationEntity> = emptyList()
    val messagesForConversation = mutableMapOf<String, List<ProfileCorpusMessageEntity>>()
    val messagesPageResults = mutableMapOf<Triple<String, Int, Int>, List<ProfileCorpusMessageEntity>>()
    var searchSelectedResult: List<ProfileCorpusConversationEntity> = emptyList()
    val deleteMessagesForRunCalls = mutableListOf<String>()
    val deleteConversationsForRunCalls = mutableListOf<String>()

    override suspend fun insertConversation(entity: ProfileCorpusConversationEntity) = Unit

    override suspend fun insertConversations(entities: List<ProfileCorpusConversationEntity>) = Unit

    override suspend fun insertMessages(entities: List<ProfileCorpusMessageEntity>) = Unit

    override suspend fun getConversationsForRun(runId: String): List<ProfileCorpusConversationEntity> = emptyList()

    override suspend fun getSelectedConversations(runId: String): List<ProfileCorpusConversationEntity> =
        selectedConversationsResult

    override suspend fun getMessagesForConversation(conversationId: String): List<ProfileCorpusMessageEntity> =
        messagesForConversation[conversationId] ?: emptyList()

    override suspend fun getConversationCount(runId: String): Int = 0

    override suspend fun getTotalSelectedUserTokens(runId: String): Int? = null

    override suspend fun updateConversation(entity: ProfileCorpusConversationEntity) = Unit

    override suspend fun updateSignalScore(
        id: String,
        score: Float,
    ) = Unit

    override suspend fun updateClusterId(
        id: String,
        clusterId: Int,
    ) = Unit

    override suspend fun updateSelected(
        id: String,
        selected: Boolean,
    ) = Unit

    override suspend fun deleteConversationsForRun(runId: String) {
        deleteConversationsForRunCalls += runId
    }

    override suspend fun deleteMessagesForRun(runId: String) {
        deleteMessagesForRunCalls += runId
    }

    override suspend fun searchSelectedConversations(
        runId: String,
        query: String,
        limit: Int,
    ): List<ProfileCorpusConversationEntity> = searchSelectedResult

    override suspend fun getMessagesPage(
        conversationId: String,
        pageSize: Int,
        offset: Int,
    ): List<ProfileCorpusMessageEntity> = messagesPageResults[Triple(conversationId, pageSize, offset)] ?: emptyList()
}

class FakeProfileFindingsDao : ProfileFindingsDao {
    val insertCalls = mutableListOf<ProfileFindingsEntity>()
    var getAllResult: List<ProfileFindingsEntity> = emptyList()
    val getByCategoryResults = mutableMapOf<String, List<ProfileFindingsEntity>>()
    val getByCategoryCalls = mutableListOf<Pair<String, String>>()
    val deleteAllCalls = mutableListOf<String>()

    override suspend fun insert(entity: ProfileFindingsEntity): Long {
        insertCalls += entity
        return 1L
    }

    override suspend fun getAll(runId: String): List<ProfileFindingsEntity> = getAllResult

    override suspend fun getByCategory(
        runId: String,
        category: String,
    ): List<ProfileFindingsEntity> {
        getByCategoryCalls += runId to category
        return getByCategoryResults[category] ?: emptyList()
    }

    override suspend fun deleteAll(runId: String) {
        deleteAllCalls += runId
    }
}

class FakeProfileAnalysisRunDao : ProfileAnalysisRunDao {
    var byRunIdResult: ProfileAnalysisRunEntity? = null
    val upsertCalls = mutableListOf<ProfileAnalysisRunEntity>()
    val deleteCalls = mutableListOf<String>()

    override suspend fun upsert(entity: ProfileAnalysisRunEntity) {
        upsertCalls += entity
    }

    override suspend fun getByRunId(runId: String): ProfileAnalysisRunEntity? = byRunIdResult

    override suspend fun delete(runId: String) {
        deleteCalls += runId
    }
}

class FakeTelemetryEventDao : TelemetryEventDao() {
    val insertedEvents = mutableListOf<TelemetryEventEntity>()
    val markUploadedCalls = mutableListOf<List<String>>()
    val evictOldestCalls = mutableListOf<Int>()
    var deleteUploadedCallCount = 0
    var countResult: Int? = null
    var insertFailure: Throwable? = null
    val pendingUploadsQueue = ArrayDeque<List<TelemetryEventEntity>>()
    var pendingUploadsResult: List<TelemetryEventEntity> = emptyList()

    override suspend fun insert(event: TelemetryEventEntity) {
        insertFailure?.let { throw it }
        insertedEvents += event
    }

    override suspend fun getPendingUploads(limit: Int): List<TelemetryEventEntity> =
        if (pendingUploadsQueue.isNotEmpty()) pendingUploadsQueue.removeFirst() else pendingUploadsResult

    override suspend fun markUploaded(ids: List<String>) {
        markUploadedCalls += ids
    }

    override suspend fun evictOldest(count: Int) {
        evictOldestCalls += count
    }

    override suspend fun getCount(): Int = countResult ?: insertedEvents.size

    override suspend fun deleteUploaded() {
        deleteUploadedCallCount++
    }
}

class FakeCleanupDao : CleanupDao {
    override suspend fun deleteChats() = Unit

    override suspend fun deleteMessages() = Unit

    override suspend fun deleteMessageTranscriptSegments() = Unit

    override suspend fun deleteMessageInferenceTelemetry() = Unit

    override suspend fun deleteChatSummaries() = Unit

    override suspend fun deleteMessageFiles() = Unit

    override suspend fun deleteDocuments() = Unit

    override suspend fun deleteVisionAnalysis() = Unit

    override suspend fun deleteMemories() = Unit

    override suspend fun deleteStudioMedia() = Unit

    override suspend fun deleteStudioAlbums() = Unit

    override suspend fun deleteApiCredentials() = Unit

    override suspend fun deleteApiModelConfigurations() = Unit

    override suspend fun deleteAllDefaultModels() = Unit

    override suspend fun deleteLocalModelConfigurations() = Unit

    override suspend fun deleteLocalModels() = Unit

    override suspend fun deleteUtilityModels() = Unit

    override suspend fun deleteTtsProviders() = Unit

    override suspend fun deleteMediaProviders() = Unit

    override suspend fun deleteEmbeddings() = Unit

    override suspend fun deleteMemoryEmbeddings(query: androidx.sqlite.db.SupportSQLiteQuery): Long = 0L

    override suspend fun deleteDocumentEmbeddings(query: androidx.sqlite.db.SupportSQLiteQuery): Long = 0L

    override suspend fun deleteArtifactBlockEmbeddings(query: androidx.sqlite.db.SupportSQLiteQuery): Long = 0L

    override suspend fun deleteWebSearchSources() = Unit
}

class FakeMessageCommittedInferencePromptDao : MessageCommittedInferencePromptDao {
    override fun insertOrUpdate(entity: MessageCommittedInferencePromptEntity) = Unit

    override fun getByMessageId(messageId: MessageId): MessageCommittedInferencePromptEntity? = null

    override fun deleteByMessageId(messageId: MessageId) = Unit
}

/**
 * [EnclaveDatabase] subclass returning the hand-written DAO fakes. There is no
 * SQLite backing: opening a real connection throws, which only matters if a
 * test drives `withTransaction` through a default DAO (tests that exercise
 * persistence use a real in-memory database instead).
 */
class FakeEnclaveDatabase : EnclaveDatabase() {
    private val fakeChatDao = FakeChatDao()
    private val fakeMessageQueryDao = FakeMessageQueryDao()
    private val fakeMessageCommandDao = FakeMessageCommandDao()
    private val fakeMessageSyncCommandDao = FakeMessageSyncCommandDao()
    private val fakeChatSummaryDao = FakeChatSummaryDao()
    private val fakeLocalModelsDao = FakeLocalModelsDao()
    private val fakeLocalModelConfigurationsDao = FakeLocalModelConfigurationsDao()
    private val fakeApiModelConfigurationsDao = FakeApiModelConfigurationsDao()
    private val fakeApiCredentialsDao = FakeApiCredentialsDao()
    private val fakeDefaultModelsDao = FakeDefaultModelsDao()
    private val fakeWebSearchSourceDao = FakeWebSearchSourceDao()
    private val fakeMessageVisionAnalysisDao = FakeMessageVisionAnalysisDao()
    private val fakeEmbeddingDao = FakeEmbeddingDao()
    private val fakeTtsProviderDao = FakeTtsProviderDao()
    private val fakeLiveVoiceProviderDao = FakeLiveVoiceProviderDao()
    private val fakeMemoriesDao = FakeMemoriesDao()
    private val fakeMediaProviderDao = FakeMediaProviderDao()
    private val fakeStudioMediaDao = FakeStudioMediaDao()
    private val fakeStudioAlbumDao = FakeStudioAlbumDao()
    private val fakeMessageFileDao = FakeMessageFileDao()
    private val fakeMessageTranscriptSegmentDao = FakeMessageTranscriptSegmentDao()
    private val fakeCleanupDao = FakeCleanupDao()
    private val fakeDocumentDao = FakeDocumentDao()
    private val fakeUtilityModelsDao = FakeUtilityModelsDao()
    private val fakeMessageInferenceTelemetryDao = FakeMessageInferenceTelemetryDao()
    private val fakeFirestoreSyncPlaceholderDao = FakeFirestoreSyncPlaceholderDao()
    private val fakeMessageCommittedInferencePromptDao = FakeMessageCommittedInferencePromptDao()
    private val fakeTelemetryEventDao = FakeTelemetryEventDao()
    private val fakeProfileCorpusDao = FakeProfileCorpusDao()
    private val fakeProfileFindingsDao = FakeProfileFindingsDao()
    private val fakeProfileAnalysisRunDao = FakeProfileAnalysisRunDao()

    override fun chatDao(): ChatDao = fakeChatDao

    override fun messageQueryDao(): MessageQueryDao = fakeMessageQueryDao

    override fun messageCommandDao(): MessageCommandDao = fakeMessageCommandDao

    override fun messageSyncCommandDao(): MessageSyncCommandDao = fakeMessageSyncCommandDao

    override fun chatSummaryDao(): ChatSummaryDao = fakeChatSummaryDao

    override fun localModelsDao(): LocalModelsDao = fakeLocalModelsDao

    override fun localModelConfigurationsDao(): LocalModelConfigurationsDao = fakeLocalModelConfigurationsDao

    override fun apiModelConfigurationsDao(): ApiModelConfigurationsDao = fakeApiModelConfigurationsDao

    override fun apiCredentialsDao(): ApiCredentialsDao = fakeApiCredentialsDao

    override fun defaultModelsDao(): DefaultModelsDao = fakeDefaultModelsDao

    override fun webSearchSourceDao(): WebSearchSourceDao = fakeWebSearchSourceDao

    override fun messageVisionAnalysisDao(): MessageVisionAnalysisDao = fakeMessageVisionAnalysisDao

    override fun embeddingDao(): EmbeddingDao = fakeEmbeddingDao

    override fun ttsProviderDao(): TtsProviderDao = fakeTtsProviderDao

    override fun liveVoiceProviderDao(): LiveVoiceProviderDao = fakeLiveVoiceProviderDao

    override fun memoriesDao(): MemoriesDao = fakeMemoriesDao

    override fun mediaProviderDao(): MediaProviderDao = fakeMediaProviderDao

    override fun studioMediaDao(): StudioMediaDao = fakeStudioMediaDao

    override fun studioAlbumDao(): StudioAlbumDao = fakeStudioAlbumDao

    override fun messageFileDao(): MessageFileDao = fakeMessageFileDao

    override fun messageTranscriptSegmentDao(): MessageTranscriptSegmentDao = fakeMessageTranscriptSegmentDao

    override fun cleanupDao(): CleanupDao = fakeCleanupDao

    override fun documentDao(): DocumentDao = fakeDocumentDao

    override fun utilityModelsDao(): UtilityModelsDao = fakeUtilityModelsDao

    override fun messageInferenceTelemetryDao(): MessageInferenceTelemetryDao = fakeMessageInferenceTelemetryDao

    override fun firestoreSyncPlaceholderDao(): FirestoreSyncPlaceholderDao = fakeFirestoreSyncPlaceholderDao

    override fun messageCommittedInferencePromptDao(): MessageCommittedInferencePromptDao = fakeMessageCommittedInferencePromptDao

    override fun telemetryEventDao(): TelemetryEventDao = fakeTelemetryEventDao

    override fun profileCorpusDao(): ProfileCorpusDao = fakeProfileCorpusDao

    override fun profileFindingsDao(): ProfileFindingsDao = fakeProfileFindingsDao

    override fun profileAnalysisRunDao(): ProfileAnalysisRunDao = fakeProfileAnalysisRunDao

    override fun createInvalidationTracker(): InvalidationTracker = InvalidationTracker(this)

    override fun createOpenHelper(config: DatabaseConfiguration): SupportSQLiteOpenHelper =
        throw UnsupportedOperationException("FakeEnclaveDatabase has no SQLite backing")

    override fun clearAllTables() = Unit
}
