package com.browntowndev.pocketcrew.core.data.sync

import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EmbeddingRegenerationCoordinator @Inject constructor(
    private val workManager: WorkManager,
    @com.browntowndev.pocketcrew.domain.qualifier.ApplicationScope private val externalScope: kotlinx.coroutines.CoroutineScope
) {
    private val pendingMessageIds = mutableSetOf<String>()
    private val pendingMemoryIds = mutableSetOf<String>()
    private val pendingDocumentIds = mutableSetOf<String>()
    private var fullScanPending = false
    private val mutex = kotlinx.coroutines.sync.Mutex()

    fun enqueueMessage(messageId: String) {
        externalScope.launch {
            mutex.withLock {
                pendingMessageIds.add(messageId)
                triggerWorker()
            }
        }
    }

    fun enqueueMemory(memoryId: String) {
        externalScope.launch {
            mutex.withLock {
                pendingMemoryIds.add(memoryId)
                triggerWorker()
            }
        }
    }

    fun enqueueDocument(documentId: String) {
        externalScope.launch {
            mutex.withLock {
                pendingDocumentIds.add(documentId)
                triggerWorker()
            }
        }
    }

    fun enqueueFullRegeneration() {
        externalScope.launch {
            mutex.withLock {
                fullScanPending = true
                triggerWorker()
            }
        }
    }

    private fun triggerWorker() {
        val constraints = androidx.work.Constraints.Builder()
            .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()

        val request = OneTimeWorkRequestBuilder<EmbeddingRegenerationWorker>()
            .setConstraints(constraints)
            .build()

        // Use a unique name to collapse multiple triggers into a single batch
        workManager.enqueueUniqueWork(
            "pocketcrew_embedding_regen",
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    suspend fun consumePending(): PendingWork {
        return mutex.withLock {
            val messages = pendingMessageIds.toList()
            val memories = pendingMemoryIds.toList()
            val documents = pendingDocumentIds.toList()
            val full = fullScanPending
            
            pendingMessageIds.clear()
            pendingMemoryIds.clear()
            pendingDocumentIds.clear()
            fullScanPending = false
            
            PendingWork(messages, memories, documents, full)
        }
    }

    suspend fun markFailed(work: PendingWork) {
        mutex.withLock {
            pendingMessageIds.addAll(work.messageIds)
            pendingMemoryIds.addAll(work.memoryIds)
            pendingDocumentIds.addAll(work.documentIds)
            if (work.fullScanPending) fullScanPending = true
        }
    }
}

data class PendingWork(
    val messageIds: List<String>,
    val memoryIds: List<String>,
    val documentIds: List<String>,
    val fullScanPending: Boolean = false
) {
    fun isEmpty() = messageIds.isEmpty() && memoryIds.isEmpty() && documentIds.isEmpty() && !fullScanPending
}
