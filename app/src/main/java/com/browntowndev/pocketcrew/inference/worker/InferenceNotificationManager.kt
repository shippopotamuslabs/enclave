package com.browntowndev.pocketcrew.inference.worker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.ForegroundInfo
import com.browntowndev.pocketcrew.domain.model.inference.PipelineStep
import com.browntowndev.pocketcrew.presentation.MainActivity

/**
 * Manages notifications for the Crew Mode inference pipeline.
 * Shows progress during pipeline execution and completion notifications.
 */
class InferenceNotificationManager(
    private val context: Context,
    private val notificationManager: NotificationManager
) {
    companion object {
        const val NOTIFICATION_ID = 2001
        const val CHANNEL_ID = "crew_inference_channel"
        const val COMPLETION_CHANNEL_ID = "crew_completion_channel"
        const val KEY_STATE_JSON = "pipeline_state_json"
        const val ACTION_CANCEL = "com.browntowndev.pocketcrew.ACTION_CANCEL_INFERENCE"
    }

    fun createNotificationChannel() {
        // Progress channel
        val progressChannel = NotificationChannel(
            CHANNEL_ID,
            "Crew Progress",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows progress when Crew is thinking"
            setShowBadge(false)
        }
        notificationManager.createNotificationChannel(progressChannel)

        // Completion channel
        val completionChannel = NotificationChannel(
            COMPLETION_CHANNEL_ID,
            "Crew Complete",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Shows when Crew finishes thinking"
            setShowBadge(true)
        }
        notificationManager.createNotificationChannel(completionChannel)
    }

    /**
     * Creates initial foreground info when worker starts.
     */
    fun createForegroundInfo(
        currentStep: PipelineStep,
        cancelPendingIntent: PendingIntent
    ): ForegroundInfo {
        val notification = createProgressNotification(
            currentStep = currentStep,
            progress = 0,
            hasMoreSteps = currentStep.next() != null,
            cancelPendingIntent = cancelPendingIntent
        )

        return ForegroundInfo(
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )
    }

    /**
     * Creates foreground info for step progress updates.
     */
    fun createForegroundInfoForStep(
        currentStep: PipelineStep,
        hasMoreSteps: Boolean,
        cancelPendingIntent: PendingIntent
    ): ForegroundInfo {
        // Calculate progress based on step
        val progress = when (currentStep) {
            PipelineStep.DRAFT_ONE -> 25
            PipelineStep.DRAFT_TWO -> 50
            PipelineStep.SYNTHESIS -> 75
            PipelineStep.FINAL -> 100
        }

        val notification = createProgressNotification(
            currentStep = currentStep,
            progress = progress,
            hasMoreSteps = hasMoreSteps,
            cancelPendingIntent = cancelPendingIntent
        )

        return ForegroundInfo(
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )
    }

    /**
     * Updates the foreground notification directly via NotificationManager.
     * This is more reliable than setForegroundAsync for updates.
     */
    fun updateNotificationProgress(
        currentStep: PipelineStep,
        hasMoreSteps: Boolean
    ) {
        val cancelIntent = PendingIntent.getBroadcast(
            context,
            0,
            Intent(ACTION_CANCEL),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = createProgressNotification(
            currentStep = currentStep,
            progress = when (currentStep) {
                PipelineStep.DRAFT_ONE -> 25
                PipelineStep.DRAFT_TWO -> 50
                PipelineStep.SYNTHESIS -> 75
                PipelineStep.FINAL -> 100
            },
            hasMoreSteps = hasMoreSteps,
            cancelPendingIntent = cancelIntent
        )

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun createProgressNotification(
        currentStep: PipelineStep,
        progress: Int,
        hasMoreSteps: Boolean,
        cancelPendingIntent: PendingIntent
    ): Notification {
        val contentTitle = "Crew is thinking"
        val contentText = currentStep.displayName()
        val nextStepText = if (hasMoreSteps) {
            "Next: ${currentStep.next()?.displayName() ?: "Complete"}"
        } else {
            "Final step..."
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(contentTitle)
            .setContentText("$contentText - $nextStepText")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true)
            .setProgress(100, progress, false)
            .setContentIntent(pendingIntent)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Cancel",
                cancelPendingIntent
            )
            .build()
    }

    /**
     * Shows completion notification when pipeline finishes.
     */
    fun showCompletionNotification(
        durationSeconds: Int,
        openChatPendingIntent: PendingIntent
    ) {
        val notification = NotificationCompat.Builder(context, COMPLETION_CHANNEL_ID)
            .setContentTitle("Crew finished thinking")
            .setContentText("Completed in ${durationSeconds}s - tap to see response")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setAutoCancel(true)
            .setContentIntent(openChatPendingIntent)
            .setDefaults(NotificationCompat.DEFAULT_SOUND)
            .build()

        notificationManager.notify(NOTIFICATION_ID + 1, notification)
    }
}
