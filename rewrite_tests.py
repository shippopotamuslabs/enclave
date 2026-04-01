import sys

with open("./core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/download/DownloadNotificationManagerTest.kt", "r") as f:
    content = f.read()

# Replace mockk with spyk logic cleanly
content = content.replace(
    "import io.mockk.verify",
    "import io.mockk.verify\nimport io.mockk.spyk"
)
content = content.replace(
    "notificationManager = DownloadNotificationManager(mockContext, mockNotificationManager)",
    "notificationManager = spyk(DownloadNotificationManager(mockContext, mockNotificationManager))"
)

tests = """

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
        verify { notificationManager.createForegroundInfoForProgress(0.5f, "test.litertlm", "5 MB / 10 MB • 1.5 MB/s • ETA: 60 s", mockPendingIntent) }
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
        verify { notificationManager.createForegroundInfoForProgress(0.5f, "test.litertlm", "5 MB / 10 MB • ETA: 60 s", mockPendingIntent) }
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
        verify { notificationManager.createForegroundInfoForProgress(0.5f, "test.litertlm", "5 MB / 10 MB • 1.5 MB/s", mockPendingIntent) }
    }
"""

if content.endswith("}\n"):
    content = content[:-2]
    content += tests + "}\n"

with open("./core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/download/DownloadNotificationManagerTest.kt", "w") as f:
    f.write(content)
