package com.browntowndev.pocketcrew.core.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.browntowndev.pocketcrew.core.data.local.EmbeddingDao
import com.browntowndev.pocketcrew.core.data.local.EmbeddingEntity
import com.browntowndev.pocketcrew.domain.model.chat.MessageId
import com.browntowndev.pocketcrew.domain.port.inference.EmbeddingEnginePort
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.repository.MemoriesRepository
import com.browntowndev.pocketcrew.domain.port.repository.MessageRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
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
        private val messageDao: com.browntowndev.pocketcrew.core.data.local.MessageDao,
        private val memoriesDao: com.browntowndev.pocketcrew.core.data.local.MemoriesDao,
        private val documentDao: com.browntowndev.pocketcrew.core.data.local.DocumentDao,
        private val embeddingDao: com.browntowndev.pocketcrew.core.data.local.EmbeddingDao,
        private val embeddingEngine: EmbeddingEnginePort,
        private val progressManager: EmbeddingProgressManager,
        private val loggingPort: LoggingPort,
    ) : CoroutineWorker(context, params) {
        companion object {
            private const val TAG = "EmbeddingRegenerationWorker"
        }

        override suspend fun doWork(): Result {
            val pending = coordinator.consumePending()
            if (pending.isEmpty()) return Result.success()

            progressManager.setReindexing(true)
            return try {
                val total =
                    (if (pending.fullScanPending) 100 else 0) +
                        pending.messageIds.size + pending.memoryIds.size + pending.documentIds.size
                var processed = 0

                if (pending.fullScanPending) {
                    performFullScan { current, subTotal ->
                        progressManager.updateProgress(processed + (current * 100 / subTotal), total)
                    }
                    processed += 100
                }

                pending.messageIds.forEach { id ->
                    processMessage(id)
                    processed++
                    progressManager.updateProgress(processed, total)
                }

                pending.memoryIds.forEach { id ->
                    processMemory(id)
                    processed++
                    progressManager.updateProgress(processed, total)
                }

                pending.documentIds.forEach { id ->
                    processDocument(id)
                    processed++
                    progressManager.updateProgress(processed, total)
                }

                Result.success()
            } catch (e: Exception) {
                loggingPort.error(TAG, "Embedding regeneration failed", e)
                coordinator.markFailed(pending)
                Result.retry()
            } finally {
                progressManager.setReindexing(false)
            }
        }

        private suspend fun performFullScan(onProgress: (Int, Int) -> Unit = { _, _ -> }) {
            loggingPort.info(TAG, "Starting full embedding scan")

            val messageIds = messageDao.getAllIds().map { it.value }
            val memoryIds = memoriesDao.getAllIds()
            val documentIds = documentDao.getAllIds()

            val total = messageIds.size + memoryIds.size + documentIds.size
            if (total == 0) return

            var current = 0
            messageIds.forEach { id ->
                processMessage(id)
                current++
                onProgress(current, total)
            }
            memoryIds.forEach { id ->
                processMemory(id)
                current++
                onProgress(current, total)
            }
            documentIds.forEach { id ->
                processDocument(id)
                current++
                onProgress(current, total)
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
            val doc: kotlinx.serialization.json.JsonObject =
                try {
                    json.parseToJsonElement(entity.json) as kotlinx.serialization.json.JsonObject
                } catch (e: Exception) {
                    loggingPort.error(TAG, "Failed to parse document JSON for re-embedding $id", e)
                    return
                }

            val blocks = doc["blocks"] as? kotlinx.serialization.json.JsonArray ?: return

            blocks.forEachIndexed { index, blockElement ->
                val blockText =
                    com.browntowndev.pocketcrew.domain.util.ArtifactBlockUtil
                        .getVisibleText(blockElement)
                if (blockText.isBlank()) return@forEachIndexed

                val compositeId = "${id}_$index"
                val hash = blockText.sha256()

                val existing = embeddingDao.getEmbeddingMetadata(compositeId)
                if (existing?.contentHash != hash) {
                    try {
                        val embedding = embeddingEngine.getEmbedding(blockText)
                        val vectorBlob = floatArrayToByteArray(embedding)

                        documentDao.insertBlockEmbedding(
                            androidx.sqlite.db.SimpleSQLiteQuery(
                                "INSERT OR REPLACE INTO artifact_block_embeddings(id, vector) VALUES (?, ?)",
                                arrayOf(compositeId, vectorBlob),
                            ),
                        )
                        embeddingDao.insertEmbeddingMetadata(EmbeddingEntity(compositeId, hash))
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
