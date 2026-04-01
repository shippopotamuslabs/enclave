import re

with open("./core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/download/DownloadNotificationManagerTest.kt", "r") as f:
    content = f.read()

# Let's completely remove anything after the FIRST "    }\n}" because we double added it

idx = content.find("    fun createForegroundInfoForSnapshot_buildsCorrectSubText_withoutEta() {")
if idx != -1:
    # find the end of the first occurrence
    end_idx = content.find("    }\n}", idx)
    if end_idx != -1:
        content = content[:end_idx + 6] # +6 to include "    }\n}\n"

with open("./core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/download/DownloadNotificationManagerTest.kt", "w") as f:
    f.write(content)
