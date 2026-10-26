package com.browntowndev.enclave.core.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import com.browntowndev.enclave.domain.port.repository.StudioRepositoryPort
import com.browntowndev.enclave.domain.port.security.CryptoManagerPort
import com.google.firebase.storage.FirebaseStorage
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.tasks.await

/**
 * Materializes cloud media on devices that synced metadata down but never had the bytes locally
 * (the fresh-install case). The upload worker stores media in Firebase Storage encrypted at rest;
 * sync-down only carries the metadata + remote URL, so without this worker the gallery points Coil
 * at ciphertext and renders blank. For each row still missing its local file this downloads the
 * encrypted blob, decrypts it with the cached DEK, and repoints the row at a local file.
 */
@HiltWorker
class MediaCloudDownloadWorker
    @AssistedInject
    constructor(
        @Assisted context: Context,
        @Assisted params: WorkerParameters,
        private val studioRepository: StudioRepositoryPort,
        private val storage: FirebaseStorage,
        private val loggingPort: LoggingPort,
        private val cryptoManager: CryptoManagerPort,
        private val userIdProvider: () -> String?,
    ) : CoroutineWorker(context, params) {
        companion object {
            private const val TAG = "MediaCloudDownloadWorker"
            private const val MAX_DOWNLOAD_BYTES = 256L * 1024 * 1024
        }

        @Suppress("LoopWithTooManyJumpStatements")
        override suspend fun doWork(): Result {
            val userId = userIdProvider() ?: return Result.failure()
            if (!cryptoManager.hasCachedKey()) {
                loggingPort.debug(TAG, "Vault locked; deferring media download")
                return Result.retry()
            }

            val pending = studioRepository.getMediaPendingDownload()
            if (pending.isEmpty()) return Result.success()

            val failures = mutableListOf<Throwable>()
            for (media in pending) {
                try {
                    val storageRef = storage.reference.child("users/$userId/media/${media.id}")
                    val encryptedBytes = storageRef.getBytes(MAX_DOWNLOAD_BYTES).await()

                    val decrypted = cryptoManager.decryptBytes(encryptedBytes)
                    if (decrypted.isFailure) {
                        loggingPort.error(TAG, "Decryption failed for media ${media.id}", decrypted.exceptionOrNull())
                        decrypted.exceptionOrNull()?.let { failures += it }
                        continue
                    }

                    studioRepository.materializeDownloadedMedia(media.id, decrypted.getOrThrow())
                    loggingPort.debug(TAG, "Materialized media ${media.id}")
                } catch (e: Exception) {
                    loggingPort.error(TAG, "Failed to download media ${media.id}", e)
                    failures += e
                }
            }

            return failures.toWorkerResult()
        }
    }
