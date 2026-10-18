package com.browntowndev.enclave.core.data.sync

import android.content.Context
import androidx.sqlite.db.SupportSQLiteQuery
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.ForegroundUpdater
import androidx.work.ListenableWorker
import androidx.work.ProgressUpdater
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.impl.utils.taskexecutor.WorkManagerTaskExecutor
import com.browntowndev.enclave.core.data.local.DocumentDao
import com.browntowndev.enclave.core.data.local.DocumentEntity
import com.browntowndev.enclave.core.data.local.EmbeddingDao
import com.browntowndev.enclave.core.data.local.EmbeddingEntity
import com.browntowndev.enclave.core.data.local.MemoryEmbeddingMatch
import com.browntowndev.enclave.domain.model.artifact.MimeType
import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.util.UUID
import java.util.concurrent.Executor

/**
 * In-memory [EmbeddingDao] fake (ENC-28). The vec0 virtual-table raw queries cannot
 * run without the sqlite-vec native extension, so they are recorded as no-ops;
 * embedding metadata (a plain Room table in production) is kept in a real map.
 */
class FakeEmbeddingDao : EmbeddingDao {
    val metadata = mutableMapOf<String, EmbeddingEntity>()

    /** Raw-query SQL strings passed to [insertEmbedding], in call order. */
    val rawInserts = mutableListOf<String>()

    var replaceEmbeddingCallCount = 0
        private set

    override suspend fun searchSimilarMessages(query: SupportSQLiteQuery): List<String> = emptyList()

    override suspend fun searchMemoryEmbeddingMatches(query: SupportSQLiteQuery): List<MemoryEmbeddingMatch> = emptyList()

    override suspend fun insertEmbedding(query: SupportSQLiteQuery): Long {
        rawInserts += query.sql
        return 1L
    }

    override suspend fun replaceEmbedding(
        deleteQuery: SupportSQLiteQuery,
        insertQuery: SupportSQLiteQuery,
    ) {
        replaceEmbeddingCallCount++
        insertEmbedding(deleteQuery)
        insertEmbedding(insertQuery)
    }

    override suspend fun getEmbeddingMetadata(messageId: String): EmbeddingEntity? = metadata[messageId]

    override suspend fun insertEmbeddingMetadata(entity: EmbeddingEntity) {
        metadata[entity.messageId] = entity
    }

    override suspend fun deleteEmbeddingMetadata(messageId: String) {
        metadata.remove(messageId)
    }
}

/** In-memory [DocumentDao] fake (ENC-28), backed by a document map. */
class FakeDocumentDao : DocumentDao {
    val documents = mutableMapOf<String, DocumentEntity>()

    /** Raw-query SQL strings passed to the block-embedding writers, in call order. */
    val blockEmbeddingWrites = mutableListOf<String>()

    override fun getAllDocuments(): Flow<List<DocumentEntity>> =
        flowOf(documents.values.filter { !it.deleted }.sortedByDescending { it.createdAt })

    override suspend fun getDocumentById(id: String): DocumentEntity? = documents[id]?.takeIf { !it.deleted }

    override fun getDocumentsByChatIdFlow(chatId: ChatId): Flow<List<DocumentEntity>> = flowOf(emptyList())

    override fun getDocumentsByMessageIdFlow(messageId: MessageId): Flow<List<DocumentEntity>> =
        flowOf(documents.values.filter { it.messageId == messageId && !it.deleted })

    override suspend fun insertDocument(document: DocumentEntity) {
        documents[document.id] = document
    }

    override suspend fun updateDocument(document: DocumentEntity) {
        documents[document.id] = document
    }

    override suspend fun deleteDocument(id: String, timestamp: Long) {
        documents[id]?.let { documents[id] = it.copy(deleted = true, updatedAt = timestamp, pendingSync = 1) }
    }

    override suspend fun updateTargetFormat(artifactId: String, targetFormat: MimeType, timestamp: Long) {
        documents[artifactId]?.let {
            documents[artifactId] = it.copy(targetFormat = targetFormat, updatedAt = timestamp, pendingSync = 1)
        }
    }

    override suspend fun softDeleteByMessageId(messageId: MessageId, timestamp: Long) {
        documents.values
            .filter { it.messageId == messageId }
            .forEach { documents[it.id] = it.copy(deleted = true, updatedAt = timestamp, pendingSync = 1) }
    }

    override suspend fun softDeleteByChatId(chatId: ChatId, timestamp: Long) = Unit

    override suspend fun claimPending() {
        documents.values
            .filter { it.pendingSync == 1 }
            .forEach { documents[it.id] = it.copy(pendingSync = 2) }
    }

    override suspend fun getClaimedForSync(): List<DocumentEntity> = documents.values.filter { it.pendingSync == 2 }

    override suspend fun markSynced(ids: List<String>) {
        ids.forEach { id -> documents[id]?.let { documents[id] = it.copy(pendingSync = 0) } }
    }

    override suspend fun getAllIds(): List<String> = documents.keys.toList()

    override suspend fun deleteBlockEmbedding(query: SupportSQLiteQuery): Int {
        blockEmbeddingWrites += query.sql
        return 1
    }

    override suspend fun insertBlockEmbedding(query: SupportSQLiteQuery): Long {
        blockEmbeddingWrites += query.sql
        return 1L
    }

    override suspend fun searchSimilarBlocks(query: SupportSQLiteQuery): List<String> = emptyList()
}

/**
 * [com.browntowndev.enclave.domain.port.inference.EmbeddingEnginePort] fake (ENC-28)
 * with a scripted concurrency cap that tracks how many [getEmbedding] calls are
 * simultaneously in flight (each call holds for 50ms of caller-context time).
 */
class ConcurrencyTrackingEmbeddingEngine(
    override val maxConcurrentEmbeddings: Int,
) : com.browntowndev.enclave.domain.port.inference.EmbeddingEnginePort {
    private val lock = Any()
    private var activeCalls = 0

    var maxConcurrentActiveCalls = 0
        private set

    override suspend fun getEmbedding(text: String): FloatArray {
        synchronized(lock) {
            activeCalls++
            maxConcurrentActiveCalls = maxOf(maxConcurrentActiveCalls, activeCalls)
        }
        kotlinx.coroutines.delay(50)
        synchronized(lock) {
            activeCalls--
        }
        return floatArrayOf()
    }

    override suspend fun close() = Unit
}

/** [ProgressUpdater] fake (ENC-28) that records every progress payload. */
class RecordingProgressUpdater : ProgressUpdater {
    val updates = mutableListOf<Data>()

    @Suppress("UNCHECKED_CAST")
    override fun updateProgress(
        context: Context,
        id: UUID,
        data: Data,
    ): ListenableFuture<Void> {
        updates += data
        return Futures.immediateFuture(null) as ListenableFuture<Void>
    }
}

/**
 * Builds real [WorkerParameters] (ENC-28) wired to the given [ProgressUpdater],
 * replacing MockK-relaxed parameters in worker tests. CoroutineWorker.setProgress
 * routes through [progressUpdater], so progress writes stay observable.
 */
fun realWorkerParameters(
    progressUpdater: ProgressUpdater = RecordingProgressUpdater(),
    inputData: Data = Data.EMPTY,
): WorkerParameters =
    WorkerParameters(
        UUID.randomUUID(),
        inputData,
        emptyList(),
        WorkerParameters.RuntimeExtras(),
        0,
        0,
        Executor { it.run() },
        Dispatchers.Unconfined,
        WorkManagerTaskExecutor(Executor { it.run() }),
        // work-runtime 2.11 removed WorkerFactory.getDefaultWorkerFactory();
        // an inert factory that creates nothing is the equivalent filler here.
        object : WorkerFactory() {
            override fun createWorker(
                appContext: Context,
                workerClassName: String,
                workerParameters: WorkerParameters,
            ): ListenableWorker? = null
        },
        progressUpdater,
        ForegroundUpdater { _, _, _ -> Futures.immediateFuture(null) },
    )
