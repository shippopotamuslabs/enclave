import sys

with open("./core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/download/DownloadNotificationManagerTest.kt", "r") as f:
    content = f.read()

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
"""

content = content.replace("}\n", tests + "}\n")

with open("./core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/download/DownloadNotificationManagerTest.kt", "w") as f:
    f.write(content)
