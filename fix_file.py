import sys

# Read the file
with open("./core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/download/DownloadNotificationManagerTest.kt", "r") as f:
    content = f.read()

# I see my previous replace strategy caused duplication because of matching strings incorrectly. Let's just restore the file using git checkout and start clean.
