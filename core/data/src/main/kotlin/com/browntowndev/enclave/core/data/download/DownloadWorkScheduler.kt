package com.browntowndev.enclave.core.data.download

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.browntowndev.enclave.domain.model.download.DownloadWorkRequest
import com.browntowndev.enclave.domain.model.download.ModelConfig
import com.browntowndev.enclave.domain.port.download.DownloadWorkSchedulerPort
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadWorkScheduler
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val workManager: WorkManager,
    ) : DownloadWorkSchedulerPort {
        companion object {
            private const val TAG = "DownloadWorkScheduler"
            private const val MAX_WORK_DATA_BYTES = Data.MAX_DATA_BYTES
        }

        override fun enqueue(request: DownloadWorkRequest) {
            // Build network constraints
            val networkType = if (request.wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED
            val constraints =
                Constraints
                    .Builder()
                    .setRequiredNetworkType(networkType)
                    .setRequiresStorageNotLow(true)
                    .build()

            // Serialize DownloadFileSpec list to JSON
            val downloadFilesJson = Json.encodeToString(request.files)

            // Build download worker input — WorkManager rejects payloads over MAX_DATA_BYTES at enqueue time.
            val downloadInput =
                try {
                    workDataOf(
                        DownloadWorkKeys.KEY_SESSION_ID to request.sessionId,
                        DownloadWorkKeys.KEY_REQUEST_KIND to request.requestKind.name,
                        DownloadWorkKeys.KEY_DOWNLOAD_FILES to downloadFilesJson,
                        DownloadWorkKeys.KEY_TARGET_MODEL_ID to request.targetModelId?.value,
                    )
                } catch (e: IllegalArgumentException) {
                    Log.e(
                        TAG,
                        "Download work payload exceeds $MAX_WORK_DATA_BYTES bytes for ${request.files.size} file specs",
                        e,
                    )
                    throw DownloadWorkPayloadTooLargeException(request.files.size, e)
                }

            // Build download worker request
            val downloadRequest =
                OneTimeWorkRequestBuilder<ModelDownloadWorker>()
                    .setConstraints(constraints)
                    .setInputData(downloadInput)
                    .addTag(ModelConfig.WORK_TAG)
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                    .build()

            // Build finalize worker request with static request metadata.
            // Even though WorkManager merges parent output into child input via
            // OverwritingInputMerger, explicit static metadata ensures the finalizer
            // is self-sufficient and does not rely solely on parent output.
            val finalizeInput =
                workDataOf(
                    DownloadWorkKeys.KEY_SESSION_ID to request.sessionId,
                    DownloadWorkKeys.KEY_REQUEST_KIND to request.requestKind.name,
                    DownloadWorkKeys.KEY_TARGET_MODEL_ID to (request.targetModelId?.value ?: ""),
                )
            val finalizeRequest =
                OneTimeWorkRequestBuilder<DownloadFinalizeWorker>()
                    .setConstraints(constraints)
                    .setInputData(finalizeInput)
                    .addTag(ModelConfig.WORK_TAG)
                    .build()

            // Enqueue as a chained work sequence
            // The chain automatically passes output data from each worker to the next
            workManager
                .beginUniqueWork(ModelConfig.WORK_TAG, ExistingWorkPolicy.REPLACE, downloadRequest)
                .then(finalizeRequest)
                .enqueue()
        }

        override fun cancel() {
            workManager.cancelUniqueWork(ModelConfig.WORK_TAG)
        }

        override suspend fun cleanupTempFiles() {
            withContext(Dispatchers.IO) {
                val modelsDir = File(context.getExternalFilesDir(null), ModelConfig.MODELS_DIR)
                modelsDir.listFiles()?.forEach { file ->
                    if (file.extension == "tmp" || file.name.endsWith(ModelConfig.TEMP_META_EXTENSION)) {
                        Log.d(TAG, "Deleting partial file: ${file.name}")
                        file.delete()
                    }
                }
            }
        }
    }
