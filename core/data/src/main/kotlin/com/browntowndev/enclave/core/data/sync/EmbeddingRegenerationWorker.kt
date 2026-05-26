package com.browntowndev.enclave.core.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.browntowndev.enclave.core.data.local.EmbeddingDao
import com.browntowndev.enclave.core.data.local.EmbeddingEntity
import com.browntowndev.enclave.core.data.security.AadContext
import com.browntowndev.enclave.core.data.security.LocalDataEncryptionService
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.port.inference.EmbeddingEnginePort
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import com.browntowndev.enclave.domain.port.repository.MemoriesRepository
import com.browntowndev.enclave.domain.port.repository.MessageRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.Json
import java.security.MessageDigest

@HiltWorker
class EmbeddingRegenerationWorker
    @AssistedInject
    constructor(
        @Assisted context: Context,
        @Assisted params: WorkerParameters,
        private val coordinator: EmbeddingRegenerationCoordinator,
        private val messageRepository: MessageRepository,
        private val memoriesRepository: MemoriesRepository,
        private val messageDao: com.browntowndev.enclave.core.data.local.MessageDao,
        private val memoriesDao: com.browntowndev.enclave.core.data.local.MemoriesDao,
        private val documentDao: com.browntowndev.enclave.core.data.local.DocumentDao,
        private val embeddingDao: com.browntowndev.enclave.core.data.local.EmbeddingDao,
        private val embeddingEngine: EmbeddingEnginePort,
        private val progressManager: EmbeddingProgressManager,
        private val loggingPort: LoggingPort,
        private val localEncryption: LocalDataEncryptionService,
    ) : CoroutineWorker(context, params) {
    companion object {
        private const val TAG = "EmbeddingRegenerationWorker"
    }

    override suspend fun doWork(): Result {
        progressManager.setReindexing(true)
        return try {
            drainPendingBatches()
        } finally {
            progressManager.setReindexing(coordinator.hasPendingWork())
            coordinator.onWorkerFinished()
        }
    }

    private suspend fun drainPendingBatches(): Result {
        var pending = coordinator.consumePending()
        while (!pending.isEmpty()) {
            if (!runPendingBatch(pending)) {
                return Result.retry()
            }
            pending = coordinator.consumePending()
        }
        return Result.success()
    }

    private suspend fun runPendingBatch(pending: PendingWork): Boolean =
        try {
            processPendingBatch(pending)
            true
        } catch (c: CancellationException) {
            throw c
        } catch (e: Exception) {
            loggingPort.error(TAG, "Embedding regeneration failed", e)
            coordinator.markFailed(pending)
            false
        }

    private suspend fun processPendingBatch(pending: PendingWork) {
        val tasks = mutableListOf<suspend () -> Unit>()

        if (pending.fullScanPending) {
            val messageIds = messageDao.getAllIds().map { it.value }
            val memoryIds = memoriesDao.getAllIds()
            val documentIds = documentDao.getAllIds()

            messageIds.forEach { id ->
                tasks.add { processMessage(id) }
            }
            memoryIds.forEach { id ->
                tasks.add { processMemory(id) }
            }
            documentIds.forEach { id ->
                tasks.add { processDocument(id) }
            }
        } else {
            pending.messageIds.forEach { id ->
                tasks.add { processMessage(id) }
            }
            pending.memoryIds.forEach { id ->
                tasks.add { processMemory(id) }
            }
            pending.documentIds.forEach { id ->
                tasks.add { processDocument(id) }
            }
        }

        val actualTotal = tasks.size
        if (actualTotal == 0) {
            setProgress(
                androidx.work.workDataOf(
                    "current" to 0,
                    "total" to 0,
                    "fraction" to 0f
                )
            )
            return
        }

        var actualProcessed = 0
        setProgress(
            androidx.work.workDataOf(
                "current" to 0,
                "total" to actualTotal,
                "fraction" to 0f
            )
        )

        val maxConcurrency = embeddingEngine.maxConcurrentEmbeddings
        if (maxConcurrency > 1) {
            val semaphore = Semaphore(maxConcurrency)
            val mutex = Mutex()
            kotlinx.coroutines.coroutineScope {
                tasks.map { task ->
                    async {
                        semaphore.withPermit {
                            try {
                                task()
                            } catch (c: CancellationException) {
                                throw c
                            } catch (e: Exception) {
                                loggingPort.error(TAG, "Task failed during concurrent embedding", e)
                            } finally {
                                mutex.withLock {
                                    actualProcessed++
                                    val fraction = actualProcessed.toFloat() / actualTotal
                                    setProgress(
                                        androidx.work.workDataOf(
                                            "current" to actualProcessed,
                                            "total" to actualTotal,
                                            "fraction" to fraction
                                        )
                                    )
                                }
                            }
                        }
                    }
                }.awaitAll()
            }
        } else {
            tasks.forEach { task ->
                try {
                    task()
                } catch (c: CancellationException) {
                    throw c
                } finally {
                    actualProcessed++
                    val fraction = actualProcessed.toFloat() / actualTotal
                    setProgress(
                        androidx.work.workDataOf(
                            "current" to actualProcessed,
                            "total" to actualTotal,
                            "fraction" to fraction
                        )
                    )
                }
            }
        }
    }

    private suspend fun processMessage(id: String) {
        val message = messageRepository.getMessageById(MessageId(id)) ?: return
        val content = message.content.text
        val hash = content.sha256()

        val existing = embeddingDao.getEmbeddingMetadata(id)
        if (existing?.contentHash != hash) {
            val embedding = embeddingEngine.getEmbedding(content)
            messageRepository.saveEmbedding(message.id, embedding)
            embeddingDao.insertEmbeddingMetadata(EmbeddingEntity(id, hash))
            loggingPort.debug(TAG, "Re-embedded message $id")
        }
    }

    private suspend fun processMemory(id: String) {
        val memory = memoriesRepository.getMemoryById(id) ?: return
        val content = memory.content
        val hash = content.sha256()

        val existing = embeddingDao.getEmbeddingMetadata(id)
        if (existing?.contentHash != hash) {
            val embedding = embeddingEngine.getEmbedding(content)
            // Use DAO directly for virtual table to avoid redundant memory repository sync
            val vectorBlob = floatArrayToByteArray(embedding)
            embeddingDao.replaceEmbedding(
                androidx.sqlite.db.SimpleSQLiteQuery(
                    "DELETE FROM memory_embeddings WHERE id = ?",
                    arrayOf(id),
                ),
                androidx.sqlite.db.SimpleSQLiteQuery(
                    "INSERT INTO memory_embeddings (id, vector) VALUES (?, ?)",
                    arrayOf(id, vectorBlob),
                ),
            )
            embeddingDao.insertEmbeddingMetadata(EmbeddingEntity(id, hash))
            loggingPort.debug(TAG, "Re-embedded memory $id")
        }
    }

    private suspend fun processDocument(id: String) {
        val entity = documentDao.getDocumentById(id) ?: return

        val json = Json { ignoreUnknownKeys = true }
        // Must match ArtifactToolExecutor / SensitiveEntityFieldEncryptors binding for DocumentEntity.json
        val jsonAad = AadContext("DocumentEntity", "json", id).toBytes()
        val doc: kotlinx.serialization.json.JsonObject =
            try {
                json.parseToJsonElement(
                    localEncryption.decryptString(entity.json, jsonAad),
                ) as kotlinx.serialization.json.JsonObject
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                loggingPort.error(TAG, "Failed to parse document JSON for re-embedding $id", e)
                return
            }

        val blocks = doc["blocks"] as? kotlinx.serialization.json.JsonArray ?: return

        blocks.forEachIndexed { index, blockElement ->
            val blockText =
                com.browntowndev.enclave.domain.util.ArtifactBlockUtil
                    .getVisibleText(blockElement)
            if (blockText.isBlank()) return@forEachIndexed

            val compositeId = "${id}_$index"
            val hash = blockText.sha256()

            val existing = embeddingDao.getEmbeddingMetadata(compositeId)
            if (existing?.contentHash != hash) {
                try {
                    val embedding = embeddingEngine.getEmbedding(blockText)
                    val vectorBlob = floatArrayToByteArray(embedding)

                    documentDao.deleteBlockEmbedding(
                        androidx.sqlite.db.SimpleSQLiteQuery(
                            "DELETE FROM artifact_block_embeddings WHERE id = ?",
                            arrayOf(compositeId),
                        ),
                    )
                    documentDao.insertBlockEmbedding(
                        androidx.sqlite.db.SimpleSQLiteQuery(
                            "INSERT INTO artifact_block_embeddings(id, vector) VALUES (?, ?)",
                            arrayOf(compositeId, vectorBlob),
                        ),
                    )
                    embeddingDao.insertEmbeddingMetadata(EmbeddingEntity(compositeId, hash))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    loggingPort.error(TAG, "Failed to embed block $index for document $id", e)
                }
            }
        }
    }

    private fun floatArrayToByteArray(floatArray: FloatArray): ByteArray {
        val buffer =
            java.nio.ByteBuffer
                .allocate(floatArray.size * 4)
                .order(java.nio.ByteOrder.LITTLE_ENDIAN)
        for (f in floatArray) buffer.putFloat(f)
        return buffer.array()
    }

    private fun String.sha256(): String =
        java.security.MessageDigest
            .getInstance("SHA-256")
            .digest(toByteArray())
            .joinToString("") { "%02x".format(it) }
}
