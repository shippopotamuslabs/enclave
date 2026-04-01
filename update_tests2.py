import sys

with open("./core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/download/DownloadNotificationManagerTest.kt", "r") as f:
    content = f.read()

# Add spyk to test the internal call to createForegroundInfoForProgress so we can assert the exact subText that was computed

content = content.replace("import io.mockk.verify", "import io.mockk.verify\nimport io.mockk.spyk\nimport io.mockk.every\nimport io.mockk.just\nimport io.mockk.runs")
content = content.replace("notificationManager = DownloadNotificationManager(mockContext, mockNotificationManager)", "notificationManager = spyk(DownloadNotificationManager(mockContext, mockNotificationManager))")

# Add assertions for subText
content = content.replace("assertEquals(DownloadNotificationManager.NOTIFICATION_ID, foregroundInfo.notificationId)\n        assertNotNull(foregroundInfo.notification)\n    }\n\n    @Test\n    fun createForegroundInfoForSnapshot_buildsCorrectSubText_withoutSpeed()",
"""assertEquals(DownloadNotificationManager.NOTIFICATION_ID, foregroundInfo.notificationId)
        assertNotNull(foregroundInfo.notification)
        verify { notificationManager.createForegroundInfoForProgress(0.5f, "test.litertlm", "5 MB / 10 MB • 1.5 MB/s • ETA: 60 s", mockPendingIntent) }
    }

    @Test
    fun createForegroundInfoForSnapshot_buildsCorrectSubText_withoutSpeed()""")

content = content.replace("assertEquals(DownloadNotificationManager.NOTIFICATION_ID, foregroundInfo.notificationId)\n        assertNotNull(foregroundInfo.notification)\n    }\n\n    @Test\n    fun createForegroundInfoForSnapshot_buildsCorrectSubText_withoutEta()",
"""assertEquals(DownloadNotificationManager.NOTIFICATION_ID, foregroundInfo.notificationId)
        assertNotNull(foregroundInfo.notification)
        verify { notificationManager.createForegroundInfoForProgress(0.5f, "test.litertlm", "5 MB / 10 MB • ETA: 60 s", mockPendingIntent) }
    }

    @Test
    fun createForegroundInfoForSnapshot_buildsCorrectSubText_withoutEta()""")


content = content.replace("assertEquals(DownloadNotificationManager.NOTIFICATION_ID, foregroundInfo.notificationId)\n        assertNotNull(foregroundInfo.notification)\n    }\n}",
"""assertEquals(DownloadNotificationManager.NOTIFICATION_ID, foregroundInfo.notificationId)
        assertNotNull(foregroundInfo.notification)
        verify { notificationManager.createForegroundInfoForProgress(0.5f, "test.litertlm", "5 MB / 10 MB • 1.5 MB/s", mockPendingIntent) }
    }
}""")

with open("./core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/download/DownloadNotificationManagerTest.kt", "w") as f:
    f.write(content)
