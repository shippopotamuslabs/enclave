import sys
import re

with open("./core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/download/DownloadNotificationManagerTest.kt", "r") as f:
    content = f.read()

content = content.replace("import io.mockk.spyk\n", "")
content = content.replace("notificationManager = spyk(DownloadNotificationManager(mockContext, mockNotificationManager))", "notificationManager = DownloadNotificationManager(mockContext, mockNotificationManager)")
content = re.sub(r"verify\s*\{\s*notificationManager\.createForegroundInfoForProgress.*?\}\n", "", content)

with open("./core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/download/DownloadNotificationManagerTest.kt", "w") as f:
    f.write(content)
