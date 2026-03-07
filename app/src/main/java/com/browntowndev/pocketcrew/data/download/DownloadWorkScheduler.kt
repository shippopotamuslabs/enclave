package com.browntowndev.pocketcrew.data.download

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.browntowndev.pocketcrew.domain.model.ModelConfig
import com.browntowndev.pocketcrew.domain.model.ModelFile
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

    fun enqueue(models: List<ModelFile>, sessionId: String?) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.UNMETERED)
            .setRequiresStorageNotLow(true)
            .build()

        val inputData = models
            .map { "${it.originalFileName}|${it.sizeBytes}|${it.url}|${it.md5 ?: ""}|${it.modelTypes.joinToString(",")}|${it.modelFileFormat.name}" }
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
