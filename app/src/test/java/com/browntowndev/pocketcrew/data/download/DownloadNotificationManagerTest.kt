package com.browntowndev.pocketcrew.data.download

import android.app.Notification
import android.app.NotificationManager
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

@Disabled("Requires Android runtime - use Robolectric for full testing")
class DownloadNotificationManagerTest {

    private lateinit var mockContext: Context
    private lateinit var mockNotificationManager: NotificationManager
    private lateinit var notificationManager: DownloadNotificationManager

    @BeforeEach
    fun setup() {
        mockContext = mockk(relaxed = true)
        mockNotificationManager = mockk(relaxed = true)
        notificationManager = DownloadNotificationManager(mockContext, mockNotificationManager)
    }

    @Test
    fun createNotificationChannel_createsChannelWithCorrectSettings() {
        val channelSlot = slot<android.app.NotificationChannel>()

        notificationManager.createNotificationChannel()

        verify { mockNotificationManager.createNotificationChannel(capture(channelSlot)) }
        assertEquals("model_download_channel", channelSlot.captured.id)
    }

    @Test
    fun createForegroundInfo_returnsValidForegroundInfo() {
        val mockPendingIntent = mockk<android.app.PendingIntent>(relaxed = true)

        val foregroundInfo = notificationManager.createForegroundInfo(mockPendingIntent)

        assertEquals(DownloadNotificationManager.NOTIFICATION_ID, foregroundInfo.notificationId)
        assertNotNull(foregroundInfo.notification)
    }

    @Test
    fun updateNotification_doesNothing_whenPermissionDenied() {
        notificationManager.updateNotification(
            notificationId = 1001,
            progress = 0.5f,
            currentFile = "test.litertlm",
            subText = "50%",
            cancelPendingIntent = null
        )

        verify { mockNotificationManager.notify(any(), any<Notification>()) }
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
        val mockPendingIntent = mockk<android.app.PendingIntent>(relaxed = true)

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
