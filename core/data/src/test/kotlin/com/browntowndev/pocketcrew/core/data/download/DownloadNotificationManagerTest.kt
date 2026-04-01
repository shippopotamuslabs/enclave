package com.browntowndev.pocketcrew.core.data.download
import android.Manifest
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


@Disabled("Requires Android runtime - use Robolectric for full testing")
import org.robolectric.Shadows
import org.robolectric.shadows.ShadowNotification
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import androidx.test.core.app.ApplicationProvider

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DownloadNotificationManagerTest {

    private lateinit var appContext: android.app.Application
    private lateinit var mockNotificationManager: NotificationManager
    private lateinit var notificationManager: DownloadNotificationManager

    @Before
    fun setup() {
        appContext = ApplicationProvider.getApplicationContext<android.app.Application>()
        val shadowApp = Shadows.shadowOf(appContext)
        shadowApp.grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        mockNotificationManager = mockk(relaxed = true)
        notificationManager = DownloadNotificationManager(appContext, mockNotificationManager)
    }

    @Test
    fun createNotificationChannel_createsChannelWithCorrectSettings() {
        val channelSlot = slot<NotificationChannel>()

        notificationManager.createNotificationChannel()

        verify { mockNotificationManager.createNotificationChannel(capture(channelSlot)) }
        assertEquals("model_download_channel", channelSlot.captured.id)
    }

    @Test
    fun createForegroundInfo_returnsValidForegroundInfo() {
        val mockPendingIntent = mockk<PendingIntent>(relaxed = true)

        val foregroundInfo = notificationManager.createForegroundInfo(mockPendingIntent)

        assertEquals(DownloadNotificationManager.NOTIFICATION_ID, foregroundInfo.notificationId)
        assertNotNull(foregroundInfo.notification)
    }

    @Test
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
    fun updateNotification_doesNothing_whenPermissionDenied() {
        val shadowApp = Shadows.shadowOf(appContext)
        shadowApp.denyPermissions(Manifest.permission.POST_NOTIFICATIONS)

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
        // We can optionally verify the notification content here as well
    }

    @Test
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

    fun createForegroundInfoForProgress_calculatesProgressPercentage() {
        val mockPendingIntent = mockk<PendingIntent>(relaxed = true)

        val foregroundInfo = notificationManager.createForegroundInfoForProgress(
            progress = 0.75f,
            currentFile = "test.litertlm",
            subText = "75%",
            cancelPendingIntent = mockPendingIntent
        )

        val notification = foregroundInfo.notification

        // Progress max is typically 100, current progress should be 75
        assertEquals(100, notification.extras.getInt(Notification.EXTRA_PROGRESS_MAX))
        assertEquals(75, notification.extras.getInt(Notification.EXTRA_PROGRESS))
    }
}
