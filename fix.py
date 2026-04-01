import re

file_path = "core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/download/DownloadNotificationManagerTest.kt"
with open(file_path, "r") as f:
    content = f.read()

# Robolectric SDK compatibility error!
# Let's specify sdk = [34] or [33] in @Config
content = content.replace("@Config(manifest=Config.NONE)", "@Config(manifest=Config.NONE, sdk = [34])")

with open(file_path, "w") as f:
    f.write(content)
