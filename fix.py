import re

file_path = "core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/download/DownloadNotificationManagerTest.kt"
with open(file_path, "r") as f:
    content = f.read()

# remove ambiguous import
content = content.replace("import androidx.test.core.app.ApplicationProvider\n", "", 1)

with open(file_path, "w") as f:
    f.write(content)
