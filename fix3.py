import re

file_path = "core/data/build.gradle.kts"
with open(file_path, "r") as f:
    content = f.read()

# Add robolectric
content = content.replace('testImplementation(libs.mockk)', 'testImplementation(libs.mockk)\n    testImplementation(libs.robolectric)\n    testImplementation("org.robolectric:robolectric:4.13")')

with open(file_path, "w") as f:
    f.write(content)
