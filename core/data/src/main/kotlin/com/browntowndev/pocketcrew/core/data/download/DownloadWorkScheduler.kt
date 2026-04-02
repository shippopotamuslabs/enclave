package com.browntowndev.pocketcrew.core.data.download

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.browntowndev.pocketcrew.domain.model.download.ModelConfig
import com.browntowndev.pocketcrew.domain.model.config.LocalModelAsset
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import dagger.hilt.android.qualifiers.ApplicationContext
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
) {
    companion object {
        const val KEY_SESSION_ID = "work_session_id"
        private const val TAG = "DownloadWorkScheduler"
    }

    fun enqueue(models: Map<ModelType, LocalModelAsset>, sessionId: String?, wifiOnly: Boolean = true) {
        // Use UNMETERED when wifiOnly is enabled (requires WiFi)
        // Use CONNECTED when wifiOnly is disabled (allows mobile data)
        val networkType = if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(networkType)
            .setRequiresStorageNotLow(true)
            .build()

        // Serialize LocalModelAsset to pipe-delimited string:
        // modelType|remoteFileName|localFileName|presetName|huggingFaceModelName|sizeInBytes|sha256|modelFileFormat|temperature|topK|topP|minP|repetitionPenalty|maxTokens|contextWindow|systemPrompt|isSystemPreset
        val inputData = models.entries
            .map { (modelType, asset) ->
                val config = asset.configurations.firstOrNull()
                listOf(
                    modelType.name,
                    asset.metadata.remoteFileName,
                    asset.metadata.localFileName,
                    config?.displayName ?: asset.metadata.huggingFaceModelName,
                    asset.metadata.huggingFaceModelName,
                    asset.metadata.sizeInBytes.toString(),
                    asset.metadata.sha256,
                    asset.metadata.modelFileFormat.name,
                    config?.temperature?.toString() ?: "0.7",
                    config?.topK?.toString() ?: "40",
                    config?.topP?.toString() ?: "0.95",
                    config?.minP?.toString() ?: "0.0",
                    config?.repetitionPenalty?.toString() ?: "1.1",
                    config?.maxTokens?.toString() ?: "4096",
                    config?.contextWindow?.toString() ?: "4096",
                    config?.systemPrompt ?: "",
                    config?.isSystemPreset?.toString() ?: "true"
                ).joinToString("|")
            }
            .toTypedArray()

        val workRequest = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
            .setConstraints(constraints)
            .setInputData(
                Data.Builder()
                    .putStringArray("model_files", inputData as Array<String?>)
                    .putString(KEY_SESSION_ID, sessionId)
                    .build()
            )
            .addTag(ModelConfig.WORK_TAG)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()

        workManager.enqueueUniqueWork(
            ModelConfig.WORK_TAG,
            ExistingWorkPolicy.REPLACE,
            workRequest
        )
    }

    fun cancel() {
        workManager.cancelUniqueWork(ModelConfig.WORK_TAG)
    }

    suspend fun cleanupTempFiles() = withContext(Dispatchers.IO) {
        val modelsDir = File(context.getExternalFilesDir(null), ModelConfig.MODELS_DIR)
        modelsDir.listFiles()?.forEach { file ->
            if (file.extension == "tmp") {
                Log.d(TAG, "Deleting partial file: ${file.name}")
                file.delete()
            }
        }
    }
}
