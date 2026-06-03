package com.browntowndev.enclave.core.data.profile.analysis

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.annotation.Keep
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.browntowndev.enclave.core.data.R
import com.browntowndev.enclave.domain.model.config.ApiModelConfigurationId
import com.browntowndev.enclave.domain.model.error.InferenceIOException
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.IOException

@Keep
@HiltWorker
class ProfileAnalysisWorker
    @AssistedInject
    constructor(
        @Assisted private val context: Context,
        @Assisted params: WorkerParameters,
        private val orchestrator: ProfileAnalysisOrchestrator,
    ) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result {
            val corpusRunId = inputData.getString(KEY_CORPUS_RUN_ID) ?: return Result.failure()
            val configId = inputData.getString(KEY_CONFIG_ID) ?: return Result.failure()

            ensureNotificationChannel()
            setForeground(buildForegroundInfo("Analysing your conversations…"))

            return try {
                orchestrator.runAnalysis(
                    corpusRunId = corpusRunId,
                    configId = ApiModelConfigurationId(configId),
                    onProgress = { current, total ->
                        setProgress(workDataOf(KEY_CURRENT_PASS to current, KEY_TOTAL_PASSES to total))
                        setForeground(buildForegroundInfo("Pass $current of $total…"))
                    },
                )
                Result.success(workDataOf(KEY_STATUS to "complete"))
            } catch (e: InferenceIOException) {
                handleRetry(e)
            } catch (e: IOException) {
                handleRetry(e)
            } catch (e: Exception) {
                Result.failure(workDataOf(KEY_STATUS to "failed", KEY_ERROR to (e.message ?: "Unknown error")))
            }
        }

        private fun handleRetry(e: Exception): Result =
            if (runAttemptCount < 3) {
                Result.retry()
            } else {
                Result.failure(
                    workDataOf(
                        KEY_STATUS to "failed",
                        KEY_ERROR to "Max retries exceeded: ${e.message}",
                    ),
                )
            }

        private fun ensureNotificationChannel() {
            val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
                mgr.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, "Profile Analysis", NotificationManager.IMPORTANCE_LOW),
                )
            }
        }

        private fun buildForegroundInfo(text: String): ForegroundInfo {
            val notification = ProfileAnalysisNotificationFactory.build(context, text)
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            } else {
                ForegroundInfo(NOTIFICATION_ID, notification)
            }
        }

        companion object {
            const val KEY_CORPUS_RUN_ID = "corpus_run_id"
            const val KEY_CONFIG_ID = "config_id"
            const val KEY_CURRENT_PASS = "current_pass"
            const val KEY_TOTAL_PASSES = "total_passes"
            const val KEY_STATUS = "status"
            const val KEY_ERROR = "error"
            const val CHANNEL_ID = "profile_analysis_channel"
            const val NOTIFICATION_ID = 2001
        }
    }

internal object ProfileAnalysisNotificationFactory {
    fun build(
        context: Context,
        text: String,
    ): Notification =
        NotificationCompat
            .Builder(context, ProfileAnalysisWorker.CHANNEL_ID)
            .setContentTitle("Building psychological profile")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setSilent(true)
            .build()
}
