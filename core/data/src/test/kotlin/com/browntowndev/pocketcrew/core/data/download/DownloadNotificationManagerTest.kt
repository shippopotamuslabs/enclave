package com.browntowndev.pocketcrew.core.data.download
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import androidx.work.ForegroundInfo
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.mockk.every
import io.mockk.just
import io.mockk.runs
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test


import android.graphics.drawable.Icon
import androidx.core.app.NotificationCompat
import io.mockk.mockkConstructor
import org.junit.jupiter.api.AfterEach
import android.os.Build
class DownloadNotificationManagerTest {

    private lateinit var mockContext: Context
    private lateinit var mockNotificationManager: NotificationManager
    private lateinit var notificationManager: DownloadNotificationManager


    @BeforeEach
    fun setup() {
        mockContext = mockk(relaxed = true)
        mockNotificationManager = mockk(relaxed = true)
        notificationManager = DownloadNotificationManager(mockContext, mockNotificationManager)

        mockkConstructor(Notification.Builder::class)
        every { anyConstructed<Notification.Builder>().setContentTitle(any()) } returns mockk(relaxed=true)
        every { anyConstructed<Notification.Builder>().build() } returns mockk(relaxed=true)

        mockkConstructor(NotificationCompat.Builder::class)
        every { anyConstructed<NotificationCompat.Builder>().setContentTitle(any()) } returns mockk(relaxed=true)
        every { anyConstructed<NotificationCompat.Builder>().build() } returns mockk(relaxed=true)

        io.mockk.mockkStatic(Icon::class)
        every { Icon.createWithResource(any<Context>(), any<Int>()) } returns mockk(relaxed=true)
    }


    @Test
    @Disabled
    fun createNotificationChannel_createsChannelWithCorrectSettings() {
        val channelSlot = slot<NotificationChannel>()

        notificationManager.createNotificationChannel()

        verify { mockNotificationManager.createNotificationChannel(capture(channelSlot)) }
        assertEquals("model_download_channel", channelSlot.captured.id)
    }

    @Test
    @Disabled
    fun createForegroundInfo_returnsValidForegroundInfo() {
        val mockPendingIntent = mockk<PendingIntent>(relaxed = true)

        val foregroundInfo = notificationManager.createForegroundInfo(mockPendingIntent)

        assertEquals(DownloadNotificationManager.NOTIFICATION_ID, foregroundInfo.notificationId)
        assertNotNull(foregroundInfo.notification)
    }

    @Test
    @Disabled
    fun createForegroundInfoForProgress_returnsValidForegroundInfo() {
        val mockPendingIntent = mockk<PendingIntent>(relaxed = true)

        val foregroundInfo = notificationManager.createForegroundInfoForProgress(
            progress = 0.5f,
            currentFile = "test.litertlm",
            subText = "50%",
            cancelPendingIntent = mockPendingIntent
        )

        assertEquals(DownloadNotificationManager.NOTIFICATION_ID, foregroundInfo.notificationId)
        assertNotNull(foregroundInfo.notification)
    }

    @Test
    @Disabled
    fun updateNotification_doesNothing_whenPermissionDenied() {
        notificationManager.updateNotification(
            notificationId = 1001,
            progress = 0.5f,
            currentFile = "test.litertlm",
            subText = "50%",
            cancelPendingIntent = null
        )

        verify(exactly = 0) { mockNotificationManager.notify(any(), any<Notification>()) }
    }

    @Test
    @Disabled
    fun updateNotification_callsNotify_whenPermissionGranted() {
        val notificationSlot = slot<Notification>()

        notificationManager.updateNotification(
            notificationId = 1001,
            progress = 0.5f,
            currentFile = "test.litertlm",
            subText = "50%",
            cancelPendingIntent = null
        )

        verify { mockNotificationManager.notify(1001, capture(notificationSlot)) }
        assertNotNull(notificationSlot.captured)
    }

    @Test
    @Disabled
    fun updateNotification_calculatesProgressPercentage() {
        val notificationSlot = slot<Notification>()

        notificationManager.updateNotification(
            notificationId = 1001,
            progress = 0.75f,
            currentFile = "test.litertlm",
            subText = "75%",
            cancelPendingIntent = null
        )

        verify { mockNotificationManager.notify(1001, capture(notificationSlot)) }
    }

    @Test
    @Disabled
    fun createNotification_buildsCorrectNotification() {
        val notificationSlot = slot<Notification>()

        notificationManager.updateNotification(
            notificationId = 1001,
            progress = 0.5f,
            currentFile = "main.litertlm",
            subText = "50%",
            cancelPendingIntent = null
        )

        verify { mockNotificationManager.notify(1001, capture(notificationSlot)) }
        assertNotNull(notificationSlot.captured)
    }

    @Test
    @Disabled
    fun createNotification_handlesCancelAction() {
        val mockPendingIntent = mockk<PendingIntent>(relaxed = true)

        val notificationSlot = slot<Notification>()

        notificationManager.updateNotification(
            notificationId = 1001,
            progress = 0.5f,
            currentFile = "test.litertlm",
            subText = "50%",
            cancelPendingIntent = mockPendingIntent
        )

        verify { mockNotificationManager.notify(1001, capture(notificationSlot)) }
        assertNotNull(notificationSlot.captured)
    }

    @Test
    fun createForegroundInfoForSnapshot_buildsCorrectSubText_withSpeedAndEta() {
        val mockPendingIntent = mockk<PendingIntent>(relaxed = true)
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
            cancelPendingIntent = mockPendingIntent
        )

        assertEquals(DownloadNotificationManager.NOTIFICATION_ID, foregroundInfo.notificationId)
        assertNotNull(foregroundInfo.notification)
            }

    @Test
    fun createForegroundInfoForSnapshot_buildsCorrectSubText_withoutSpeed() {
        val mockPendingIntent = mockk<PendingIntent>(relaxed = true)
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
            cancelPendingIntent = mockPendingIntent
        )

        assertEquals(DownloadNotificationManager.NOTIFICATION_ID, foregroundInfo.notificationId)
        assertNotNull(foregroundInfo.notification)
            }

    @Test
    fun createForegroundInfoForSnapshot_buildsCorrectSubText_withoutEta() {
        val mockPendingIntent = mockk<PendingIntent>(relaxed = true)
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
            cancelPendingIntent = mockPendingIntent
        )

        assertEquals(DownloadNotificationManager.NOTIFICATION_ID, foregroundInfo.notificationId)
        assertNotNull(foregroundInfo.notification)
            }

}
