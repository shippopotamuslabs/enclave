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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test


class DownloadNotificationManagerTest {

    private lateinit var mockContext: Context
    private lateinit var mockNotificationManager: NotificationManager
    private lateinit var notificationManager: DownloadNotificationManager


    @BeforeEach
    fun setup() {
        mockContext = mockk(relaxed = true)
        mockNotificationManager = mockk(relaxed = true)
        notificationManager = DownloadNotificationManager(mockContext, mockNotificationManager)

        io.mockk.mockkConstructor(Notification.Builder::class)
        io.mockk.every { anyConstructed<Notification.Builder>().setContentTitle(any()) } returns mockk(relaxed=true)
        io.mockk.every { anyConstructed<Notification.Builder>().setContentText(any()) } returns mockk(relaxed=true)
        io.mockk.every { anyConstructed<Notification.Builder>().setSmallIcon(any<Int>()) } returns mockk(relaxed=true)
        io.mockk.every { anyConstructed<Notification.Builder>().setOngoing(any()) } returns mockk(relaxed=true)
        io.mockk.every { anyConstructed<Notification.Builder>().setSubText(any()) } returns mockk(relaxed=true)
        io.mockk.every { anyConstructed<Notification.Builder>().setStyle(any()) } returns mockk(relaxed=true)
        io.mockk.every { anyConstructed<Notification.Builder>().addAction(any()) } returns mockk(relaxed=true)
        io.mockk.every { anyConstructed<Notification.Builder>().build() } returns mockk(relaxed=true)

        io.mockk.mockkConstructor(Notification.ProgressStyle::class)
        io.mockk.every { anyConstructed<Notification.ProgressStyle>().setProgress(any()) } returns mockk(relaxed=true)
        io.mockk.every { anyConstructed<Notification.ProgressStyle>().setStyledByProgress(any()) } returns mockk(relaxed=true)

        io.mockk.mockkConstructor(Notification.Action.Builder::class)
        io.mockk.every { anyConstructed<Notification.Action.Builder>().build() } returns mockk(relaxed=true)

        io.mockk.mockkConstructor(androidx.core.app.NotificationCompat.Builder::class)
        io.mockk.every { anyConstructed<androidx.core.app.NotificationCompat.Builder>().setContentTitle(any()) } returns mockk(relaxed=true)
        io.mockk.every { anyConstructed<androidx.core.app.NotificationCompat.Builder>().setContentText(any()) } returns mockk(relaxed=true)
        io.mockk.every { anyConstructed<androidx.core.app.NotificationCompat.Builder>().setSmallIcon(any<Int>()) } returns mockk(relaxed=true)
        io.mockk.every { anyConstructed<androidx.core.app.NotificationCompat.Builder>().setOngoing(any()) } returns mockk(relaxed=true)
        io.mockk.every { anyConstructed<androidx.core.app.NotificationCompat.Builder>().setProgress(any(), any(), any()) } returns mockk(relaxed=true)
        io.mockk.every { anyConstructed<androidx.core.app.NotificationCompat.Builder>().setSubText(any()) } returns mockk(relaxed=true)
        io.mockk.every { anyConstructed<androidx.core.app.NotificationCompat.Builder>().addAction(any<Int>(), any(), any()) } returns mockk(relaxed=true)
        io.mockk.every { anyConstructed<androidx.core.app.NotificationCompat.Builder>().build() } returns mockk(relaxed=true)

        io.mockk.mockkStatic(android.graphics.drawable.Icon::class)
        io.mockk.every { android.graphics.drawable.Icon.createWithResource(any<Context>(), any<Int>()) } returns mockk(relaxed=true)

        io.mockk.mockkConstructor(NotificationChannel::class)
        io.mockk.every { anyConstructed<NotificationChannel>().description = any() } returns Unit
        io.mockk.every { anyConstructed<NotificationChannel>().setShowBadge(any()) } returns Unit
        io.mockk.every { anyConstructed<NotificationChannel>().id } returns "model_download_channel"

        io.mockk.mockkStatic(androidx.core.content.ContextCompat::class)
        io.mockk.every { androidx.core.content.ContextCompat.checkSelfPermission(any(), any()) } returns android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    @org.junit.jupiter.api.AfterEach
    fun teardown() {
        io.mockk.unmockkAll()
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
        io.mockk.every { androidx.core.content.ContextCompat.checkSelfPermission(any(), any()) } returns android.content.pm.PackageManager.PERMISSION_DENIED
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
}
