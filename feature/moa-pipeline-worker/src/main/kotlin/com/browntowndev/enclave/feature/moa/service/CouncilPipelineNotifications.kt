package com.browntowndev.enclave.feature.moa.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import com.browntowndev.enclave.domain.model.inference.PipelineStep
import com.browntowndev.enclave.core.ui.R as CoreUiR

internal object CouncilPipelineNotifications {
    const val PROGRESS_CHANNEL_ID = "crew_inference_channel"
    const val COMPLETION_CHANNEL_ID = "crew_completion_channel"
    const val NOTIFICATION_ID = 2001

    private const val PROGRESS_DRAFT_ONE = 25
    private const val PROGRESS_DRAFT_TWO = 50
    private const val PROGRESS_SYNTHESIS = 75
    private const val PROGRESS_FINAL = 100

    fun createNotificationChannels(notificationManager: NotificationManager) {
        val progressChannel =
            NotificationChannel(
                PROGRESS_CHANNEL_ID,
                "Crew Progress",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Shows progress when Crew is thinking"
                setShowBadge(false)
            }
        notificationManager.createNotificationChannel(progressChannel)

        val completionChannel =
            NotificationChannel(
                COMPLETION_CHANNEL_ID,
                "Crew Complete",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Shows when Crew finishes thinking"
                setShowBadge(true)
            }
        notificationManager.createNotificationChannel(completionChannel)
    }

    fun createCancelPendingIntent(service: Service): PendingIntent {
        val cancelIntent =
            Intent(service, InferenceService::class.java).apply {
                action = InferenceService.ACTION_STOP
            }
        return PendingIntent.getService(
            service,
            0,
            cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun createProgressNotification(
        service: Service,
        chatId: String,
        currentStep: PipelineStep,
        hasMoreSteps: Boolean,
        cancelPendingIntent: PendingIntent,
    ): Notification {
        val progress =
            when (currentStep) {
                PipelineStep.DRAFT_ONE -> PROGRESS_DRAFT_ONE
                PipelineStep.DRAFT_TWO -> PROGRESS_DRAFT_TWO
                PipelineStep.SYNTHESIS -> PROGRESS_SYNTHESIS
                PipelineStep.FINAL -> PROGRESS_FINAL
            }

        val contentTitle = "Crew is thinking"
        val contentText = currentStep.displayName()
        val nextStepText =
            if (hasMoreSteps) {
                "Next: ${currentStep.next()?.displayName() ?: "Complete"}"
            } else {
                "Final step..."
            }

        val pendingIntent = chatDeepLinkPendingIntent(service, chatId)

        return NotificationCompat
            .Builder(service, PROGRESS_CHANNEL_ID)
            .setContentTitle(contentTitle)
            .setContentText("$contentText - $nextStepText")
            .setSmallIcon(CoreUiR.drawable.ic_notification)
            .setOngoing(true)
            .setProgress(100, progress, false)
            .setContentIntent(pendingIntent)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Cancel",
                cancelPendingIntent,
            ).build()
    }

    fun showCompletionNotification(
        service: Service,
        notificationManager: NotificationManager,
        chatId: String,
        thinkingDurationSeconds: Int,
    ) {
        val pendingIntent = chatDeepLinkPendingIntent(service, chatId)

        val notification =
            NotificationCompat
                .Builder(service, COMPLETION_CHANNEL_ID)
                .setContentTitle("Crew finished thinking")
                .setContentText("Completed in ${thinkingDurationSeconds}s - tap to see response")
                .setSmallIcon(CoreUiR.drawable.ic_notification)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setDefaults(NotificationCompat.DEFAULT_SOUND)
                .build()

        notificationManager.notify(NOTIFICATION_ID + 1, notification)
    }

    private fun chatDeepLinkPendingIntent(
        service: Service,
        chatId: String,
    ): PendingIntent {
        val intent =
            Intent(Intent.ACTION_VIEW, Uri.parse("enclave://chat/$chatId")).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
        return PendingIntent.getActivity(
            service,
            chatId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
