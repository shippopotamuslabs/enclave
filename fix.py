import re

file_path = "core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/download/DownloadNotificationManagerTest.kt"
with open(file_path, "r") as f:
    content = f.read()

# Add assertions for subText extracting it from notification.extras.getCharSequence(Notification.EXTRA_SUB_TEXT)
replace_str_1 = """        assertEquals(DownloadNotificationManager.NOTIFICATION_ID, foregroundInfo.notificationId)
        val notification = foregroundInfo.notification
        val subText = notification.extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()
        assertEquals("5 MB / 10 MB • 1.5 MB/s • ETA: 60 s", subText)"""

content = content.replace("assertEquals(DownloadNotificationManager.NOTIFICATION_ID, foregroundInfo.notificationId)\n        assertNotNull(foregroundInfo.notification)\n    }\n\n    @Test\n    fun createForegroundInfoForSnapshot_buildsCorrectSubText_withoutSpeed", replace_str_1 + "\n    }\n\n    @Test\n    fun createForegroundInfoForSnapshot_buildsCorrectSubText_withoutSpeed")

replace_str_2 = """        assertEquals(DownloadNotificationManager.NOTIFICATION_ID, foregroundInfo.notificationId)
        val notification = foregroundInfo.notification
        val subText = notification.extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()
        assertEquals("5 MB / 10 MB • ETA: 60 s", subText)"""

content = content.replace("assertEquals(DownloadNotificationManager.NOTIFICATION_ID, foregroundInfo.notificationId)\n        assertNotNull(foregroundInfo.notification)\n    }\n\n    @Test\n    fun createForegroundInfoForSnapshot_buildsCorrectSubText_withoutEta", replace_str_2 + "\n    }\n\n    @Test\n    fun createForegroundInfoForSnapshot_buildsCorrectSubText_withoutEta")

replace_str_3 = """        assertEquals(DownloadNotificationManager.NOTIFICATION_ID, foregroundInfo.notificationId)
        val notification = foregroundInfo.notification
        val subText = notification.extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()
        assertEquals("5 MB / 10 MB • 1.5 MB/s", subText)"""

content = content.replace("assertEquals(DownloadNotificationManager.NOTIFICATION_ID, foregroundInfo.notificationId)\n        assertNotNull(foregroundInfo.notification)\n    }\n}", replace_str_3 + "\n    }\n}")

with open(file_path, "w") as f:
    f.write(content)
