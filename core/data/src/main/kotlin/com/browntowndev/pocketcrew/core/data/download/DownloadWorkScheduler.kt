package com.browntowndev.pocketcrew.core.data.download

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.browntowndev.pocketcrew.domain.model.download.DownloadWorkRequest
import com.browntowndev.pocketcrew.domain.model.download.ModelConfig
import com.browntowndev.pocketcrew.domain.port.download.DownloadWorkSchedulerPort
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadWorkScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val workManager: WorkManager
) : DownloadWorkSchedulerPort {
    companion object {
        private const val TAG = "DownloadWorkScheduler"
    }

    override fun enqueue(request: DownloadWorkRequest) {
        // Build network constraints
        val networkType = if (request.wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(networkType)
            .setRequiresStorageNotLow(true)
            .build()

        // Serialize DownloadFileSpec list to JSON
        val filesJson = JSONArray()
        request.files.forEach { spec ->
            filesJson.put(JSONObject().apply {
                put("remoteFileName", spec.remoteFileName)
                put("localFileName", spec.localFileName)
                put("sha256", spec.sha256)
                put("sizeInBytes", spec.sizeInBytes)
                put("huggingFaceModelName", spec.huggingFaceModelName)
                put("source", spec.source)
                put("modelFileFormat", spec.modelFileFormat)
                put("mmprojRemoteFileName", spec.mmprojRemoteFileName)
                put("mmprojLocalFileName", spec.mmprojLocalFileName)
                put("mmprojSha256", spec.mmprojSha256)
                put("mmprojSizeInBytes", spec.mmprojSizeInBytes)
            })
        }

        // Build download worker input
        val downloadInput = workDataOf(
            DownloadWorkKeys.KEY_SESSION_ID to request.sessionId,
            DownloadWorkKeys.KEY_REQUEST_KIND to request.requestKind.name,
            DownloadWorkKeys.KEY_DOWNLOAD_FILES to filesJson.toString(),
            DownloadWorkKeys.KEY_TARGET_MODEL_ID to request.targetModelId?.value,
        )

        // Build download worker request
        val downloadRequest = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
            .setConstraints(constraints)
            .setInputData(downloadInput)
            .addTag(ModelConfig.WORK_TAG)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()

        // Build finalize worker request with static request metadata.
        // Even though WorkManager merges parent output into child input via
        // OverwritingInputMerger, explicit static metadata ensures the finalizer
        // is self-sufficient and does not rely solely on parent output.
        val finalizeInput = workDataOf(
            DownloadWorkKeys.KEY_SESSION_ID to request.sessionId,
            DownloadWorkKeys.KEY_REQUEST_KIND to request.requestKind.name,
            DownloadWorkKeys.KEY_TARGET_MODEL_ID to (request.targetModelId?.value ?: ""),
        )
        val finalizeRequest = OneTimeWorkRequestBuilder<DownloadFinalizeWorker>()
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
                if (file.extension == "tmp") {
                    Log.d(TAG, "Deleting partial file: ${file.name}")
                    file.delete()
                }
            }
        }
    }
}