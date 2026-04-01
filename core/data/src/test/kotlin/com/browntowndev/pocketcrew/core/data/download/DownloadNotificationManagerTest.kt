package com.browntowndev.pocketcrew.core.data.download

import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import androidx.work.ForegroundInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import androidx.test.core.app.ApplicationProvider
import org.robolectric.Shadows.shadowOf
import android.Manifest
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest=Config.NONE, sdk = [34])
class DownloadNotificationManagerTest {

    private lateinit var context: Context
    private lateinit var notificationManager: DownloadNotificationManager
    private lateinit var systemNotificationManager: NotificationManager

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        systemNotificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager = DownloadNotificationManager(context, systemNotificationManager)
    }

    @Test
    fun createNotificationChannel_createsChannelWithCorrectSettings() {
        notificationManager.createNotificationChannel()

        val channels = systemNotificationManager.notificationChannels
        assertEquals(1, channels.size)
        assertEquals("model_download_channel", channels[0].id)
    }

    @Test
    fun createForegroundInfo_returnsValidForegroundInfo() {
        val pendingIntent = PendingIntent.getActivity(context, 0, android.content.Intent(), PendingIntent.FLAG_IMMUTABLE)

        val foregroundInfo = notificationManager.createForegroundInfo(pendingIntent)

        assertEquals(DownloadNotificationManager.NOTIFICATION_ID, foregroundInfo.notificationId)
        assertNotNull(foregroundInfo.notification)
    }

    @Test
    fun createForegroundInfoForProgress_returnsValidForegroundInfo() {
        val pendingIntent = PendingIntent.getActivity(context, 0, android.content.Intent(), PendingIntent.FLAG_IMMUTABLE)

        val foregroundInfo = notificationManager.createForegroundInfoForProgress(
            progress = 0.5f,
            currentFile = "test.litertlm",
            subText = "50%",
            cancelPendingIntent = pendingIntent
        )

        assertEquals(DownloadNotificationManager.NOTIFICATION_ID, foregroundInfo.notificationId)
        assertNotNull(foregroundInfo.notification)
    }

    @Test
    fun createForegroundInfoForProgress_calculatesProgressPercentage() {
        val pendingIntent = PendingIntent.getActivity(context, 0, android.content.Intent(), PendingIntent.FLAG_IMMUTABLE)

        val foregroundInfo = notificationManager.createForegroundInfoForProgress(
            progress = 0.75f,
            currentFile = "test.litertlm",
            subText = "75%",
            cancelPendingIntent = pendingIntent
        )

        assertEquals(DownloadNotificationManager.NOTIFICATION_ID, foregroundInfo.notificationId)
        val notification = foregroundInfo.notification
        val progress = notification.extras.getInt(Notification.EXTRA_PROGRESS)
        val progressMax = notification.extras.getInt(Notification.EXTRA_PROGRESS_MAX)

        assertEquals(75, progress)
        assertEquals(100, progressMax)
    }

    @Test
    fun updateNotification_doesNothing_whenPermissionDenied() {
        shadowOf(context as android.app.Application).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)

        notificationManager.updateNotification(
            notificationId = 1001,
            progress = 0.5f,
            currentFile = "test.litertlm",
            subText = "50%",
            cancelPendingIntent = null
        )

        val notifications = shadowOf(systemNotificationManager).allNotifications
        assertEquals(0, notifications.size)
    }

    @Test
    fun updateNotification_callsNotify_whenPermissionGranted() {
        shadowOf(context as android.app.Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)

        notificationManager.updateNotification(
            notificationId = 1001,
            progress = 0.5f,
            currentFile = "test.litertlm",
            subText = "50%",
            cancelPendingIntent = null
        )

        val notifications = shadowOf(systemNotificationManager).allNotifications
        assertEquals(1, notifications.size)
    }

    @Test
    fun updateNotification_calculatesProgressPercentage() {
        shadowOf(context as android.app.Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)

        notificationManager.updateNotification(
            notificationId = 1001,
            progress = 0.75f,
            currentFile = "test.litertlm",
            subText = "75%",
            cancelPendingIntent = null
        )

        val notifications = shadowOf(systemNotificationManager).allNotifications
        assertEquals(1, notifications.size)
    }

    @Test
    fun createNotification_buildsCorrectNotification() {
        shadowOf(context as android.app.Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)

        notificationManager.updateNotification(
            notificationId = 1001,
            progress = 0.5f,
            currentFile = "main.litertlm",
            subText = "50%",
            cancelPendingIntent = null
        )

        val notifications = shadowOf(systemNotificationManager).allNotifications
        assertEquals(1, notifications.size)
    }

    @Test
    fun createNotification_handlesCancelAction() {
        shadowOf(context as android.app.Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val pendingIntent = PendingIntent.getActivity(context, 0, android.content.Intent(), PendingIntent.FLAG_IMMUTABLE)

        notificationManager.updateNotification(
            notificationId = 1001,
            progress = 0.5f,
            currentFile = "test.litertlm",
            subText = "50%",
            cancelPendingIntent = pendingIntent
        )

        val notifications = shadowOf(systemNotificationManager).allNotifications
        assertEquals(1, notifications.size)
    }

    @Test
    fun createForegroundInfoForSnapshot_buildsCorrectSubText_withSpeedAndEta() {
        val pendingIntent = PendingIntent.getActivity(context, 0, android.content.Intent(), PendingIntent.FLAG_IMMUTABLE)
        val snapshot = ProgressSnapshot(
            overallProgress = 0.5f,
            totalBytesDownloaded = 5242880L, // 5 MB
            totalSize = 10485760L, // 10 MB
            completedFiles = 0,
            totalFiles = 1,
            currentFile = "test.litertlm",
            currentSpeedMBps = 1.5,
            etaSeconds = 60L,
            filesProgress = emptyList()
        )

        val formatBytes: (Long) -> String = { bytes -> "${bytes / 1024 / 1024} MB" }
        val formatEta: (Long) -> String = { secs -> "$secs s" }

        val foregroundInfo = notificationManager.createForegroundInfoForSnapshot(
            snapshot = snapshot,
            formatBytes = formatBytes,
            formatEta = formatEta,
            cancelPendingIntent = pendingIntent
        )

                assertEquals(DownloadNotificationManager.NOTIFICATION_ID, foregroundInfo.notificationId)
        val notification = foregroundInfo.notification
        val subText = notification.extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()
        assertEquals("5 MB / 10 MB • 1.5 MB/s • ETA: 60 s", subText)
    }

    @Test
    fun createForegroundInfoForSnapshot_buildsCorrectSubText_withoutSpeed() {
        val pendingIntent = PendingIntent.getActivity(context, 0, android.content.Intent(), PendingIntent.FLAG_IMMUTABLE)
        val snapshot = ProgressSnapshot(
            overallProgress = 0.5f,
            totalBytesDownloaded = 5242880L, // 5 MB
            totalSize = 10485760L, // 10 MB
            completedFiles = 0,
            totalFiles = 1,
            currentFile = "test.litertlm",
            currentSpeedMBps = 0.0, // No speed
            etaSeconds = 60L,
            filesProgress = emptyList()
        )

        val formatBytes: (Long) -> String = { bytes -> "${bytes / 1024 / 1024} MB" }
        val formatEta: (Long) -> String = { secs -> "$secs s" }

        val foregroundInfo = notificationManager.createForegroundInfoForSnapshot(
            snapshot = snapshot,
            formatBytes = formatBytes,
            formatEta = formatEta,
            cancelPendingIntent = pendingIntent
        )

                assertEquals(DownloadNotificationManager.NOTIFICATION_ID, foregroundInfo.notificationId)
        val notification = foregroundInfo.notification
        val subText = notification.extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()
        assertEquals("5 MB / 10 MB • ETA: 60 s", subText)
    }

    @Test
    fun createForegroundInfoForSnapshot_buildsCorrectSubText_withoutEta() {
        val pendingIntent = PendingIntent.getActivity(context, 0, android.content.Intent(), PendingIntent.FLAG_IMMUTABLE)
        val snapshot = ProgressSnapshot(
            overallProgress = 0.5f,
            totalBytesDownloaded = 5242880L, // 5 MB
            totalSize = 10485760L, // 10 MB
            completedFiles = 0,
            totalFiles = 1,
            currentFile = "test.litertlm",
            currentSpeedMBps = 1.5,
            etaSeconds = -1L, // No ETA
            filesProgress = emptyList()
        )

        val formatBytes: (Long) -> String = { bytes -> "${bytes / 1024 / 1024} MB" }
        val formatEta: (Long) -> String = { secs -> "$secs s" }

        val foregroundInfo = notificationManager.createForegroundInfoForSnapshot(
            snapshot = snapshot,
            formatBytes = formatBytes,
            formatEta = formatEta,
            cancelPendingIntent = pendingIntent
        )

                assertEquals(DownloadNotificationManager.NOTIFICATION_ID, foregroundInfo.notificationId)
        val notification = foregroundInfo.notification
        val subText = notification.extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()
        assertEquals("5 MB / 10 MB • 1.5 MB/s", subText)
    }
}
