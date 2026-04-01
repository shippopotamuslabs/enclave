import sys

with open("./core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/download/DownloadNotificationManagerTest.kt", "r") as f:
    lines = f.readlines()

# Ensure exactly one closing brace at the end
while lines[-1].strip() == "}":
    lines.pop()
while lines[-1].strip() == "":
    lines.pop()

lines.append("}\n")

with open("./core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/download/DownloadNotificationManagerTest.kt", "w") as f:
    f.writelines(lines)
