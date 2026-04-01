import re

file_path = "core/data/build.gradle.kts"
with open(file_path, "r") as f:
    content = f.read()

# I need to add robolectric and robolectric-jupiter dependencies so I can use Robolectric extension!
# The reviewer said: "retain Robolectric for this specific suite (Robolectric natively supports JUnit 5 via the robolectric-jupiter extension)."

import os
# Check if libs.robolectric is available in gradle libs
