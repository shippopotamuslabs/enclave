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
import com.browntowndev.pocketcrew.domain.port.download.DownloadWorkSchedulerPort
import dagger.hilt.android.qualifiers.ApplicationContext
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
        const val KEY_SESSION_ID = "work_session_id"
        private const val TAG = "DownloadWorkScheduler"
    }

    override fun enqueue(models: Map<ModelType, LocalModelAsset>, sessionId: String?, wifiOnly: Boolean) {
        // Use UNMETERED when wifiOnly is enabled (requires WiFi)
        // Use CONNECTED when wifiOnly is disabled (allows mobile data)
        val networkType = if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(networkType)
            .setRequiresStorageNotLow(true)
            .build()

        // Serialize LocalModelAsset as JSON so prompts and display names can contain arbitrary characters.
        val inputData = Array<String?>(models.size) { index ->
            val (modelType, asset) = models.entries.elementAt(index)
            val config = asset.configurations.firstOrNull()
            JSONObject().apply {
                put("modelType", modelType.name)
                put("remoteFileName", asset.metadata.remoteFileName)
                put("localFileName", asset.metadata.localFileName)
                put("presetName", config?.displayName ?: asset.metadata.huggingFaceModelName)
                put("huggingFaceModelName", asset.metadata.huggingFaceModelName)
                put("sizeInBytes", asset.metadata.sizeInBytes)
                put("sha256", asset.metadata.sha256)
                put("mmprojRemoteFileName", asset.metadata.mmprojRemoteFileName)
                put("mmprojLocalFileName", asset.metadata.mmprojLocalFileName)
                put("mmprojSha256", asset.metadata.mmprojSha256)
                put("mmprojSizeInBytes", asset.metadata.mmprojSizeInBytes)
                put("source", asset.metadata.source.name)
                put("modelFileFormat", asset.metadata.modelFileFormat.name)
                put("temperature", config?.temperature ?: 0.7)
                put("topK", config?.topK ?: 40)
                put("topP", config?.topP ?: 0.95)
                put("minP", config?.minP ?: 0.0)
                put("repetitionPenalty", config?.repetitionPenalty ?: 1.1)
                put("maxTokens", config?.maxTokens ?: 4096)
                put("contextWindow", config?.contextWindow ?: 4096)
                put("systemPrompt", config?.systemPrompt ?: "")
                put("isSystemPreset", config?.isSystemPreset ?: true)
                put("thinkingEnabled", config?.thinkingEnabled ?: false)
            }.toString()
        }

        val workRequest = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
            .setConstraints(constraints)
            .setInputData(
                Data.Builder()
                    .putStringArray("model_files", inputData)
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

    override fun scheduleModelDownload(modelType: ModelType, modelAsset: LocalModelAsset) {
        enqueue(mapOf(modelType to modelAsset), sessionId = null, wifiOnly = true)
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
