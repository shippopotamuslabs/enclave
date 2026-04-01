import sys

file_path = "core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/download/DownloadNotificationManagerTest.kt"
with open(file_path, "r") as f:
    content = f.read()

# Fix `just io.mockk.runs` by replacing with `returns Unit`
content = content.replace("just io.mockk.runs", "returns Unit")

with open(file_path, "w") as f:
    f.write(content)
