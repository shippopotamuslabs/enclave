package com.browntowndev.pocketcrew.core.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.browntowndev.pocketcrew.core.data.local.ChatDao
import com.browntowndev.pocketcrew.core.data.local.MessageDao
import com.browntowndev.pocketcrew.core.data.mapper.toDomain
import com.browntowndev.pocketcrew.domain.port.repository.CloudSyncPort
import com.browntowndev.pocketcrew.domain.port.security.CryptoManagerPort
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val chatDao: ChatDao,
    private val messageDao: MessageDao,
    private val cryptoManager: CryptoManagerPort,
    private val cloudSyncPort: CloudSyncPort,
    private val loggingPort: LoggingPort
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "SyncWorker"
    }

    override suspend fun doWork(): Result {
        if (!cryptoManager.hasCachedKey()) {
            // Vault is locked, cannot encrypt data. Retry later.
            return Result.retry()
        }

        return try {
            val pendingChats = chatDao.getPendingSyncChats()
            val pendingMessages = messageDao.getPendingSyncMessages()

            loggingPort.debug(TAG, "SyncWorker: Found ${pendingChats.size} pending chats and ${pendingMessages.size} pending messages")

            if (pendingChats.isEmpty() && pendingMessages.isEmpty()) {
                loggingPort.debug(TAG, "SyncWorker: Nothing to sync, returning success")
                return Result.success()
            }

            val result = cloudSyncPort.syncEntitiesToCloud(
                chats = pendingChats.map { it.toDomain() },
                messages = pendingMessages.map { it.toDomain() }
            )

            if (result.isSuccess) {
                loggingPort.debug(TAG, "SyncWorker: Cloud push successful, marking as synced")
                chatDao.markSynced(pendingChats.map { it.id })
                messageDao.markSynced(pendingMessages.map { it.message.id })
                Result.success()
            } else {
                loggingPort.warning(TAG, "SyncWorker: Cloud push failed, returning retry")
                Result.retry()
            }
        } catch (e: Exception) {
            loggingPort.error(TAG, "Sync failed", e)
            Result.retry()
        }
    }
}
