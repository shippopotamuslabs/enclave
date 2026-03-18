package com.browntowndev.pocketcrew.core.data.download

import android.Manifest
import android.app.Notification
import android.content.Context
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.ForegroundInfo

class DownloadNotificationManager(
    private val context: Context,
    private val notificationManager: NotificationManager
) {
    companion object {
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "model_download_channel"
    }

    fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Model Downloads",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows progress when downloading AI models"
            setShowBadge(false)
        }
        notificationManager.createNotificationChannel(channel)
    }

    fun createForegroundInfo(cancelPendingIntent: PendingIntent): ForegroundInfo {
        val notification = createNotification(
            progress = 0,
            currentFile = "Preparing...",
            subText = "Calculating...",
            cancelPendingIntent = cancelPendingIntent
        )

        return ForegroundInfo(
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )
    }

    /**
     * Creates a ForegroundInfo with progress updates for ongoing foreground service.
     * Use this instead of updateNotification() when calling from within a WorkManager
     * worker after setForeground() has been initially called.
     */
    fun createForegroundInfoForProgress(
        progress: Float,
        currentFile: String,
        subText: String,
        cancelPendingIntent: PendingIntent
    ): ForegroundInfo {
        val progressInt = (progress * 100).toInt()

        val notification = createNotification(
            progress = progressInt,
            currentFile = currentFile,
            subText = subText,
            cancelPendingIntent = cancelPendingIntent
        )

        return ForegroundInfo(
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )
    }

    /**
     * Creates a ForegroundInfo for progress updates during download.
     * This method encapsulates the notification update logic, computing progress from
     * the snapshot and building the subText internally.
     */
    fun createForegroundInfoForSnapshot(
        snapshot: ProgressSnapshot,
        formatBytes: (Long) -> String,
        formatEta: (Long) -> String,
        cancelPendingIntent: PendingIntent
    ): ForegroundInfo {
        val speedString = if (snapshot.currentSpeedMBps > 0) {
            String.format(java.util.Locale.US, "%.1f MB/s", snapshot.currentSpeedMBps)
        } else ""

        val etaString = formatEta(snapshot.etaSeconds)

        val subText = buildString {
            append(formatBytes(snapshot.totalBytesDownloaded))
            append(" / ")
            append(formatBytes(snapshot.totalSize))
            if (speedString.isNotEmpty()) {
                append(" • ")
                append(speedString)
            }
            if (snapshot.etaSeconds >= 0) {
                append(" • ETA: ")
                append(etaString)
            }
        }

        return createForegroundInfoForProgress(
            progress = snapshot.overallProgress,
            currentFile = snapshot.currentFile,
            subText = subText,
            cancelPendingIntent = cancelPendingIntent
        )
    }

    @Suppress("NotificationPermission")
    fun updateNotification(
        notificationId: Int,
        progress: Float,
        currentFile: String,
        subText: String,
        cancelPendingIntent: PendingIntent?
    ) {
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val progressInt = (progress * 100).toInt()

        val notification = createNotification(
            progress = progressInt,
            currentFile = currentFile,
            subText = subText,
            cancelPendingIntent = cancelPendingIntent
        )

        notificationManager.notify(notificationId, notification)
    }

    private fun createNotification(
        progress: Int,
        currentFile: String,
        subText: String,
        cancelPendingIntent: PendingIntent?
    ): Notification {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
            Notification.Builder(context, CHANNEL_ID)
                .setContentTitle("Downloading Crew")
                .setContentText(currentFile)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setOngoing(true)
                .setSubText(subText)
                .setStyle(
                    Notification.ProgressStyle()
                        .setProgress(progress.toFloat().toInt())
                        .setStyledByProgress(true)
                )
                .addAction(
                    Notification.Action.Builder(
                        Icon.createWithResource(context, android.R.drawable.ic_menu_close_clear_cancel),
                        "Cancel",
                        cancelPendingIntent
                    ).build()
                )
                .build()
        } else {
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle("Downloading Crew")
                .setContentText(currentFile)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setOngoing(true)
                .setProgress(100, progress, false)
                .setSubText(subText)
                .addAction(
                    android.R.drawable.ic_menu_close_clear_cancel,
                    "Cancel",
                    cancelPendingIntent
                )
                .build()
        }
    }
}
